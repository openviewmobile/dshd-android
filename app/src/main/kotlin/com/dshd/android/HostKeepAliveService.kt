package com.dshd.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.drawable.Icon
import android.os.IBinder
import android.system.Os
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

/**
 * Owns the local dsh Host process. The Activity only renders the WebView and
 * observes this service, so Host lifetime no longer depends on the Activity.
 */
class HostKeepAliveService : Service() {
  private var hostProcess: Process? = null
  private var outputThread: Thread? = null
  private var currentUrl: String? = null
  private var lastMessage = ""

  @Volatile private var starting = false

  @Volatile private var stopping = false
  private var consecutiveFailures = 0
  private var hostStartedAt = 0L

  override fun onCreate() {
    super.onCreate()
    val manager = getSystemService(NotificationManager::class.java)!!
    if (manager.getNotificationChannel(CHANNEL) == null) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL, "dsh Host", NotificationManager.IMPORTANCE_LOW)
      )
    }
    startForeground(NOTIFICATION_ID, notification("Starting local dsh Host…", false))
    if (RootfsInstaller.isReady(File(filesDir, ROOTFS_NAME))) {
      DeviceBridge.start(this)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action: String? = if (intent == null) ACTION_QUERY else intent.action
    if (ACTION_STOP == action) {
      stopHost()
      stopSelf()
      return START_NOT_STICKY
    }
    if (ACTION_QUERY == action) {
      broadcastState()
      return START_STICKY
    }
    if (hostProcess == null && !starting) {
      stopping = false
      startHost()
    } else {
      broadcastState()
    }
    return START_STICKY
  }

  private fun startHost() {
    val rootfs = File(filesDir, ROOTFS_NAME)
    if (!RootfsInstaller.isReady(rootfs)) {
      lastMessage = "rootfs is not installed"
      broadcast(STATE_ERROR, null, lastMessage)
      updateNotification("dsh rootfs missing", false)
      return
    }
    currentUrl = null
    starting = true
    stopping = false
    broadcast(STATE_STARTING, null, "")
    updateNotification("Starting local dsh Host…", false)
    Thread({
      try {
        val port = findFreePort()
        if (port < 0) throw IOException("no free port in $MIN_PORT-$MAX_PORT")
        hostStartedAt = System.currentTimeMillis()
        val runtime = File(filesDir, "runtime")
        prepareHook(runtime)
        val storagePatch = StorageConfig.writePatch(this, runtime)
        val loader = File(rootfs, "lib/ld-linux-aarch64.so.1")
        val node = File(rootfs, "opt/node24/bin/node")
        val dsh = File(rootfs, "opt/node24/lib/node_modules/@deepseek-ai/dsh/lib/bin.js")
        val home = File(rootfs, "home")
        val tmp = File(rootfs, "tmp")
        home.mkdirs()
        tmp.mkdirs()
        val libPath = listOf(
          File(rootfs, "lib/aarch64-linux-gnu").absolutePath,
          File(rootfs, "usr/lib/aarch64-linux-gnu").absolutePath,
          File(rootfs, "lib").absolutePath,
          File(rootfs, "usr/lib").absolutePath
        ).joinToString(":")
        val command = mutableListOf<String>()
        command.add(loader.absolutePath)
        command.add("--library-path")
        command.add(libPath)
        command.add(node.absolutePath)
        command.add(dsh.absolutePath)
        command.add("web")
        command.add("--patch")
        command.add(storagePatch.absolutePath)
        command.add("--no-open")
        command.add("--port")
        command.add(port.toString())
        val builder = ProcessBuilder(command)
        builder.directory(home)
        builder.redirectErrorStream(true)
        val env: MutableMap<String, String> = builder.environment()
        env["CORTEX_ROOT"] = rootfs.absolutePath
        env["HOME"] = home.absolutePath
        env["DSH_HOME"] = File(home, ".dsh").absolutePath
        env["TMPDIR"] = tmp.absolutePath
        env["LD_LIBRARY_PATH"] = libPath
        env["LD_PRELOAD"] = File(runtime, "libcortex-hook.so").absolutePath
        env["GLIBC_TUNABLES"] = "glibc.pthread.rseq=0"
        // Android provides no usable Linux sandbox backend; keep the permission
        // default and approval policy consistent with full access.
        env["DSH_PERMISSION_MODE"] = "danger-full-access"
        env["PATH"] = "/opt/node24/bin:/opt/node24/lib/node_modules/.bin:" +
          "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        val providerKey = ProviderSecretStore.load(this)
        if (providerKey.isNotEmpty()) env[ProviderConfig.PROVIDER_ENV] = providerKey
        Log.i(TAG, "starting service host: " + command.joinToString(" "))
        hostProcess = builder.start()
        val thread = Thread({ readOutput() }, "dsh-output")
        outputThread = thread
        thread.isDaemon = true
        thread.start()
      } catch (error: Exception) {
        Log.e(TAG, "service host start failed", error)
        starting = false
        lastMessage = error.toString()
        broadcast(STATE_ERROR, null, lastMessage)
        updateNotification("dsh Host failed", false)
      }
    }, "dsh-start").start()
  }

  private fun readOutput() {
    val tail = StringBuilder()
    try {
      val process = hostProcess!!
      BufferedReader(
        InputStreamReader(process.inputStream, StandardCharsets.UTF_8)
      ).use { reader ->
        var line = reader.readLine()
        while (line != null) {
          Log.i(TAG, "host: $line")
          tail.append(line).append('\n')
          if (tail.length > 4000) tail.delete(0, tail.length - 4000)
          if (line.contains("http://127.0.0.1:")) {
            currentUrl = line.substring(line.indexOf("http://127.0.0.1:")).trim()
            starting = false
            consecutiveFailures = 0
            broadcast(STATE_READY, currentUrl, "")
            updateNotification("dsh is running", true)
          }
          line = reader.readLine()
        }
      }
      val exit = process.waitFor()
      val uptime = System.currentTimeMillis() - hostStartedAt
      hostProcess = null
      starting = false
      if (stopping) return
      if (uptime > 15_000) consecutiveFailures = 0
      consecutiveFailures++
      if (consecutiveFailures <= 5) {
        val delay = Math.min(30, 1 shl consecutiveFailures)
        lastMessage = "Host exited ($exit), restarting in ${delay}s\n\n$tail"
        Log.w(TAG, "service host exited: $exit, retry $consecutiveFailures in ${delay}s")
        broadcast(STATE_STARTING, null, lastMessage)
        updateNotification("Restarting dsh Host in ${delay}s…", false)
        scheduleRestart(delay)
      } else {
        lastMessage = "Host failed repeatedly ($exit)\n\n$tail"
        Log.e(TAG, "service host failed repeatedly")
        broadcast(STATE_ERROR, null, lastMessage)
        updateNotification("dsh Host failed repeatedly", false)
      }
    } catch (error: Exception) {
      if (stopping) {
        hostProcess = null
        starting = false
        return
      }
      Log.e(TAG, "service host output failed", error)
      starting = false
      lastMessage = error.toString()
      broadcast(STATE_ERROR, null, lastMessage)
      updateNotification("dsh Host output failed", false)
    }
  }

  private fun broadcastState() {
    if (currentUrl != null) {
      broadcast(STATE_READY, currentUrl, "")
    } else if (hostProcess != null) {
      broadcast(STATE_STARTING, null, "")
    } else if (lastMessage.isNotEmpty()) {
      broadcast(STATE_EXITED, null, lastMessage)
    } else {
      broadcast(STATE_STARTING, null, "")
    }
  }

  private fun broadcast(state: String, url: String?, message: String?) {
    val event = Intent(EVENT).setPackage(packageName)
    event.putExtra(EXTRA_STATE, state)
    if (url != null) event.putExtra(EXTRA_URL, url)
    if (message != null) event.putExtra(EXTRA_MESSAGE, message)
    sendBroadcast(event)
  }

  private fun notification(text: String, ready: Boolean): Notification {
    val open = Intent(this, MainActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val openIntent = PendingIntent.getActivity(
      this,
      0,
      open,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val stopIntent = PendingIntent.getService(
      this,
      1,
      Intent(this, HostKeepAliveService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return Notification.Builder(this, CHANNEL)
      .setContentTitle(if (ready) "dsh is running" else "dsh Host")
      .setContentText(text)
      .setSmallIcon(android.R.drawable.stat_sys_download_done)
      .setContentIntent(openIntent)
      .addAction(
        Notification.Action.Builder(
          null as Icon?,
          "Stop",
          stopIntent
        ).build()
      )
      .setOngoing(true)
      .build()
  }

  private fun updateNotification(text: String, ready: Boolean) {
    getSystemService(NotificationManager::class.java)!!
      .notify(NOTIFICATION_ID, notification(text, ready))
  }

  private fun findFreePort(): Int {
    for (port in MIN_PORT..MAX_PORT) {
      try {
        ServerSocket().use { socket ->
          socket.reuseAddress = true
          socket.bind(InetSocketAddress("127.0.0.1", port))
          return port
        }
      } catch (ignored: IOException) {
        // try the next candidate
      }
    }
    return -1
  }

  private fun scheduleRestart(seconds: Int) {
    Thread({
      try {
        Thread.sleep(seconds * 1000L)
      } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        return@Thread
      }
      if (!stopping && hostProcess == null) startHost()
    }, "dsh-restart").start()
  }

  private fun prepareHook(runtimeDir: File) {
    if (!runtimeDir.isDirectory && !runtimeDir.mkdirs()) {
      throw IOException("cannot create $runtimeDir")
    }
    val assetManager: AssetManager = getAssets()
    for (name in RUNTIME_ASSETS) {
      val target = File(runtimeDir, name)
      assetManager.open(name).use { input ->
        FileOutputStream(target).use { output ->
          val buffer = ByteArray(64 * 1024)
          var read = input.read(buffer)
          while (read > 0) {
            output.write(buffer, 0, read)
            read = input.read(buffer)
          }
        }
      }
      try {
        Os.chmod(target.absolutePath, 493) // 0755
      } catch (error: android.system.ErrnoException) {
        throw IOException("cannot chmod $target", error)
      }
    }
  }

  private fun stopHost() {
    stopping = true
    val process = hostProcess
    if (process != null) {
      process.destroy()
      hostProcess = null
    }
    currentUrl = null
    starting = false
    stopForeground(true)
  }

  override fun onDestroy() {
    DeviceBridge.stop()
    stopHost()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  companion object {
    private const val TAG = "dshd"
    private const val CHANNEL = "dsh-host"
    private const val NOTIFICATION_ID = 1
    private const val MIN_PORT = 3080
    private const val MAX_PORT = 3099
    private const val ROOTFS_NAME = "rootfs"
    private val RUNTIME_ASSETS = arrayOf("libcortex-hook.so")

    const val ACTION_START = "com.dshd.android.action.START_HOST"
    const val ACTION_STOP = "com.dshd.android.action.STOP_HOST"
    const val ACTION_QUERY = "com.dshd.android.action.QUERY_HOST"
    const val EVENT = "com.dshd.android.event.HOST"
    const val EXTRA_STATE = "state"
    const val EXTRA_URL = "url"
    const val EXTRA_MESSAGE = "message"
    const val STATE_STARTING = "starting"
    const val STATE_READY = "ready"
    const val STATE_EXITED = "exited"
    const val STATE_ERROR = "error"
  }
}
