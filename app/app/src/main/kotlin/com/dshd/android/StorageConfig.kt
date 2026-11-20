package com.dshd.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Chooses where dsh stores Workspaces and other user documents.
 *
 * <p>The Linux rootfs stays in app-private internal storage: Android mounts
 * removable/emulated shared storage noexec, so Node and the ELF loader cannot
 * execute from it. Workspace files are plain data and can live on an SD card.
 * Only the app-specific external directory is used, so no broad storage
 * permission is required and uninstalling the app removes its data.
 */
object StorageConfig {
  private const val TAG = "dshd"
  private const val PREFS = "dshd_storage"
  private const val KEY_MODE = "workspace_mode"
  const val MODE_INTERNAL = "internal"
  const val MODE_EXTERNAL = "external"

  fun mode(context: Context): String {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_MODE, MODE_INTERNAL) ?: MODE_INTERNAL
  }

  fun setMode(context: Context, mode: String?) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putString(KEY_MODE, mode).apply()
  }

  /** Removable SD card app directory, or null when no card is mounted. */
  fun removableDir(context: Context): File? {
    val dirs = context.getExternalFilesDirs(null)
    if (dirs == null || dirs.size < 2 || dirs[1] == null) return null
    try {
      if (!Environment.isExternalStorageRemovable(dirs[1])) return null
    } catch (error: RuntimeException) {
      Log.w(TAG, "cannot classify external storage", error)
      return null
    }
    return dirs[1]
  }

  /** Emulated external app directory (shared storage, not necessarily an SD card). */
  fun externalDir(context: Context): File? {
    return context.getExternalFilesDir(null)
  }

  /** Directory that becomes the parent of the first dsh Workspace. */
  fun documentsDirectory(context: Context): File {
    var base: File?
    if (MODE_EXTERNAL == mode(context)) {
      val removable = removableDir(context)
      base = removable ?: externalDir(context)
    } else {
      base = context.filesDir
    }
    if (base == null) base = context.filesDir
    var documents = File(base, "documents")
    if (!documents.isDirectory && !documents.mkdirs()) {
      Log.w(TAG, "cannot create workspace storage at " + documents + "; using internal")
      documents = File(context.filesDir, "documents")
      //noinspection ResultOfMethodCallIgnored
      documents.mkdirs()
    }
    return documents
  }

  /** Human-readable location for the storage picker. */
  fun describe(context: Context, mode: String?): String {
    if (MODE_EXTERNAL == mode) {
      val removable = removableDir(context)
      if (removable != null) return "SD card (" + removable.absolutePath + ")"
      val external = externalDir(context)
      return "External app storage" + (if (external == null) " unavailable" else " (" + external.absolutePath + ")")
    }
    return "Internal app storage"
  }

  fun currentLabel(context: Context): String {
    return if (MODE_EXTERNAL == mode(context)) "External" else "Internal"
  }

  /**
   * Emits a dsh profile patch that moves first-use Workspace creation. Existing
   * registered Workspaces keep their paths and are never moved implicitly.
   */
  @Throws(IOException::class)
  fun writePatch(context: Context, runtime: File): File {
    val patch = File(runtime, "workspace-storage.yml")
    val path = quote(documentsDirectory(context).absolutePath)
    var yaml = "" +
      "- id: \"workspace-controller\"\n" +
      "  config:\n" +
      "    documentsDirectory: " + path + "\n"
    val plugin = File(
      File(context.filesDir, "rootfs"),
      "opt/node24/lib/node_modules/@deepseek-ai/dsh/node_modules/" +
        "@deepseek-ai/dsh-android-bridge/lib/index.js"
    )
    if (plugin.isFile) {
      yaml += "- insert:\n" +
        "    - id: \"android-bridge\"\n" +
        "      name: " + quote(plugin.absolutePath) + "\n"
    } else {
      Log.w(TAG, "Android bridge plugin is missing at " + plugin)
    }
    FileOutputStream(patch).use { output ->
      output.write(yaml.toByteArray(StandardCharsets.UTF_8))
      output.fd.sync()
    }
    Log.i(TAG, "workspace documents directory: " + documentsDirectory(context))
    return patch
  }

  private fun quote(value: String): String {
    return "'" + value.replace("'", "''") + "'"
  }
}
