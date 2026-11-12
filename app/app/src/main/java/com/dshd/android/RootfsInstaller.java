package com.dshd.android;

import android.system.Os;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.tukaani.xz.XZInputStream;

/**
 * First-run rootfs importer.
 *
 * The product archive is a gzip- or xz-compressed ustar/pax tar with the same layout as
 * Cortex's Ubuntu rootfs. Java has no tar API, so this deliberately small reader
 * handles the entries that matter for a rootfs: directories, regular files,
 * symlinks, hard links, GNU long names and pax path/linkpath records.
 */
final class RootfsInstaller {
  interface Progress {
    /** Percent is -1 when the total is unknown. */
    void onProgress(String message, int percent);
  }

  private static final int BLOCK = 512;

  private RootfsInstaller() {}

  static boolean isReady(File rootfs) {
    return new File(rootfs, "lib/ld-linux-aarch64.so.1").isFile()
        && new File(rootfs, "opt/node24/bin/node").isFile()
        && new File(rootfs,
            "opt/node24/lib/node_modules/@deepseek-ai/dsh/lib/bin.js").isFile();
  }

  static void download(String url, String expectedSha256, File destination,
      Progress progress) throws Exception {
    File part = new File(destination.getParentFile(), destination.getName() + ".part");
    long existing = part.isFile() ? part.length() : 0;
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(30_000);
    connection.setReadTimeout(60_000);
    connection.setInstanceFollowRedirects(true);
    connection.setRequestProperty("User-Agent", "dshd-android/0.1");
    if (existing > 0) connection.setRequestProperty("Range", "bytes=" + existing + "-");
    int status = connection.getResponseCode();
    boolean resumed = status == HttpURLConnection.HTTP_PARTIAL;
    if (status == HttpURLConnection.HTTP_OK && existing > 0) {
      // The server ignored Range; restart from zero rather than corrupting the archive.
      existing = 0;
    } else if (status == 416 && existing > 0) {
      // The partial file may already be complete. Verify it before giving up.
      String digest = sha256(part);
      if (expectedSha256 != null && !expectedSha256.isEmpty()
          && digest.equalsIgnoreCase(expectedSha256.trim())) {
        replace(part, destination);
        if (progress != null) progress.onProgress("Download complete (" + digest + ")", 100);
        return;
      }
      //noinspection ResultOfMethodCallIgnored
      part.delete();
      throw new IOException("range not satisfiable; partial archive removed");
    }
    if (status < 200 || status >= 300) {
      throw new IOException("download HTTP " + status + " from " + url);
    }
    long total = connection.getContentLengthLong();
    if (total > 0) total += existing;
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    if (existing > 0) {
      try (InputStream previous = new FileInputStream(part)) {
        byte[] buffer = new byte[128 * 1024];
        int read;
        while ((read = previous.read(buffer)) > 0) digest.update(buffer, 0, read);
      }
    }
    long received = existing;
    long lastReport = -1;
    try (InputStream input = connection.getInputStream();
         FileOutputStream output = new FileOutputStream(part, existing > 0)) {
      byte[] buffer = new byte[128 * 1024];
      int read;
      while ((read = input.read(buffer)) > 0) {
        output.write(buffer, 0, read);
        digest.update(buffer, 0, read);
        received += read;
        if (progress != null && received / (4L * 1024 * 1024) != lastReport) {
          lastReport = received / (4L * 1024 * 1024);
          String size = total > 0
              ? String.format("%.1f / %.1f MB%s", received / 1048576.0,
                  total / 1048576.0, resumed ? " (resumed)" : "")
              : String.format("%.1f MB", received / 1048576.0);
          int percent = total > 0
              ? (int) Math.min(100, received * 100 / total) : -1;
          progress.onProgress("Downloading rootfs… " + size, percent);
        }
      }
    } finally {
      connection.disconnect();
    }
    String actual = hex(digest.digest());
    if (expectedSha256 != null && !expectedSha256.isEmpty()
        && !actual.equalsIgnoreCase(expectedSha256.trim())) {
      //noinspection ResultOfMethodCallIgnored
      part.delete();
      //noinspection ResultOfMethodCallIgnored
      destination.delete();
      throw new IOException("rootfs SHA-256 mismatch: expected " + expectedSha256 + ", got " + actual);
    }
    replace(part, destination);
    if (progress != null) progress.onProgress("Download complete (" + actual + ")", 100);
  }

  private static String sha256(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = new FileInputStream(file)) {
      byte[] buffer = new byte[128 * 1024];
      int read;
      while ((read = input.read(buffer)) > 0) digest.update(buffer, 0, read);
    }
    return hex(digest.digest());
  }

  private static void replace(File source, File destination) throws IOException {
    if (destination.exists() && !destination.delete()) {
      throw new IOException("cannot replace " + destination);
    }
    if (!source.renameTo(destination)) {
      throw new IOException("cannot move downloaded archive to " + destination);
    }
  }

  static void install(InputStream raw, File rootfs, Progress progress) throws Exception {
    install(raw, rootfs, -1, progress);
  }

  static void install(InputStream raw, File rootfs, long totalBytes, Progress progress)
      throws Exception {
    File parent = rootfs.getParentFile();
    if (parent == null || !parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("cannot create " + parent);
    }
    // A unique staging name avoids a stale, partially-deleted importing tree
    // blocking every retry on filesystems that keep empty/symlink skeletons.
    File temp = new File(parent,
        rootfs.getName() + ".importing." + System.currentTimeMillis());
    if (!temp.mkdirs()) throw new IOException("cannot create " + temp);

    CountingInputStream counted = new CountingInputStream(raw);
    BufferedInputStream buffered = new BufferedInputStream(counted, 1 << 16);
    byte[] magic = new byte[6];
    buffered.mark(magic.length);
    int magicLength = buffered.read(magic);
    buffered.reset();
    InputStream archive;
    if (magicLength >= 2 && (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b) {
      archive = new GZIPInputStream(buffered, 1 << 16);
    } else if (magicLength == 6
        && (magic[0] & 0xff) == 0xfd && (magic[1] & 0xff) == 0x37
        && (magic[2] & 0xff) == 0x7a && (magic[3] & 0xff) == 0x58
        && (magic[4] & 0xff) == 0x5a && (magic[5] & 0xff) == 0x00) {
      archive = new XZInputStream(buffered);
    } else {
      archive = buffered;
    }

    int entries = 0;
    int lastPercent = -1;
    try {
      byte[] header = new byte[BLOCK];
      String longName = null;
      String longLink = null;
      Map<String, String> pax = new HashMap<>();

      while (readBlock(archive, header)) {
        if (isZero(header)) {
          // A zero block ends the archive. Some writers append another block.
          break;
        }
        char type = (char) (header[156] & 0xff);
        long size = parseNumber(header, 124, 12);
        String name = readString(header, 0, 100);
        String prefix = readString(header, 345, 155);
        if (!prefix.isEmpty()) name = prefix + "/" + name;
        String link = readString(header, 157, 100);
        int mode = (int) parseNumber(header, 100, 8);

        if (type == 'x' || type == 'g') {
          if (size > 1024 * 1024) throw new IOException("oversized pax header");
          parsePax(readBody(archive, size), pax);
          continue;
        }
        if (type == 'L') {
          if (size > 1024 * 1024) throw new IOException("oversized long name");
          longName = new String(readBody(archive, size), StandardCharsets.UTF_8).trim();
          continue;
        }
        if (type == 'K') {
          if (size > 1024 * 1024) throw new IOException("oversized long link");
          longLink = new String(readBody(archive, size), StandardCharsets.UTF_8).trim();
          continue;
        }

        if (pax.containsKey("path")) name = pax.get("path");
        if (pax.containsKey("linkpath")) link = pax.get("linkpath");
        if (longName != null) name = longName;
        if (longLink != null) link = longLink;
        longName = null;
        longLink = null;
        pax.clear();

        File target = safeTarget(temp, name);
        if (target == null) continue;
        File targetParent = target.getParentFile();
        if (targetParent != null && !targetParent.isDirectory() && !targetParent.mkdirs()) {
          throw new IOException("cannot create " + targetParent);
        }

        switch (type) {
          case '5':
            if (!target.isDirectory() && !target.mkdirs()) {
              throw new IOException("cannot create directory " + target);
            }
            chmod(target, mode);
            skipFully(archive, size + padding(size));
            break;
          case '2':
            deleteIfExists(target);
            try {
              Os.symlink(link, target.getAbsolutePath());
            } catch (ErrnoException error) {
              throw new IOException("symlink " + name + " -> " + link, error);
            }
            skipFully(archive, size + padding(size));
            break;
          case '1':
            deleteIfExists(target);
            File hardlinkSource = safeTarget(temp, link);
            if (hardlinkSource == null) throw new IOException("invalid hardlink " + name);
            try {
              Os.link(hardlinkSource.getAbsolutePath(), target.getAbsolutePath());
            } catch (Exception error) {
              // Fall back to a copy if the platform/filesystem rejects hard links.
              if (!hardlinkSource.isFile()) throw new IOException("hardlink " + name, error);
              copyFile(hardlinkSource, target);
              chmod(target, mode);
            }
            skipFully(archive, size + padding(size));
            break;
          case '0':
          case '\0':
          case '7':
            deleteIfExists(target);
            try (FileOutputStream output = new FileOutputStream(target)) {
              copyBody(archive, size, output);
            }
            chmod(target, mode);
            break;
          default:
            // Character/block devices and FIFOs do not belong in an app rootfs.
            skipFully(archive, size + padding(size));
            break;
        }

        entries++;
        int percent = totalBytes > 0
            ? (int) Math.min(99, counted.count * 100 / totalBytes) : -1;
        if (progress != null && (entries % 250 == 0 || percent != lastPercent)) {
          lastPercent = percent;
          progress.onProgress(percent >= 0
              ? "Unpacking runtime… " + percent + "%"
              : "Importing rootfs… " + entries + " entries", percent);
        }
      }
    } finally {
      archive.close();
    }

    if (progress != null) progress.onProgress("Finalizing rootfs…", 99);
    // A unique trash name avoids a stale rootfs.old directory blocking the
    // rename. Never fall back to deleting an existing rootfs in place: if the
    // rename fails, keep the old tree intact and report the failure.
    File old = new File(parent, rootfs.getName() + ".old." + System.currentTimeMillis());
    if (rootfs.exists() && !rootfs.renameTo(old)) {
      throw new IOException("cannot move existing rootfs to " + old);
    }
    if (!temp.renameTo(rootfs)) {
      // Roll back so a failed replacement does not leave the app without a rootfs.
      if (old.exists() && !old.renameTo(rootfs)) {
        throw new IOException("cannot install " + temp + " and cannot restore " + old);
      }
      throw new IOException("cannot move " + temp + " to " + rootfs);
    }
    if (!isReady(rootfs)) {
      throw new IOException("archive did not produce a usable rootfs");
    }
    deleteTree(old);
    if (progress != null) progress.onProgress("Rootfs ready", 100);
  }

  private static final class CountingInputStream extends FilterInputStream {
    long count;

    CountingInputStream(InputStream input) {
      super(input);
    }

    @Override public int read() throws IOException {
      int value = super.read();
      if (value >= 0) count++;
      return value;
    }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = super.read(buffer, offset, length);
      if (read > 0) count += read;
      return read;
    }

    @Override public long skip(long count) throws IOException {
      long skipped = super.skip(count);
      if (skipped > 0) this.count += skipped;
      return skipped;
    }
  }

  static void deleteTree(File file) {
    if (file == null) return;
    boolean exists = false;
    try {
      StructStat stat = Os.lstat(file.getAbsolutePath());
      exists = true;
      if (OsConstants.S_ISLNK(stat.st_mode)) {
        // Never follow symlinks into another app or an absolute rootfs path.
        // lstat also makes dangling symlinks visible to exists().
        removePath(file);
        return;
      }
    } catch (ErrnoException ignored) {
      // Fall through; a missing path is fine.
    }
    if (!exists && !file.exists()) return;
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) for (File child : children) deleteTree(child);
      removePath(file);
      return;
    }
    removePath(file);
  }

  private static void removePath(File file) {
    try {
      Os.remove(file.getAbsolutePath());
    } catch (ErrnoException ignored) {
      //noinspection ResultOfMethodCallIgnored
      file.delete();
    }
  }

  private static File safeTarget(File root, String name) throws IOException {
    String normalized = name.replace('\\', '/');
    while (normalized.startsWith("./")) normalized = normalized.substring(2);
    if (normalized.isEmpty() || normalized.equals(".") || normalized.startsWith("/")
        || normalized.contains("../") || normalized.endsWith("/..")) {
      return null;
    }
    return new File(root, normalized);
  }

  private static byte[] readBody(InputStream input, long size) throws IOException {
    if (size < 0 || size > 1024 * 1024) {
      throw new IOException("invalid tar metadata size " + size);
    }
    byte[] body = new byte[(int) size];
    int offset = 0;
    while (offset < body.length) {
      int read = input.read(body, offset, body.length - offset);
      if (read < 0) throw new EOFException("truncated tar entry");
      offset += read;
    }
    skipFully(input, padding(size));
    return body;
  }

  private static long padding(long size) {
    return (BLOCK - (size % BLOCK)) % BLOCK;
  }

  private static void copyBody(InputStream input, long size, FileOutputStream output) throws IOException {
    if (size < 0) throw new IOException("invalid tar entry size " + size);
    byte[] buffer = new byte[64 * 1024];
    long remaining = size;
    while (remaining > 0) {
      int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (read < 0) throw new EOFException("truncated tar entry");
      output.write(buffer, 0, read);
      remaining -= read;
    }
    skipFully(input, padding(size));
  }

  private static boolean readBlock(InputStream input, byte[] block) throws IOException {
    int offset = 0;
    while (offset < block.length) {
      int read = input.read(block, offset, block.length - offset);
      if (read < 0) return offset != 0;
      offset += read;
    }
    return true;
  }

  private static void skipFully(InputStream input, long count) throws IOException {
    while (count > 0) {
      long skipped = input.skip(count);
      if (skipped <= 0) {
        if (input.read() < 0) throw new EOFException();
        skipped = 1;
      }
      count -= skipped;
    }
  }

  private static boolean isZero(byte[] block) {
    for (byte value : block) if (value != 0) return false;
    return true;
  }

  private static String readString(byte[] block, int offset, int length) {
    int end = offset;
    while (end < offset + length && block[end] != 0) end++;
    return new String(block, offset, end - offset, StandardCharsets.UTF_8).trim();
  }

  private static long parseNumber(byte[] block, int offset, int length) throws IOException {
    if ((block[offset] & 0x80) != 0) {
      long value = 0;
      for (int i = offset; i < offset + length; i++) value = (value << 8) | (block[i] & 0xff);
      return value;
    }
    String text = readString(block, offset, length).trim();
    if (text.isEmpty()) return 0;
    try {
      return Long.parseLong(text, 8);
    } catch (NumberFormatException error) {
      throw new IOException("invalid tar number: " + text, error);
    }
  }

  private static void parsePax(byte[] body, Map<String, String> values) {
    int offset = 0;
    while (offset < body.length) {
      int space = offset;
      while (space < body.length && body[space] != ' ') space++;
      if (space >= body.length) break;
      int length;
      try {
        length = Integer.parseInt(new String(body, offset, space - offset, StandardCharsets.UTF_8));
      } catch (NumberFormatException error) {
        break;
      }
      if (length <= 0 || offset + length > body.length) break;
      String record = new String(body, offset, length, StandardCharsets.UTF_8);
      int eq = record.indexOf('=');
      if (eq > 0) {
        String key = record.substring(0, eq);
        String value = record.substring(eq + 1).replace("\n", "");
        values.put(key, value);
      }
      offset += length;
    }
  }

  static void copyTree(File source, File target) throws IOException {
    if (source == null || !source.exists()) return;
    if (source.isDirectory()) {
      if (!target.isDirectory() && !target.mkdirs()) {
        throw new IOException("cannot create " + target);
      }
      File[] children = source.listFiles();
      if (children != null) {
        for (File child : children) copyTree(child, new File(target, child.getName()));
      }
      return;
    }
    if (source.isFile()) {
      File parent = target.getParentFile();
      if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
        throw new IOException("cannot create " + parent);
      }
      copyFile(source, target);
      chmod(target, source.canExecute() ? 0700 : 0600);
    }
  }

  static void copyFile(File source, File target) throws IOException {
    try (InputStream input = new FileInputStream(source);
         FileOutputStream output = new FileOutputStream(target)) {
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
    }
  }

  private static void chmod(File file, int mode) {
    try {
      Os.chmod(file.getAbsolutePath(), mode & 0777);
    } catch (ErrnoException ignored) {
      // The mode is cosmetic for most files; app-private access still works.
    }
  }

  private static String hex(byte[] bytes) {
    char[] out = new char[bytes.length * 2];
    char[] digits = "0123456789abcdef".toCharArray();
    for (int i = 0; i < bytes.length; i++) {
      out[i * 2] = digits[(bytes[i] >> 4) & 0xf];
      out[i * 2 + 1] = digits[bytes[i] & 0xf];
    }
    return new String(out);
  }

  private static void deleteIfExists(File file) throws IOException {
    if (file.exists() && !file.delete()) throw new IOException("cannot delete " + file);
  }
}
