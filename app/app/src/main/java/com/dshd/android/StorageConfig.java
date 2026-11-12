package com.dshd.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Chooses where dsh stores Workspaces and other user documents.
 *
 * <p>The Linux rootfs stays in app-private internal storage: Android mounts
 * removable/emulated shared storage noexec, so Node and the ELF loader cannot
 * execute from it. Workspace files are plain data and can live on an SD card.
 * Only the app-specific external directory is used, so no broad storage
 * permission is required and uninstalling the app removes its data.
 */
final class StorageConfig {
  private static final String TAG = "dshd";
  private static final String PREFS = "dshd_storage";
  private static final String KEY_MODE = "workspace_mode";
  static final String MODE_INTERNAL = "internal";
  static final String MODE_EXTERNAL = "external";

  private StorageConfig() {}

  static String mode(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    return prefs.getString(KEY_MODE, MODE_INTERNAL);
  }

  static void setMode(Context context, String mode) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_MODE, mode).apply();
  }

  /** Removable SD card app directory, or null when no card is mounted. */
  static File removableDir(Context context) {
    File[] dirs = context.getExternalFilesDirs(null);
    if (dirs == null || dirs.length < 2 || dirs[1] == null) return null;
    try {
      if (!Environment.isExternalStorageRemovable(dirs[1])) return null;
    } catch (RuntimeException error) {
      Log.w(TAG, "cannot classify external storage", error);
      return null;
    }
    return dirs[1];
  }

  /** Emulated external app directory (shared storage, not necessarily an SD card). */
  static File externalDir(Context context) {
    return context.getExternalFilesDir(null);
  }

  /** Directory that becomes the parent of the first dsh Workspace. */
  static File documentsDirectory(Context context) {
    File base;
    if (MODE_EXTERNAL.equals(mode(context))) {
      File removable = removableDir(context);
      base = removable != null ? removable : externalDir(context);
    } else {
      base = context.getFilesDir();
    }
    if (base == null) base = context.getFilesDir();
    File documents = new File(base, "documents");
    if (!documents.isDirectory() && !documents.mkdirs()) {
      Log.w(TAG, "cannot create workspace storage at " + documents + "; using internal");
      documents = new File(context.getFilesDir(), "documents");
      //noinspection ResultOfMethodCallIgnored
      documents.mkdirs();
    }
    return documents;
  }

  /** Human-readable location for the storage picker. */
  static String describe(Context context, String mode) {
    if (MODE_EXTERNAL.equals(mode)) {
      File removable = removableDir(context);
      if (removable != null) return "SD card (" + removable.getAbsolutePath() + ")";
      File external = externalDir(context);
      return "External app storage" + (external == null ? " unavailable" : " (" + external.getAbsolutePath() + ")");
    }
    return "Internal app storage";
  }

  static String currentLabel(Context context) {
    return mode(context).equals(MODE_EXTERNAL) ? "External" : "Internal";
  }

  /**
   * Emits a dsh profile patch that moves first-use Workspace creation. Existing
   * registered Workspaces keep their paths and are never moved implicitly.
   */
  static File writePatch(Context context, File runtime) throws IOException {
    File patch = new File(runtime, "workspace-storage.yml");
    String path = quote(documentsDirectory(context).getAbsolutePath());
    String yaml = ""
        + "- id: \"workspace-controller\"\n"
        + "  config:\n"
        + "    documentsDirectory: " + path + "\n";
    File plugin = new File(new File(context.getFilesDir(), "rootfs"),
        "opt/node24/lib/node_modules/@deepseek-ai/dsh/node_modules/"
            + "@deepseek-ai/dsh-android-bridge/lib/index.js");
    if (plugin.isFile()) {
      yaml += "- insert:\n"
          + "    - id: \"android-bridge\"\n"
          + "      name: " + quote(plugin.getAbsolutePath()) + "\n";
    } else {
      Log.w(TAG, "Android bridge plugin is missing at " + plugin);
    }
    try (FileOutputStream output = new FileOutputStream(patch)) {
      output.write(yaml.getBytes(StandardCharsets.UTF_8));
      output.getFD().sync();
    }
    Log.i(TAG, "workspace documents directory: " + documentsDirectory(context));
    return patch;
  }

  private static String quote(String value) {
    return "'" + value.replace("'", "''") + "'";
  }
}
