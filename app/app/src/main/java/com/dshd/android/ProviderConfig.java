package com.dshd.android;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes the minimal dsh provider profile and credential used by the Android shell. */
final class ProviderConfig {
  static final String PROVIDER_ENV = "NEWAPI_GATEWAY_API_KEY";
  static final String DEFAULT_MODEL = "deepseek-v4.1-flash";

  private ProviderConfig() {}

  static File patchFile(File rootfs) {
    return new File(rootfs, "home/.dsh/profiles/web/cordis.patch.yml");
  }

  static File credentialsFile(File rootfs) {
    return new File(rootfs, "home/.dsh/.credentials.yaml");
  }

  static boolean isConfigured(File rootfs, Context context) {
    return contains(patchFile(rootfs), "llm-pi-ai")
        && contains(patchFile(rootfs), "apiKeyEnv")
        && ProviderSecretStore.has(context);
  }

  static void write(File rootfs, String baseUrl, String apiKey, String model)
      throws IOException {
    if (apiKey.trim().isEmpty()) throw new IOException("API key is required");
    writeProfile(rootfs, baseUrl, model);
    stripProviderKey(rootfs);
  }

  /** Update URL/model without touching the Keystore-backed key. */
  static void writeProfile(File rootfs, String baseUrl, String model) throws IOException {
    String url = normalizeBaseUrl(baseUrl);
    String id = normalizeModel(model);
    if (url.isEmpty()) throw new IOException("base URL is required");

    File patch = patchFile(rootfs);
    File parent = patch.getParentFile();
    if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
      throw new IOException("cannot create " + parent);
    }
    String yaml = ""
        + "- id: \"@deepseek-ai/dsh-client-ui-settings-general\"\n"
        + "  config:\n"
        + "    welcomeNoticeVersion: \"2026-09-28.1\"\n"
        + "- id: \"permission\"\n"
        + "  config:\n"
        + "    presets:\n"
        + "      read-only:\n"
        + "        sandbox: read-only\n"
        + "        approval: ask\n"
        + "      workspace-write:\n"
        + "        sandbox: workspace-write\n"
        + "        approval: ask\n"
        + "      danger-full-access:\n"
        + "        sandbox: danger-full-access\n"
        + "        approval: never\n"
        + "    defaultPreset: danger-full-access\n"
        + "- id: \"agent-default-model\"\n"
        + "  name: \"@deepseek-ai/dsh-agent-default-model\"\n"
        + "  config:\n"
        + "    provider: newapi-gateway\n"
        + "    model: " + quote(id) + "\n"
        + "- id: \"llm-pi-ai\"\n"
        + "  name: \"@deepseek-ai/dsh-llm-pi-ai\"\n"
        + "  config:\n"
        + "    providers:\n"
        + "      newapi-gateway:\n"
        + "        displayName: DeepSeek\n"
        + "        apiKeyEnv: " + PROVIDER_ENV + "\n"
        + "        api: openai-completions\n"
        + "        baseURL: " + quote(url) + "\n"
        + "        models:\n"
        + "          - id: " + quote(id) + "\n"
        + "            name: " + quote(id) + "\n"
        + "            input:\n"
        + "              - text\n"
        + "              - image\n";
    writeAtomic(patch, yaml);
  }


  /** Read the current base URL from the generated profile, if present. */
  static String readBaseUrl(File rootfs) {
    return readYamlValue(patchFile(rootfs), "baseURL");
  }

  /** Read the current default model id from the generated profile. */
  static String readModel(File rootfs) {
    return readYamlValue(patchFile(rootfs), "model");
  }

  private static String readYamlValue(File file, String key) {
    try {
      String text = read(file);
      java.util.regex.Matcher matcher = java.util.regex.Pattern
          .compile("(?m)^\\s*" + java.util.regex.Pattern.quote(key)
              + ":\\s*\"?([^\"\\n]+)\"?\\s*$")
          .matcher(text);
      if (!matcher.find()) return "";
      String value = matcher.group(1).trim();
      if (value.length() >= 2
          && ((value.startsWith("\"") && value.endsWith("\""))
              || (value.startsWith("'") && value.endsWith("'")))) {
        value = value.substring(1, value.length() - 1);
      }
      return value;
    } catch (IOException ignored) {
      return "";
    }
  }

  /** Remove a plaintext key written by older builds; the key lives in Keystore now. */
  static void stripProviderKey(File rootfs) throws IOException {
    File credentials = credentialsFile(rootfs);
    String existing = read(credentials);
    StringBuilder merged = new StringBuilder();
    if (existing.trim().isEmpty()) {
      merged.append("version: 1\nrecords: {}\nrefs:\n");
    } else {
      merged.append(existing);
      if (!merged.toString().contains("refs:")) {
        if (!merged.toString().endsWith("\n")) merged.append('\n');
        merged.append("refs:\n");
      }
      String cleaned = merged.toString().replaceAll(
          "(?m)^[ \\t]*" + PROVIDER_ENV + ":.*\\R?", "");
      merged.setLength(0);
      merged.append(cleaned);
    }
    writeAtomic(credentials, merged.toString());
    credentials.setReadable(false, false);
    credentials.setWritable(false, false);
    credentials.setReadable(true, true);
    credentials.setWritable(true, true);
  }

  /** Read a key from the pre-Keystore format so it can be migrated. */
  static String readStoredKey(File rootfs) {
    try {
      String text = read(credentialsFile(rootfs));
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
          "(?m)^[ \\t]*" + PROVIDER_ENV + ":\\s*'((?:[^']|'')*)'\\s*$").matcher(text);
      return matcher.find() ? matcher.group(1).replace("''", "'") : "";
    } catch (IOException error) {
      return "";
    }
  }

  private static String normalizeBaseUrl(String value) {
    String url = value == null ? "" : value.trim();
    while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
    return url;
  }

  private static String normalizeModel(String value) {
    String model = value == null ? "" : value.trim();
    return model.isEmpty() ? DEFAULT_MODEL : model;
  }

  private static String quote(String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  private static boolean contains(File file, String needle) {
    try {
      return read(file).contains(needle);
    } catch (IOException error) {
      return false;
    }
  }

  private static String read(File file) throws IOException {
    if (!file.isFile()) return "";
    try (FileInputStream input = new FileInputStream(file)) {
      byte[] data = new byte[(int) file.length()];
      int offset = 0;
      while (offset < data.length) {
        int read = input.read(data, offset, data.length - offset);
        if (read < 0) break;
        offset += read;
      }
      return new String(data, 0, offset, StandardCharsets.UTF_8);
    }
  }

  private static void writeAtomic(File target, String text) throws IOException {
    File temp = new File(target.getParentFile(), target.getName() + ".tmp");
    try (FileOutputStream output = new FileOutputStream(temp)) {
      output.write(text.getBytes(StandardCharsets.UTF_8));
      output.getFD().sync();
    }
    if (!temp.renameTo(target)) {
      //noinspection ResultOfMethodCallIgnored
      temp.delete();
      throw new IOException("cannot replace " + target);
    }
  }
}
