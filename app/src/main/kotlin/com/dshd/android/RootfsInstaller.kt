package com.dshd.android

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HashMap
import java.util.zip.GZIPInputStream
import org.tukaani.xz.XZInputStream

/**
 * First-run rootfs importer.
 *
 * The product archive is a gzip- or xz-compressed ustar/pax tar with the same layout as
 * Cortex's Ubuntu rootfs. Java has no tar API, so this deliberately small reader
 * handles the entries that matter for a rootfs: directories, regular files,
 * symlinks, hard links, GNU long names and pax path/linkpath records.
 */
object RootfsInstaller {
  fun interface Progress {
    /** Percent is -1 when the total is unknown. */
    fun onProgress(message: String, percent: Int)
  }

  private const val BLOCK = 512

  fun isReady(rootfs: File): Boolean {
    return File(rootfs, "lib/ld-linux-aarch64.so.1").isFile &&
      File(rootfs, "opt/node24/bin/node").isFile &&
      File(
        rootfs,
        "opt/node24/lib/node_modules/@deepseek-ai/dsh/lib/bin.js"
      ).isFile
  }

  @Throws(Exception::class)
  fun download(url: String, expectedSha256: String?, destination: File, progress: Progress?) {
    val part = File(destination.parentFile, destination.name + ".part")
    var existing = if (part.isFile) part.length() else 0L
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 30_000
    connection.readTimeout = 60_000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "dshd-android/0.1")
    if (existing > 0) connection.setRequestProperty("Range", "bytes=" + existing + "-")
    val status = connection.responseCode
    val resumed = status == HttpURLConnection.HTTP_PARTIAL
    if (status == HttpURLConnection.HTTP_OK && existing > 0) {
      // The server ignored Range; restart from zero rather than corrupting the archive.
      existing = 0
    } else if (status == 416 && existing > 0) {
      // The partial file may already be complete. Verify it before giving up.
      val digest = sha256(part)
      if (expectedSha256 != null && expectedSha256.isNotEmpty() &&
        digest.equals(expectedSha256.trim(), ignoreCase = true)
      ) {
        replace(part, destination)
        progress?.onProgress("Download complete ($digest)", 100)
        return
      }
      part.delete()
      throw IOException("range not satisfiable; partial archive removed")
    }
    if (status < 200 || status >= 300) {
      throw IOException("download HTTP $status from $url")
    }
    var total = connection.contentLengthLong
    if (total > 0) total += existing
    val digest = MessageDigest.getInstance("SHA-256")
    if (existing > 0) {
      FileInputStream(part).use { previous ->
        val buffer = ByteArray(128 * 1024)
        var read = previous.read(buffer)
        while (read > 0) {
          digest.update(buffer, 0, read)
          read = previous.read(buffer)
        }
      }
    }
    var received = existing
    var lastReport = -1L
    try {
      connection.inputStream.use { input ->
        FileOutputStream(part, existing > 0).use { output ->
          val buffer = ByteArray(128 * 1024)
          var read = input.read(buffer)
          while (read > 0) {
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            received += read
            if (progress != null && received / (4L * 1024 * 1024) != lastReport) {
              lastReport = received / (4L * 1024 * 1024)
              val size = if (total > 0) {
                String.format(
                  "%.1f / %.1f MB%s",
                  received / 1048576.0,
                  total / 1048576.0,
                  if (resumed) " (resumed)" else ""
                )
              } else {
                String.format("%.1f MB", received / 1048576.0)
              }
              val percent = if (total > 0) {
                Math.min(100L, received * 100 / total).toInt()
              } else {
                -1
              }
              progress.onProgress("Downloading rootfs… $size", percent)
            }
            read = input.read(buffer)
          }
        }
      }
    } finally {
      connection.disconnect()
    }
    val actual = hex(digest.digest())
    if (expectedSha256 != null && expectedSha256.isNotEmpty() &&
      !actual.equals(expectedSha256.trim(), ignoreCase = true)
    ) {
      part.delete()
      destination.delete()
      throw IOException("rootfs SHA-256 mismatch: expected $expectedSha256, got $actual")
    }
    replace(part, destination)
    progress?.onProgress("Download complete ($actual)", 100)
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
      val buffer = ByteArray(128 * 1024)
      var read = input.read(buffer)
      while (read > 0) {
        digest.update(buffer, 0, read)
        read = input.read(buffer)
      }
    }
    return hex(digest.digest())
  }

  private fun replace(source: File, destination: File) {
    if (destination.exists() && !destination.delete()) {
      throw IOException("cannot replace $destination")
    }
    if (!source.renameTo(destination)) {
      throw IOException("cannot move downloaded archive to $destination")
    }
  }

  @Throws(Exception::class)
  fun install(raw: InputStream, rootfs: File, progress: Progress?) {
    install(raw, rootfs, -1L, progress)
  }

  @Throws(Exception::class)
  fun install(raw: InputStream, rootfs: File, totalBytes: Long, progress: Progress?) {
    val parent = rootfs.parentFile
    if (parent == null || !parent.isDirectory && !parent.mkdirs()) {
      throw IOException("cannot create $parent")
    }
    // A unique staging name avoids a stale, partially-deleted importing tree
    // blocking every retry on filesystems that keep empty/symlink skeletons.
    val temp = File(
      parent,
      rootfs.name + ".importing." + System.currentTimeMillis()
    )
    if (!temp.mkdirs()) throw IOException("cannot create $temp")

    val counted = CountingInputStream(raw)
    val buffered = BufferedInputStream(counted, 1 shl 16)
    val magic = ByteArray(6)
    buffered.mark(magic.size)
    val magicLength = buffered.read(magic)
    buffered.reset()
    val archive: InputStream
    if (magicLength >= 2 && (magic[0].toInt() and 0xff) == 0x1f &&
      (magic[1].toInt() and 0xff) == 0x8b
    ) {
      archive = GZIPInputStream(buffered, 1 shl 16)
    } else if (magicLength == 6 &&
      (magic[0].toInt() and 0xff) == 0xfd && (magic[1].toInt() and 0xff) == 0x37 &&
      (magic[2].toInt() and 0xff) == 0x7a && (magic[3].toInt() and 0xff) == 0x58 &&
      (magic[4].toInt() and 0xff) == 0x5a && (magic[5].toInt() and 0xff) == 0x00
    ) {
      archive = XZInputStream(buffered)
    } else {
      archive = buffered
    }

    var entries = 0
    var lastPercent = -1
    try {
      val header = ByteArray(BLOCK)
      var longName: String? = null
      var longLink: String? = null
      val pax = HashMap<String, String>()

      while (readBlock(archive, header)) {
        if (isZero(header)) {
          // A zero block ends the archive. Some writers append another block.
          break
        }
        val type = (header[156].toInt() and 0xff).toChar()
        val size = parseNumber(header, 124, 12)
        var name = readString(header, 0, 100)
        val prefix = readString(header, 345, 155)
        if (prefix.isNotEmpty()) name = "$prefix/$name"
        var link = readString(header, 157, 100)
        val mode = parseNumber(header, 100, 8).toInt()

        if (type == 'x' || type == 'g') {
          if (size > 1024 * 1024) throw IOException("oversized pax header")
          parsePax(readBody(archive, size), pax)
          continue
        }
        if (type == 'L') {
          if (size > 1024 * 1024) throw IOException("oversized long name")
          longName = String(readBody(archive, size), StandardCharsets.UTF_8).trim()
          continue
        }
        if (type == 'K') {
          if (size > 1024 * 1024) throw IOException("oversized long link")
          longLink = String(readBody(archive, size), StandardCharsets.UTF_8).trim()
          continue
        }

        pax["path"]?.let { name = it }
        pax["linkpath"]?.let { link = it }
        longName?.let { name = it }
        longLink?.let { link = it }
        longName = null
        longLink = null
        pax.clear()

        val target = safeTarget(temp, name)
        if (target == null) continue
        val targetParent = target.parentFile
        if (targetParent != null && !targetParent.isDirectory && !targetParent.mkdirs()) {
          throw IOException("cannot create $targetParent")
        }

        when (type) {
          '5' -> {
            if (!target.isDirectory && !target.mkdirs()) {
              throw IOException("cannot create directory $target")
            }
            chmod(target, mode)
            skipFully(archive, size + padding(size))
          }
          '2' -> {
            deleteIfExists(target)
            try {
              Os.symlink(link, target.absolutePath)
            } catch (error: ErrnoException) {
              throw IOException("symlink $name -> $link", error)
            }
            skipFully(archive, size + padding(size))
          }
          '1' -> {
            deleteIfExists(target)
            val hardlinkSource = safeTarget(temp, link)
            if (hardlinkSource == null) throw IOException("invalid hardlink $name")
            try {
              Os.link(hardlinkSource.absolutePath, target.absolutePath)
            } catch (error: Exception) {
              // Fall back to a copy if the platform/filesystem rejects hard links.
              if (!hardlinkSource.isFile) throw IOException("hardlink $name", error)
              copyFile(hardlinkSource, target)
              chmod(target, mode)
            }
            skipFully(archive, size + padding(size))
          }
          '0', '\u0000', '7' -> {
            deleteIfExists(target)
            FileOutputStream(target).use { output ->
              copyBody(archive, size, output)
            }
            chmod(target, mode)
          }
          else -> {
            // Character/block devices and FIFOs do not belong in an app rootfs.
            skipFully(archive, size + padding(size))
          }
        }

        entries++
        val percent = if (totalBytes > 0) {
          Math.min(99L, counted.count * 100 / totalBytes).toInt()
        } else {
          -1
        }
        if (progress != null && (entries % 250 == 0 || percent != lastPercent)) {
          lastPercent = percent
          progress.onProgress(
            if (percent >= 0) {
              "Unpacking runtime… $percent%"
            } else {
              "Importing rootfs… $entries entries"
            },
            percent
          )
        }
      }
    } finally {
      archive.close()
    }

    progress?.onProgress("Finalizing rootfs…", 99)
    // A unique trash name avoids a stale rootfs.old directory blocking the
    // rename. Never fall back to deleting an existing rootfs in place: if the
    // rename fails, keep the old tree intact and report the failure.
    val old = File(parent, rootfs.name + ".old." + System.currentTimeMillis())
    if (rootfs.exists() && !rootfs.renameTo(old)) {
      throw IOException("cannot move existing rootfs to $old")
    }
    if (!temp.renameTo(rootfs)) {
      // Roll back so a failed replacement does not leave the app without a rootfs.
      if (old.exists() && !old.renameTo(rootfs)) {
        throw IOException("cannot install $temp and cannot restore $old")
      }
      throw IOException("cannot move $temp to $rootfs")
    }
    if (!isReady(rootfs)) {
      throw IOException("archive did not produce a usable rootfs")
    }
    deleteTree(old)
    progress?.onProgress("Rootfs ready", 100)
  }

  private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var count: Long = 0L

    override fun read(): Int {
      val value = super.read()
      if (value >= 0) count++
      return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
      val read = super.read(buffer, offset, length)
      if (read > 0) count += read
      return read
    }

    override fun skip(count: Long): Long {
      val skipped = super.skip(count)
      if (skipped > 0) this.count += skipped
      return skipped
    }
  }

  fun deleteTree(file: File?) {
    if (file == null) return
    var exists = false
    try {
      val stat: StructStat = Os.lstat(file.absolutePath)
      exists = true
      if (OsConstants.S_ISLNK(stat.st_mode)) {
        // Never follow symlinks into another app or an absolute rootfs path.
        // lstat also makes dangling symlinks visible to exists().
        removePath(file)
        return
      }
    } catch (ignored: ErrnoException) {
      // Fall through; a missing path is fine.
    }
    if (!exists && !file.exists()) return
    if (file.isDirectory) {
      val children = file.listFiles()
      if (children != null) for (child in children) deleteTree(child)
      removePath(file)
      return
    }
    removePath(file)
  }

  private fun removePath(file: File) {
    try {
      Os.remove(file.absolutePath)
    } catch (ignored: ErrnoException) {
      file.delete()
    }
  }

  private fun safeTarget(root: File, name: String): File? {
    var normalized = name.replace('\\', '/')
    while (normalized.startsWith("./")) normalized = normalized.substring(2)
    if (normalized.isEmpty() || normalized == "." || normalized.startsWith("/") ||
      normalized.contains("../") || normalized.endsWith("/..")
    ) {
      return null
    }
    return File(root, normalized)
  }

  private fun readBody(input: InputStream, size: Long): ByteArray {
    if (size < 0 || size > 1024 * 1024) {
      throw IOException("invalid tar metadata size $size")
    }
    val body = ByteArray(size.toInt())
    var offset = 0
    while (offset < body.size) {
      val read = input.read(body, offset, body.size - offset)
      if (read < 0) throw EOFException("truncated tar entry")
      offset += read
    }
    skipFully(input, padding(size))
    return body
  }

  private fun padding(size: Long): Long {
    return (BLOCK - (size % BLOCK)) % BLOCK
  }

  private fun copyBody(input: InputStream, size: Long, output: FileOutputStream) {
    if (size < 0) throw IOException("invalid tar entry size $size")
    val buffer = ByteArray(64 * 1024)
    var remaining = size
    while (remaining > 0) {
      val read = input.read(buffer, 0, Math.min(buffer.size.toLong(), remaining).toInt())
      if (read < 0) throw EOFException("truncated tar entry")
      output.write(buffer, 0, read)
      remaining -= read
    }
    skipFully(input, padding(size))
  }

  private fun readBlock(input: InputStream, block: ByteArray): Boolean {
    var offset = 0
    while (offset < block.size) {
      val read = input.read(block, offset, block.size - offset)
      if (read < 0) return offset != 0
      offset += read
    }
    return true
  }

  private fun skipFully(input: InputStream, count: Long) {
    var remaining = count
    while (remaining > 0) {
      var skipped = input.skip(remaining)
      if (skipped <= 0) {
        if (input.read() < 0) throw EOFException()
        skipped = 1
      }
      remaining -= skipped
    }
  }

  private fun isZero(block: ByteArray): Boolean {
    for (value in block) if (value != 0.toByte()) return false
    return true
  }

  private fun readString(block: ByteArray, offset: Int, length: Int): String {
    var end = offset
    while (end < offset + length && block[end] != 0.toByte()) end++
    return String(block, offset, end - offset, StandardCharsets.UTF_8).trim()
  }

  private fun parseNumber(block: ByteArray, offset: Int, length: Int): Long {
    if ((block[offset].toInt() and 0x80) != 0) {
      var value = 0L
      for (i in offset until offset + length) {
        value = (value shl 8) or (block[i].toInt() and 0xff).toLong()
      }
      return value
    }
    val text = readString(block, offset, length).trim()
    if (text.isEmpty()) return 0L
    try {
      return text.toLong(8)
    } catch (error: NumberFormatException) {
      throw IOException("invalid tar number: $text", error)
    }
  }

  private fun parsePax(body: ByteArray, values: MutableMap<String, String>) {
    var offset = 0
    while (offset < body.size) {
      var space = offset
      while (space < body.size && body[space] != ' '.code.toByte()) space++
      if (space >= body.size) break
      val length: Int
      try {
        length = String(body, offset, space - offset, StandardCharsets.UTF_8).toInt()
      } catch (error: NumberFormatException) {
        break
      }
      if (length <= 0 || offset + length > body.size) break
      val record = String(body, offset, length, StandardCharsets.UTF_8)
      val eq = record.indexOf('=')
      if (eq > 0) {
        val key = record.substring(0, eq)
        val value = record.substring(eq + 1).replace("\n", "")
        values[key] = value
      }
      offset += length
    }
  }

  @Throws(IOException::class)
  fun copyTree(source: File?, target: File) {
    if (source == null || !source.exists()) return
    if (source.isDirectory) {
      if (!target.isDirectory && !target.mkdirs()) {
        throw IOException("cannot create $target")
      }
      val children = source.listFiles()
      if (children != null) {
        for (child in children) copyTree(child, File(target, child.name))
      }
      return
    }
    if (source.isFile) {
      val parent = target.parentFile
      if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
        throw IOException("cannot create $parent")
      }
      copyFile(source, target)
      chmod(target, if (source.canExecute()) 448 else 384) // 0700 : 0600
    }
  }

  @Throws(IOException::class)
  fun copyFile(source: File, target: File) {
    FileInputStream(source).use { input ->
      FileOutputStream(target).use { output ->
        val buffer = ByteArray(64 * 1024)
        var read = input.read(buffer)
        while (read > 0) {
          output.write(buffer, 0, read)
          read = input.read(buffer)
        }
      }
    }
  }

  private fun chmod(file: File, mode: Int) {
    try {
      Os.chmod(file.absolutePath, mode and 511) // 0777
    } catch (ignored: ErrnoException) {
      // The mode is cosmetic for most files; app-private access still works.
    }
  }

  private fun hex(bytes: ByteArray): String {
    val out = CharArray(bytes.size * 2)
    val digits = "0123456789abcdef".toCharArray()
    for (i in bytes.indices) {
      out[i * 2] = digits[(bytes[i].toInt() shr 4) and 0xf]
      out[i * 2 + 1] = digits[bytes[i].toInt() and 0xf]
    }
    return String(out)
  }

  private fun deleteIfExists(file: File) {
    if (file.exists() && !file.delete()) throw IOException("cannot delete $file")
  }
}
