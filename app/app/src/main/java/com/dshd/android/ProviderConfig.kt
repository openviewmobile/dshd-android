package com.dshd.android

import android.content.Context

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/** Writes the minimal dsh provider profile and credential used by the Android shell. */
object ProviderConfig {
  const val PROVIDER_ENV = "NEWAPI_GATEWAY_API_KEY"
  const val DEFAULT_MODEL = "deepseek-v4.1-flash"

  @JvmStatic
  fun patchFile(rootfs: File): File {
    return File(rootfs, "home/.dsh/profiles/web/cordis.patch.yml")
  }

  @JvmStatic
  fun credentialsFile(rootfs: File): File {
    return File(rootfs, "home/.dsh/.credentials.yaml")
  }

  @JvmStatic
  fun isConfigured(rootfs: File, context: Context): Boolean {
    return contains(patchFile(rootfs), "llm-pi-ai") &&
        contains(patchFile(rootfs), "apiKeyEnv") &&
        ProviderSecretStore.has(context)
  }

  @JvmStatic
  @Throws(IOException::class)
  fun write(rootfs: File, baseUrl: String?, apiKey: String, model: String?) {
    if (apiKey.trim().isEmpty()) throw IOException("API key is required")
    writeProfile(rootfs, baseUrl, model)
    stripProviderKey(rootfs)
  }

  /** Update URL/model without touching the Keystore-backed key. */
  @JvmStatic
  @Throws(IOException::class)
  fun writeProfile(rootfs: File, baseUrl: String?, model: String?) {
    val url = normalizeBaseUrl(baseUrl)
    val id = normalizeModel(model)
    if (url.isEmpty()) throw IOException("base URL is required")

    val patch = patchFile(rootfs)
    val parent = patch.parentFile
    if (parent == null || (!parent.isDirectory && !parent.mkdirs())) {
      throw IOException("cannot create " + parent)
    }
    val yaml = "" +
        "- id: \"@deepseek-ai/dsh-client-ui-settings-general\"\n" +
        "  config:\n" +
        "    welcomeNoticeVersion: \"2026-09-28.1\"\n" +
        "- id: \"permission\"\n" +
        "  config:\n" +
        "    presets:\n" +
        "      read-only:\n" +
        "        sandbox: read-only\n" +
        "        approval: ask\n" +
        "      workspace-write:\n" +
        "        sandbox: workspace-write\n" +
        "        approval: ask\n" +
        "      danger-full-access:\n" +
        "        sandbox: danger-full-access\n" +
        "        approval: never\n" +
        "    defaultPreset: danger-full-access\n" +
        "- id: \"agent-default-model\"\n" +
        "  name: \"@deepseek-ai/dsh-agent-default-model\"\n" +
        "  config:\n" +
        "    provider: newapi-gateway\n" +
        "    model: " + quote(id) + "\n" +
        "- id: \"llm-pi-ai\"\n" +
        "  name: \"@deepseek-ai/dsh-llm-pi-ai\"\n" +
        "  config:\n" +
        "    providers:\n" +
        "      newapi-gateway:\n" +
        "        displayName: DeepSeek\n" +
        "        apiKeyEnv: " + PROVIDER_ENV + "\n" +
        "        api: openai-completions\n" +
        "        baseURL: " + quote(url) + "\n" +
        "        models:\n" +
        "          - id: " + quote(id) + "\n" +
        "            name: " + quote(id) + "\n" +
        "            input:\n" +
        "              - text\n" +
        "              - image\n"
    writeAtomic(patch, yaml)
  }


  /** Read the current base URL from the generated profile, if present. */
  @JvmStatic
  fun readBaseUrl(rootfs: File): String {
    return readYamlValue(patchFile(rootfs), "baseURL")
  }

  /** Read the current default model id from the generated profile. */
  @JvmStatic
  fun readModel(rootfs: File): String {
    return readYamlValue(patchFile(rootfs), "model")
  }

  private fun readYamlValue(file: File, key: String): String {
    try {
      val text = read(file)
      val matcher = Pattern
          .compile("(?m)^\\s*" + Pattern.quote(key)
              + ":\\s*\"?([^\"\\n]+)\"?\\s*$")
          .matcher(text)
      if (!matcher.find()) return ""
      var value = matcher.group(1)!!.trim()
      if (value.length >= 2
          && ((value.startsWith("\"") && value.endsWith("\""))
              || (value.startsWith("'") && value.endsWith("'")))) {
        value = value.substring(1, value.length - 1)
      }
      return value
    } catch (ignored: IOException) {
      return ""
    }
  }

  /** Remove a plaintext key written by older builds; the key lives in Keystore now. */
  @JvmStatic
  @Throws(IOException::class)
  fun stripProviderKey(rootfs: File) {
    val credentials = credentialsFile(rootfs)
    val existing = read(credentials)
    val merged = StringBuilder()
    if (existing.trim().isEmpty()) {
      merged.append("version: 1\nrecords: {}\nrefs:\n")
    } else {
      merged.append(existing)
      if (!merged.toString().contains("refs:")) {
        if (!merged.toString().endsWith("\n")) merged.append('\n')
        merged.append("refs:\n")
      }
      val cleaned = Pattern.compile("(?m)^[ \\t]*" + PROVIDER_ENV + ":.*\\R?")
          .matcher(merged.toString())
          .replaceAll("")
      merged.setLength(0)
      merged.append(cleaned)
    }
    writeAtomic(credentials, merged.toString())
    credentials.setReadable(false, false)
    credentials.setWritable(false, false)
    credentials.setReadable(true, true)
    credentials.setWritable(true, true)
  }

  /** Read a key from the pre-Keystore format so it can be migrated. */
  @JvmStatic
  fun readStoredKey(rootfs: File): String {
    try {
      val text = read(credentialsFile(rootfs))
      val matcher = Pattern.compile(
          "(?m)^[ \\t]*" + PROVIDER_ENV + ":\\s*'((?:[^']|'')*)'\\s*$").matcher(text)
      return if (matcher.find()) matcher.group(1)!!.replace("''", "'") else ""
    } catch (error: IOException) {
      return ""
    }
  }

  private fun normalizeBaseUrl(value: String?): String {
    var url = if (value == null) "" else value.trim()
    while (url.endsWith("/")) url = url.substring(0, url.length - 1)
    return url
  }

  private fun normalizeModel(value: String?): String {
    val model = if (value == null) "" else value.trim()
    return if (model.isEmpty()) DEFAULT_MODEL else model
  }

  private fun quote(value: String): String {
    return "'" + value.replace("'", "''") + "'"
  }

  private fun contains(file: File, needle: String): Boolean {
    try {
      return read(file).contains(needle)
    } catch (error: IOException) {
      return false
    }
  }

  private fun read(file: File): String {
    if (!file.isFile) return ""
    FileInputStream(file).use { input ->
      val data = ByteArray(file.length().toInt())
      var offset = 0
      while (offset < data.size) {
        val read = input.read(data, offset, data.size - offset)
        if (read < 0) break
        offset += read
      }
      return String(data, 0, offset, StandardCharsets.UTF_8)
    }
  }

  private fun writeAtomic(target: File, text: String) {
    val temp = File(target.parentFile, target.name + ".tmp")
    FileOutputStream(temp).use { output ->
      output.write(text.toByteArray(StandardCharsets.UTF_8))
      output.fd.sync()
    }
    if (!temp.renameTo(target)) {
      //noinspection ResultOfMethodCallIgnored
      temp.delete()
      throw IOException("cannot replace " + target)
    }
  }
}
