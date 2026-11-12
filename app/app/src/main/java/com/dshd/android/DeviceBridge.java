package com.dshd.android;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.system.Os;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Loopback HTTP bridge between the Linux dsh Host and Android runtime APIs.
 *
 * The Host and its agent run as ordinary Linux processes, so they cannot call
 * Android framework APIs. This server binds 127.0.0.1 only, requires a random
 * per-process bearer token, and exposes only the small permissioned surface in
 * {@link #handle}. The token is written mode 0600 into the rootfs home for the
 * companion dsh plugin.
 */
final class DeviceBridge {
  static final String VERSION = "1";
  static final String CREDENTIALS = "android-bridge.json";
  private static final String TAG = "dshd";
  private static final String CHANNEL = "dshd-device";
  private static final Object LOCK = new Object();
  private static final Pattern PHONE = Pattern.compile("^[+]?[0-9 ()-]{3,32}$");
  private static final long CALL_COOLDOWN_MS = 5_000;

  private static DeviceBridge instance;
  private static long lastCallAt;

  private final Context context;
  private final ExecutorService workers = Executors.newCachedThreadPool();
  private ServerSocket server;
  private Thread acceptThread;
  private String token;
  private int port;
  private volatile boolean running;
  private File credentialsFile;

  private DeviceBridge(Context context) {
    this.context = context.getApplicationContext();
  }

  static void start(Context context) {
    synchronized (LOCK) {
      if (instance != null) return;
      DeviceBridge bridge = new DeviceBridge(context);
      try {
        bridge.open();
        instance = bridge;
        Log.i(TAG, "Android bridge listening on 127.0.0.1:" + bridge.port);
      } catch (Exception error) {
        Log.e(TAG, "Android bridge failed to start", error);
        bridge.close();
      }
    }
  }

  static void stop() {
    synchronized (LOCK) {
      if (instance == null) return;
      instance.close();
      instance = null;
      Log.i(TAG, "Android bridge stopped");
    }
  }

  private void open() throws Exception {
    token = randomToken();
    server = new ServerSocket();
    server.setReuseAddress(true);
    server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
    port = server.getLocalPort();
    credentialsFile = new File(new File(context.getFilesDir(), "rootfs"),
        "home/.dsh/" + CREDENTIALS);
    writeCredentials();
    running = true;
    acceptThread = new Thread(this::acceptLoop, "dshd-bridge-accept");
    acceptThread.setDaemon(true);
    acceptThread.start();
  }

  private void close() {
    running = false;
    try {
      if (server != null) server.close();
    } catch (IOException ignored) {
    }
    server = null;
    workers.shutdownNow();
    if (credentialsFile != null) {
      //noinspection ResultOfMethodCallIgnored
      credentialsFile.delete();
    }
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    StringBuilder out = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) out.append(String.format(Locale.US, "%02x", value));
    return out.toString();
  }

  private void writeCredentials() throws Exception {
    File parent = credentialsFile.getParentFile();
    if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
      throw new IOException("cannot create " + parent);
    }
    JSONObject json = new JSONObject();
    json.put("version", VERSION);
    json.put("url", "http://127.0.0.1:" + port);
    json.put("token", token);
    json.put("pid", android.os.Process.myPid());
    File temp = new File(parent, CREDENTIALS + ".tmp");
    try (FileOutputStream out = new FileOutputStream(temp)) {
      out.write(json.toString().getBytes(StandardCharsets.UTF_8));
    }
    if (!temp.renameTo(credentialsFile)) {
      throw new IOException("cannot publish " + credentialsFile);
    }
    try {
      Os.chmod(credentialsFile.getAbsolutePath(), 0600);
    } catch (Exception ignored) {
    }
  }

  private void acceptLoop() {
    while (running) {
      try {
        Socket socket = server.accept();
        socket.setSoTimeout(20_000);
        workers.execute(() -> handleSocket(socket));
      } catch (IOException error) {
        if (running) Log.w(TAG, "bridge accept failed: " + error);
      }
    }
  }

  private void handleSocket(Socket socket) {
    try (Socket ignored = socket) {
      InputStream input = new BufferedInputStream(socket.getInputStream());
      String requestLine = readLine(input);
      if (requestLine == null) return;
      String[] parts = requestLine.split(" ");
      if (parts.length < 2) return;
      String method = parts[0];
      String target = parts[1];
      Map<String, String> headers = new LinkedHashMap<>();
      String line;
      while ((line = readLine(input)) != null && !line.isEmpty()) {
        int colon = line.indexOf(':');
        if (colon > 0) {
          headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
              line.substring(colon + 1).trim());
        }
      }
      int length = 0;
      try {
        length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
      } catch (NumberFormatException numberError) {
      }
      if (length < 0 || length > 64 * 1024) {
        write(socket.getOutputStream(), 413, error("payload_too_large", "Request body is too large."));
        return;
      }
      byte[] body = readBody(input, length);
      String supplied = headers.get("x-dshd-token");
      if (supplied == null) {
        String auth = headers.getOrDefault("authorization", "");
        if (auth.startsWith("Bearer ")) supplied = auth.substring(7);
      }
      if (supplied == null || !MessageDigest.isEqual(
          token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
        write(socket.getOutputStream(), 401, error("unauthorized", "Missing or invalid bridge token."));
        return;
      }
      JSONObject result = handle(method, target, body);
      write(socket.getOutputStream(), 200, result);
    } catch (Exception error) {
      Log.w(TAG, "bridge request failed", error);
      try {
        write(socket.getOutputStream(), 500, error("internal_error", String.valueOf(error)));
      } catch (IOException ignored) {
      }
    }
  }

  private JSONObject handle(String method, String target, byte[] body) throws Exception {
    Uri uri = Uri.parse("http://127.0.0.1" + target);
    String path = uri.getPath() == null ? "" : uri.getPath();
    JSONObject payload = new JSONObject();
    if (body.length > 0) {
      try {
        payload = new JSONObject(new String(body, StandardCharsets.UTF_8));
      } catch (JSONException error) {
        return error("invalid_json", "Request body must be a JSON object.");
      }
    }
    if ("/v1/health".equals(path)) {
      return ok().put("service", "dshd-android-bridge").put("version", VERSION);
    }
    if ("/v1/permissions".equals(path)) {
      return ok().put("permissions", DevicePermissions.snapshot(context));
    }
    if ("/v1/permissions/request".equals(path)) {
      if (!"POST".equals(method)) return error("method_not_allowed", "Use POST.");
      String name = payload.optString("name", "");
      if (!Arrays.asList(DevicePermissions.KEYS).contains(name)) {
        return error("unknown_permission", "Unknown permission: " + name);
      }
      return openPermission(name);
    }
    if ("/v1/location".equals(path)) {
      if (!"GET".equals(method)) return error("method_not_allowed", "Use GET.");
      return location();
    }
    if ("/v1/bluetooth".equals(path)) {
      if (!"GET".equals(method)) return error("method_not_allowed", "Use GET.");
      return bluetoothStatus();
    }
    if ("/v1/bluetooth/scan".equals(path)) {
      if (!"POST".equals(method)) return error("method_not_allowed", "Use POST.");
      return bluetoothScan(Math.max(3, Math.min(15, payload.optInt("seconds", 8))));
    }
    if ("/v1/contacts".equals(path)) {
      if (!"GET".equals(method)) return error("method_not_allowed", "Use GET.");
      return contacts(uri.getQueryParameter("query"), uri.getQueryParameter("limit"));
    }
    if ("/v1/phone/call".equals(path)) {
      if (!"POST".equals(method)) return error("method_not_allowed", "Use POST.");
      return call(payload.optString("number", ""));
    }
    if ("/v1/notify".equals(path)) {
      if (!"POST".equals(method)) return error("method_not_allowed", "Use POST.");
      return notifyUser(payload.optString("title", "DSHD"),
          payload.optString("body", ""));
    }
    return error("not_found", "Unknown bridge endpoint: " + path);
  }

  private JSONObject openPermission(String name) {
    try {
      Intent intent = new Intent(context, MainActivity.class)
          .putExtra(MainActivity.EXTRA_OPEN_PERMISSION, name)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      context.startActivity(intent);
      return ok().put("opened", true).put("permission", name)
          .put("message", "Permission center opened on the phone. The user must grant it.");
    } catch (Exception error) {
      return error("cannot_open_activity",
          "Android blocked opening the permission center; open DSHD manually. " + error);
    }
  }

  private JSONObject location() {
    if (!DevicePermissions.locationGranted(context)) {
      return permission("Location permission is not granted.");
    }
    LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    if (manager == null) return error("location_unavailable", "Location service is unavailable.");
    List<String> providers = manager.getProviders(true);
    if (providers == null || providers.isEmpty()) {
      return error("location_disabled", "Location is disabled or has no enabled provider.");
    }
    Location best = null;
    for (String provider : providers) {
      try {
        Location candidate = manager.getLastKnownLocation(provider);
        if (candidate != null && (best == null || candidate.getTime() > best.getTime())) {
          best = candidate;
        }
      } catch (SecurityException ignored) {
      }
    }
    if (best == null) {
      final CountDownLatch latch = new CountDownLatch(1);
      final AtomicReference<Location> fresh = new AtomicReference<>();
      LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
          if (fresh.compareAndSet(null, location)) latch.countDown();
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
      };
      for (String provider : providers) {
        try {
          manager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
        } catch (Exception ignored) {
        }
      }
      try {
        latch.await(18, TimeUnit.SECONDS);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
      }
      for (String provider : providers) {
        try {
          manager.removeUpdates(listener);
        } catch (Exception ignored) {
        }
      }
      best = fresh.get();
    }
    if (best == null) {
      return cachedLocation();
    }
    try {
      JSONObject out = ok();
      out.put("latitude", best.getLatitude());
      out.put("longitude", best.getLongitude());
      out.put("accuracyMeters", best.hasAccuracy() ? best.getAccuracy() : JSONObject.NULL);
      out.put("provider", best.getProvider() == null ? "" : best.getProvider());
      out.put("timestamp", best.getTime());
      out.put("stale", false);
      context.getSharedPreferences("dshd_location", Context.MODE_PRIVATE).edit()
          .putString("last", out.toString()).apply();
      return out;
    } catch (JSONException error) {
      return error("location_error", String.valueOf(error));
    }
  }

  /** Never turn a stale fix into a silent current position; mark it explicitly. */
  private JSONObject cachedLocation() {
    String raw = context.getSharedPreferences("dshd_location", Context.MODE_PRIVATE)
        .getString("last", "");
    if (raw.isEmpty()) {
      return error("location_timeout", "No location fix within 18 seconds.");
    }
    try {
      JSONObject cached = new JSONObject(raw);
      cached.put("stale", true);
      cached.put("message", "No fresh fix; this is the last known position from this app.");
      return cached;
    } catch (JSONException error) {
      return error("location_error", String.valueOf(error));
    }
  }

  private JSONObject bluetoothStatus() {
    BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
    if (adapter == null) return error("bluetooth_unavailable", "This device has no Bluetooth adapter.");
    if (!DevicePermissions.isGranted(context, DevicePermissions.BLUETOOTH)) {
      return permission("Bluetooth permission is not granted.");
    }
    JSONObject out = ok();
    try {
      out.put("available", true);
      out.put("enabled", adapter.isEnabled());
      if (Build.VERSION.SDK_INT < 31
          || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
              == PackageManager.PERMISSION_GRANTED) {
        out.put("name", adapter.getName() == null ? "" : adapter.getName());
        JSONArray bonded = new JSONArray();
        for (BluetoothDevice device : adapter.getBondedDevices()) {
          JSONObject row = new JSONObject();
          row.put("name", device.getName() == null ? "" : device.getName());
          row.put("address", device.getAddress());
          bonded.put(row);
        }
        out.put("bonded", bonded);
      }
      out.put("scanReady", DevicePermissions.bluetoothScanGranted(context));
      return out;
    } catch (Exception error) {
      return error("bluetooth_error", String.valueOf(error));
    }
  }

  private JSONObject bluetoothScan(int seconds) throws JSONException {
    if (!DevicePermissions.isGranted(context, DevicePermissions.BLUETOOTH)) {
      return permission("Bluetooth permission is not granted.");
    }
    if (!DevicePermissions.bluetoothScanGranted(context)) {
      return permission(Build.VERSION.SDK_INT >= 31
          ? "Bluetooth scan permission is not granted."
          : "Location permission is required to scan Bluetooth on Android 11 and older.");
    }
    BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
    if (adapter == null) return error("bluetooth_unavailable", "This device has no Bluetooth adapter.");
    if (!adapter.isEnabled()) return error("bluetooth_disabled", "Bluetooth is turned off.");
    BluetoothLeScanner scanner;
    try {
      scanner = adapter.getBluetoothLeScanner();
    } catch (SecurityException error) {
      return permission("Bluetooth scan permission was revoked.");
    }
    if (scanner == null) return error("bluetooth_unavailable", "BLE scanner is unavailable.");
    final Map<String, JSONObject> found = new LinkedHashMap<>();
    final AtomicInteger scanError = new AtomicInteger(-1);
    final CountDownLatch done = new CountDownLatch(1);
    ScanCallback callback = new ScanCallback() {
      @Override public void onScanResult(int callbackType, ScanResult result) {
        BluetoothDevice device = result.getDevice();
        if (device == null) return;
        try {
          JSONObject row = new JSONObject();
          String name = device.getName();
          row.put("name", name == null ? "" : name);
          row.put("address", device.getAddress());
          row.put("rssi", result.getRssi());
          synchronized (found) {
            found.put(device.getAddress(), row);
          }
        } catch (Exception ignored) {
        }
      }
      @Override public void onScanFailed(int errorCode) {
        scanError.set(errorCode);
        done.countDown();
      }
    };
    try {
      scanner.startScan(callback);
    } catch (Exception error) {
      return error("bluetooth_scan_failed", String.valueOf(error));
    }
    try {
      done.await(seconds, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
    try {
      scanner.stopScan(callback);
    } catch (Exception ignored) {
    }
    if (scanError.get() >= 0) {
      return error("bluetooth_scan_failed", "BLE scan failed with code " + scanError.get() + ".");
    }
    JSONArray devices = new JSONArray();
    synchronized (found) {
      for (JSONObject row : found.values()) devices.put(row);
    }
    return ok().put("devices", devices).put("seconds", seconds);
  }

  private JSONObject contacts(String query, String limit) throws JSONException {
    if (!DevicePermissions.isGranted(context, DevicePermissions.CONTACTS)) {
      return permission("Contacts permission is not granted.");
    }
    String text = query == null ? "" : query.trim();
    if (text.isEmpty()) return error("invalid_query", "A non-empty query is required.");
    int max = 20;
    try {
      max = Math.max(1, Math.min(50, Integer.parseInt(limit == null ? "20" : limit)));
    } catch (NumberFormatException ignored) {
    }
    String[] projection = {
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    };
    String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ? OR "
        + ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
    String[] args = {"%" + text + "%", "%" + text + "%"};
    JSONArray rows = new JSONArray();
    try (Cursor cursor = context.getContentResolver().query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection, selection, args,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
      while (cursor != null && cursor.moveToNext() && rows.length() < max) {
        String name = cursor.getString(0);
        String number = cursor.getString(1);
        if (number == null || number.isEmpty()) continue;
        JSONObject row = new JSONObject();
        row.put("name", name == null ? "" : name);
        row.put("number", number);
        rows.put(row);
      }
    } catch (Exception error) {
      return error("contacts_failed", String.valueOf(error));
    }
    return ok().put("contacts", rows);
  }

  private JSONObject call(String number) {
    if (!DevicePermissions.isGranted(context, DevicePermissions.PHONE)) {
      return permission("CALL_PHONE permission is not granted.");
    }
    String trimmed = number == null ? "" : number.trim();
    int digits = 0;
    for (int i = 0; i < trimmed.length(); i++) {
      if (Character.isDigit(trimmed.charAt(i))) digits++;
    }
    if (!PHONE.matcher(trimmed).matches() || digits < 3) {
      return error("invalid_number", "Expected a phone number with at least 3 digits, spaces, +, - or ().");
    }
    synchronized (LOCK) {
      long now = System.currentTimeMillis();
      if (now - lastCallAt < CALL_COOLDOWN_MS) {
        return error("rate_limited", "Wait a few seconds before placing another call.");
      }
      lastCallAt = now;
    }
    try {
      Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", trimmed, null));
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
      return ok().put("number", trimmed).put("action", "call");
    } catch (Exception error) {
      return error("call_failed",
          "Android blocked the call. Unlock the phone and try again. " + error);
    }
  }

  private JSONObject notifyUser(String title, String body) throws JSONException {
    if (Build.VERSION.SDK_INT >= 33
        && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      return permission("Notification permission is not granted.");
    }
    NotificationManager manager = (NotificationManager) context.getSystemService(
        Context.NOTIFICATION_SERVICE);
    if (manager == null) return error("notifications_unavailable", "Notification service is unavailable.");
    if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
      manager.createNotificationChannel(new NotificationChannel(
          CHANNEL, "DSHD agent", NotificationManager.IMPORTANCE_DEFAULT));
    }
    Intent open = new Intent(context, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pending = PendingIntent.getActivity(context, 0, open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification notification = new Notification.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title == null || title.isEmpty() ? "DSHD" : title)
        .setContentText(body == null ? "" : body)
        .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
        .setContentIntent(pending)
        .setAutoCancel(true)
        .build();
    manager.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), notification);
    return ok().put("notified", true);
  }

  private static JSONObject ok() {
    JSONObject out = new JSONObject();
    try {
      out.put("ok", true);
    } catch (JSONException ignored) {
    }
    return out;
  }

  private static JSONObject error(String code, String message) {
    JSONObject out = new JSONObject();
    try {
      out.put("ok", false).put("code", code).put("message", message);
    } catch (JSONException ignored) {
    }
    return out;
  }

  private static JSONObject permission(String message) {
    JSONObject out = error("permission_required", message);
    try {
      out.put("action", "open_permissions");
      out.put("tool", "android_request_permission");
    } catch (JSONException ignored) {
    }
    return out;
  }

  private static String readLine(InputStream input) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int value;
    while ((value = input.read()) != -1) {
      if (value == '\n') break;
      if (value != '\r') out.write(value);
      if (out.size() > 8192) throw new IOException("header line too long");
    }
    if (value == -1 && out.size() == 0) return null;
    return out.toString(StandardCharsets.UTF_8.name());
  }

  private static byte[] readBody(InputStream input, int length) throws IOException {
    byte[] body = new byte[length];
    int offset = 0;
    while (offset < length) {
      int read = input.read(body, offset, length - offset);
      if (read == -1) break;
      offset += read;
    }
    return offset == length ? body : Arrays.copyOf(body, offset);
  }

  private static void write(OutputStream output, int status, JSONObject body) throws IOException {
    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
    String head = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Error") + "\r\n"
        + "Content-Type: application/json; charset=utf-8\r\n"
        + "Content-Length: " + bytes.length + "\r\n"
        + "Connection: close\r\n\r\n";
    output.write(head.getBytes(StandardCharsets.UTF_8));
    output.write(bytes);
    output.flush();
  }
}
