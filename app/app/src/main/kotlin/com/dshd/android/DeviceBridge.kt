package com.dshd.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.ContactsContract
import android.system.Os
import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Loopback HTTP bridge between the Linux dsh Host and Android runtime APIs.
 *
 * The Host and its agent run as ordinary Linux processes, so they cannot call
 * Android framework APIs. This server binds 127.0.0.1 only, requires a random
 * per-process bearer token, and exposes only the small permissioned surface in
 * [handle]. The token is written mode 0600 into the rootfs home for the
 * companion dsh plugin.
 */
class DeviceBridge private constructor(context: Context) {
  private val context: Context = context.applicationContext
  private val workers: ExecutorService = Executors.newCachedThreadPool()
  private var server: ServerSocket? = null
  private var acceptThread: Thread? = null
  private var token: String? = null
  private var port = 0

  @Volatile
  private var running = false
  private var credentialsFile: File? = null

  private fun open() {
    token = randomToken()
    server = ServerSocket()
    server!!.setReuseAddress(true)
    server!!.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8)
    port = server!!.localPort
    credentialsFile = File(File(context.filesDir, "rootfs"), "home/.dsh/$CREDENTIALS")
    writeCredentials()
    running = true
    acceptThread = Thread({ acceptLoop() }, "dshd-bridge-accept")
    acceptThread!!.isDaemon = true
    acceptThread!!.start()
  }

  private fun close() {
    running = false
    try {
      server?.close()
    } catch (ignored: IOException) {
    }
    server = null
    workers.shutdownNow()
    credentialsFile?.delete()
  }

  private fun writeCredentials() {
    val file = credentialsFile!!
    val parent = file.parentFile
    if (parent == null || (!parent.isDirectory && !parent.mkdirs())) {
      throw IOException("cannot create " + parent)
    }
    val json = JSONObject()
    json.put("version", VERSION)
    json.put("url", "http://127.0.0.1:" + port)
    json.put("token", token!!)
    json.put("pid", android.os.Process.myPid())
    val temp = File(parent, CREDENTIALS + ".tmp")
    FileOutputStream(temp).use { out ->
      out.write(json.toString().toByteArray(StandardCharsets.UTF_8))
    }
    if (!temp.renameTo(file)) {
      throw IOException("cannot publish " + file)
    }
    try {
      // 0600: owner read/write only, like the host-side credentials file.
      Os.chmod(file.absolutePath, 384)
    } catch (ignored: Exception) {
    }
  }

  private fun acceptLoop() {
    while (running) {
      try {
        val socket = server!!.accept()
        socket.soTimeout = 20_000
        workers.execute { handleSocket(socket) }
      } catch (failure: IOException) {
        if (running) Log.w(TAG, "bridge accept failed: " + failure)
      }
    }
  }

  private fun handleSocket(socket: Socket) {
    try {
      socket.use { connection ->
        val input = BufferedInputStream(connection.getInputStream())
        val requestLine = readLine(input) ?: return
        // Match java.lang.String.split(" "): trailing empty fields are dropped.
        val parts = requestLine.split(" ").dropLastWhile { it.isEmpty() }
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]
        val headers = LinkedHashMap<String, String>()
        while (true) {
          val line = readLine(input) ?: break
          if (line.isEmpty()) break
          val colon = line.indexOf(':')
          if (colon > 0) {
            headers[line.substring(0, colon).trim { it <= ' ' }.lowercase(Locale.US)] =
              line.substring(colon + 1).trim { it <= ' ' }
          }
        }
        var length = 0
        try {
          length = headers.getOrDefault("content-length", "0").toInt()
        } catch (ignored: NumberFormatException) {
        }
        if (length < 0 || length > 64 * 1024) {
          write(
            connection.getOutputStream(),
            413,
            error("payload_too_large", "Request body is too large.")
          )
          return
        }
        val body = readBody(input, length)
        var supplied = headers["x-dshd-token"]
        if (supplied == null) {
          val auth = headers.getOrDefault("authorization", "")
          if (auth.startsWith("Bearer ")) supplied = auth.substring(7)
        }
        if (supplied == null || !MessageDigest.isEqual(
            token!!.toByteArray(StandardCharsets.UTF_8),
            supplied.toByteArray(StandardCharsets.UTF_8)
          )
        ) {
          write(
            connection.getOutputStream(),
            401,
            error("unauthorized", "Missing or invalid bridge token.")
          )
          return
        }
        val result = handle(method, target, body)
        write(connection.getOutputStream(), 200, result)
      }
    } catch (failure: Exception) {
      Log.w(TAG, "bridge request failed", failure)
      try {
        write(socket.getOutputStream(), 500, error("internal_error", failure.toString()))
      } catch (ignored: IOException) {
      }
    }
  }

  private fun handle(method: String, target: String, body: ByteArray): JSONObject {
    val uri = Uri.parse("http://127.0.0.1" + target)
    val path = uri.path ?: ""
    var payload = JSONObject()
    if (body.isNotEmpty()) {
      try {
        payload = JSONObject(String(body, StandardCharsets.UTF_8))
      } catch (failure: JSONException) {
        return error("invalid_json", "Request body must be a JSON object.")
      }
    }
    if ("/v1/health" == path) {
      return ok().put("service", "dshd-android-bridge").put("version", VERSION)
    }
    if ("/v1/permissions" == path) {
      return ok().put("permissions", DevicePermissions.snapshot(context))
    }
    if ("/v1/permissions/request" == path) {
      if ("POST" != method) return error("method_not_allowed", "Use POST.")
      val name = payload.optString("name", "")
      if (!DevicePermissions.KEYS.asList().contains(name)) {
        return error("unknown_permission", "Unknown permission: " + name)
      }
      return openPermission(name)
    }
    if ("/v1/location" == path) {
      if ("GET" != method) return error("method_not_allowed", "Use GET.")
      return location()
    }
    if ("/v1/bluetooth" == path) {
      if ("GET" != method) return error("method_not_allowed", "Use GET.")
      return bluetoothStatus()
    }
    if ("/v1/bluetooth/scan" == path) {
      if ("POST" != method) return error("method_not_allowed", "Use POST.")
      return bluetoothScan(Math.max(3, Math.min(15, payload.optInt("seconds", 8))))
    }
    if ("/v1/contacts" == path) {
      if ("GET" != method) return error("method_not_allowed", "Use GET.")
      return contacts(uri.getQueryParameter("query"), uri.getQueryParameter("limit"))
    }
    if ("/v1/phone/call" == path) {
      if ("POST" != method) return error("method_not_allowed", "Use POST.")
      return call(payload.optString("number", ""))
    }
    if ("/v1/notify" == path) {
      if ("POST" != method) return error("method_not_allowed", "Use POST.")
      return notifyUser(payload.optString("title", "DSHD"), payload.optString("body", ""))
    }
    return error("not_found", "Unknown bridge endpoint: " + path)
  }

  private fun openPermission(name: String): JSONObject {
    try {
      val intent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN_PERMISSION, name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      context.startActivity(intent)
      return ok().put("opened", true).put("permission", name)
        .put("message", "Permission center opened on the phone. The user must grant it.")
    } catch (failure: Exception) {
      return error(
        "cannot_open_activity",
        "Android blocked opening the permission center; open DSHD manually. " + failure
      )
    }
  }

  private fun location(): JSONObject {
    if (!DevicePermissions.locationGranted(context)) {
      return permission("Location permission is not granted.")
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
    if (manager == null) return error("location_unavailable", "Location service is unavailable.")
    val providers = manager.getProviders(true)
    if (providers == null || providers.isEmpty()) {
      return error("location_disabled", "Location is disabled or has no enabled provider.")
    }
    var best: Location? = null
    for (provider in providers) {
      try {
        val candidate = manager.getLastKnownLocation(provider)
        if (candidate != null) {
          val previous = best
          if (previous == null || candidate.time > previous.time) {
            best = candidate
          }
        }
      } catch (ignored: SecurityException) {
      }
    }
    if (best == null) {
      val latch = CountDownLatch(1)
      val fresh = AtomicReference<Location?>()
      val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
          if (fresh.compareAndSet(null, location)) latch.countDown()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
      }
      for (provider in providers) {
        try {
          manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (ignored: Exception) {
        }
      }
      try {
        latch.await(18L, TimeUnit.SECONDS)
      } catch (failure: InterruptedException) {
        Thread.currentThread().interrupt()
      }
      for (provider in providers) {
        try {
          manager.removeUpdates(listener)
        } catch (ignored: Exception) {
        }
      }
      best = fresh.get()
    }
    val fix = best
    if (fix == null) {
      return cachedLocation()
    }
    try {
      val out = ok()
      out.put("latitude", fix.latitude)
      out.put("longitude", fix.longitude)
      out.put("accuracyMeters", if (fix.hasAccuracy()) fix.accuracy else JSONObject.NULL)
      out.put("provider", fix.provider ?: "")
      out.put("timestamp", fix.time)
      out.put("stale", false)
      context.getSharedPreferences("dshd_location", Context.MODE_PRIVATE).edit()
        .putString("last", out.toString()).apply()
      return out
    } catch (failure: JSONException) {
      return error("location_error", failure.toString())
    }
  }

  /** Never turn a stale fix into a silent current position; mark it explicitly. */
  private fun cachedLocation(): JSONObject {
    val raw: String = context.getSharedPreferences("dshd_location", Context.MODE_PRIVATE)
      .getString("last", "")!!
    if (raw.isEmpty()) {
      return error("location_timeout", "No location fix within 18 seconds.")
    }
    try {
      val cached = JSONObject(raw)
      cached.put("stale", true)
      cached.put("message", "No fresh fix; this is the last known position from this app.")
      return cached
    } catch (failure: JSONException) {
      return error("location_error", failure.toString())
    }
  }

  private fun bluetoothStatus(): JSONObject {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val adapter = manager?.adapter
    if (adapter == null) {
      return error(
        "bluetooth_unavailable",
        "This device has no Bluetooth adapter."
      )
    }
    if (!DevicePermissions.isGranted(context, DevicePermissions.BLUETOOTH)) {
      return permission("Bluetooth permission is not granted.")
    }
    val out = ok()
    try {
      out.put("available", true)
      out.put("enabled", adapter.isEnabled)
      if (Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
      ) {
        out.put("name", adapter.name ?: "")
        val bonded = JSONArray()
        for (device in adapter.bondedDevices) {
          val row = JSONObject()
          row.put("name", device.name ?: "")
          row.put("address", device.address)
          bonded.put(row)
        }
        out.put("bonded", bonded)
      }
      out.put("scanReady", DevicePermissions.bluetoothScanGranted(context))
      return out
    } catch (failure: Exception) {
      return error("bluetooth_error", failure.toString())
    }
  }

  private fun bluetoothScan(seconds: Int): JSONObject {
    if (!DevicePermissions.isGranted(context, DevicePermissions.BLUETOOTH)) {
      return permission("Bluetooth permission is not granted.")
    }
    if (!DevicePermissions.bluetoothScanGranted(context)) {
      return permission(
        if (Build.VERSION.SDK_INT >= 31) {
          "Bluetooth scan permission is not granted."
        } else {
          "Location permission is required to scan Bluetooth on Android 11 and older."
        }
      )
    }
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val adapter = manager?.adapter
    if (adapter == null) {
      return error(
        "bluetooth_unavailable",
        "This device has no Bluetooth adapter."
      )
    }
    if (!adapter.isEnabled) return error("bluetooth_disabled", "Bluetooth is turned off.")
    val scanner: BluetoothLeScanner? = try {
      adapter.bluetoothLeScanner
    } catch (ignored: SecurityException) {
      return permission("Bluetooth scan permission was revoked.")
    }
    if (scanner == null) return error("bluetooth_unavailable", "BLE scanner is unavailable.")
    val found = LinkedHashMap<String, JSONObject>()
    val scanError = AtomicInteger(-1)
    val done = CountDownLatch(1)
    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device
        if (device == null) return
        try {
          val row = JSONObject()
          val name = device.name
          row.put("name", name ?: "")
          row.put("address", device.address)
          row.put("rssi", result.rssi)
          synchronized(found) {
            found.put(device.address, row)
          }
        } catch (ignored: Exception) {
        }
      }

      override fun onScanFailed(errorCode: Int) {
        scanError.set(errorCode)
        done.countDown()
      }
    }
    try {
      scanner.startScan(callback)
    } catch (failure: Exception) {
      return error("bluetooth_scan_failed", failure.toString())
    }
    try {
      done.await(seconds.toLong(), TimeUnit.SECONDS)
    } catch (failure: InterruptedException) {
      Thread.currentThread().interrupt()
    }
    try {
      scanner.stopScan(callback)
    } catch (ignored: Exception) {
    }
    if (scanError.get() >= 0) {
      return error(
        "bluetooth_scan_failed",
        "BLE scan failed with code " + scanError.get() + "."
      )
    }
    val devices = JSONArray()
    synchronized(found) {
      for (row in found.values) devices.put(row)
    }
    return ok().put("devices", devices).put("seconds", seconds)
  }

  private fun contacts(query: String?, limit: String?): JSONObject {
    if (!DevicePermissions.isGranted(context, DevicePermissions.CONTACTS)) {
      return permission("Contacts permission is not granted.")
    }
    val text = query?.trim { it <= ' ' } ?: ""
    if (text.isEmpty()) return error("invalid_query", "A non-empty query is required.")
    var max = 20
    try {
      max = Math.max(1, Math.min(50, (limit ?: "20").toInt()))
    } catch (ignored: NumberFormatException) {
    }
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    val selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ? OR " +
      ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?"
    val args = arrayOf("%" + text + "%", "%" + text + "%")
    val rows = JSONArray()
    try {
      context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        selection,
        args,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
      )?.use { cursor ->
        while (cursor.moveToNext() && rows.length() < max) {
          val name = cursor.getString(0)
          val number = cursor.getString(1)
          if (number != null && number.isNotEmpty()) {
            val row = JSONObject()
            row.put("name", name ?: "")
            row.put("number", number)
            rows.put(row)
          }
        }
      }
    } catch (failure: Exception) {
      return error("contacts_failed", failure.toString())
    }
    return ok().put("contacts", rows)
  }

  private fun call(number: String?): JSONObject {
    if (!DevicePermissions.isGranted(context, DevicePermissions.PHONE)) {
      return permission("CALL_PHONE permission is not granted.")
    }
    val trimmed = number?.trim { it <= ' ' } ?: ""
    var digits = 0
    for (element in trimmed) {
      if (Character.isDigit(element)) digits++
    }
    if (!PHONE.matcher(trimmed).matches() || digits < 3) {
      return error(
        "invalid_number",
        "Expected a phone number with at least 3 digits, spaces, +, - or ()."
      )
    }
    synchronized(LOCK) {
      val now = System.currentTimeMillis()
      if (now - lastCallAt < CALL_COOLDOWN_MS) {
        return error("rate_limited", "Wait a few seconds before placing another call.")
      }
      lastCallAt = now
    }
    try {
      val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", trimmed, null))
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
      return ok().put("number", trimmed).put("action", "call")
    } catch (failure: Exception) {
      return error(
        "call_failed",
        "Android blocked the call. Unlock the phone and try again. " + failure
      )
    }
  }

  private fun notifyUser(title: String?, body: String?): JSONObject {
    if (Build.VERSION.SDK_INT >= 33 &&
      context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return permission("Notification permission is not granted.")
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
    if (manager == null) {
      return error(
        "notifications_unavailable",
        "Notification service is unavailable."
      )
    }
    if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
      manager.createNotificationChannel(
        NotificationChannel(
          CHANNEL,
          "DSHD agent",
          NotificationManager.IMPORTANCE_DEFAULT
        )
      )
    }
    val open = Intent(context, MainActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val pending = PendingIntent.getActivity(
      context,
      0,
      open,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = Notification.Builder(context, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(if (title == null || title.isEmpty()) "DSHD" else title)
      .setContentText(body ?: "")
      .setStyle(Notification.BigTextStyle().bigText(body ?: ""))
      .setContentIntent(pending)
      .setAutoCancel(true)
      .build()
    manager.notify((System.currentTimeMillis() % Integer.MAX_VALUE).toInt(), notification)
    return ok().put("notified", true)
  }

  companion object {
    const val VERSION = "1"
    const val CREDENTIALS = "android-bridge.json"

    private const val TAG = "dshd"
    private const val CHANNEL = "dshd-device"
    private val LOCK = Any()
    private val PHONE = Pattern.compile("^[+]?[0-9 ()-]{3,32}$")
    private const val CALL_COOLDOWN_MS = 5_000L

    private var instance: DeviceBridge? = null
    private var lastCallAt: Long = 0

    fun start(context: Context) {
      synchronized(LOCK) {
        if (instance != null) return
        val bridge = DeviceBridge(context)
        try {
          bridge.open()
          instance = bridge
          Log.i(TAG, "Android bridge listening on 127.0.0.1:" + bridge.port)
        } catch (failure: Exception) {
          Log.e(TAG, "Android bridge failed to start", failure)
          bridge.close()
        }
      }
    }

    fun stop() {
      synchronized(LOCK) {
        val bridge = instance ?: return
        bridge.close()
        instance = null
        Log.i(TAG, "Android bridge stopped")
      }
    }

    private fun randomToken(): String {
      val bytes = ByteArray(32)
      SecureRandom().nextBytes(bytes)
      val out = StringBuilder(bytes.size * 2)
      for (value in bytes) out.append(String.format(Locale.US, "%02x", value))
      return out.toString()
    }

    private fun ok(): JSONObject {
      val out = JSONObject()
      try {
        out.put("ok", true)
      } catch (ignored: JSONException) {
      }
      return out
    }

    private fun error(code: String, message: String): JSONObject {
      val out = JSONObject()
      try {
        out.put("ok", false).put("code", code).put("message", message)
      } catch (ignored: JSONException) {
      }
      return out
    }

    private fun permission(message: String): JSONObject {
      val out = error("permission_required", message)
      try {
        out.put("action", "open_permissions")
        out.put("tool", "android_request_permission")
      } catch (ignored: JSONException) {
      }
      return out
    }

    private fun readLine(input: InputStream): String? {
      val out = ByteArrayOutputStream()
      var value = input.read()
      while (value != -1) {
        if (value == '\n'.code) break
        if (value != '\r'.code) out.write(value)
        if (out.size() > 8192) throw IOException("header line too long")
        value = input.read()
      }
      if (value == -1 && out.size() == 0) return null
      return out.toString(StandardCharsets.UTF_8.name())
    }

    private fun readBody(input: InputStream, length: Int): ByteArray {
      val body = ByteArray(length)
      var offset = 0
      while (offset < length) {
        val read = input.read(body, offset, length - offset)
        if (read == -1) break
        offset += read
      }
      return if (offset == length) body else body.copyOf(offset)
    }

    private fun write(output: OutputStream, status: Int, body: JSONObject) {
      val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
      val head = "HTTP/1.1 " + status + " " + (if (status == 200) "OK" else "Error") + "\r\n" +
        "Content-Type: application/json; charset=utf-8\r\n" +
        "Content-Length: " + bytes.size + "\r\n" +
        "Connection: close\r\n\r\n"
      output.write(head.toByteArray(StandardCharsets.UTF_8))
      output.write(bytes)
      output.flush()
    }
  }
}
