package com.dshd.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns the local dsh Host process. The Activity only renders the WebView and
 * observes this service, so Host lifetime no longer depends on the Activity.
 */
public final class HostKeepAliveService extends Service {
  private static final String TAG = "dshd";
  private static final String CHANNEL = "dsh-host";
  private static final int NOTIFICATION_ID = 1;
  private static final int MIN_PORT = 3080;
  private static final int MAX_PORT = 3099;
  private static final String ROOTFS_NAME = "rootfs";
  private static final String[] RUNTIME_ASSETS = {"libcortex-hook.so"};

  static final String ACTION_START = "com.dshd.android.action.START_HOST";
  static final String ACTION_STOP = "com.dshd.android.action.STOP_HOST";
  static final String ACTION_QUERY = "com.dshd.android.action.QUERY_HOST";
  static final String EVENT = "com.dshd.android.event.HOST";
  static final String EXTRA_STATE = "state";
  static final String EXTRA_URL = "url";
  static final String EXTRA_MESSAGE = "message";
  static final String STATE_STARTING = "starting";
  static final String STATE_READY = "ready";
  static final String STATE_EXITED = "exited";
  static final String STATE_ERROR = "error";

  private Process hostProcess;
  private Thread outputThread;
  private String currentUrl;
  private String lastMessage = "";
  private volatile boolean starting;
  private volatile boolean stopping;
  private int consecutiveFailures;
  private long hostStartedAt;

  @Override public void onCreate() {
    super.onCreate();
    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager.getNotificationChannel(CHANNEL) == null) {
      manager.createNotificationChannel(new NotificationChannel(
          CHANNEL, "dsh Host", NotificationManager.IMPORTANCE_LOW));
    }
    startForeground(NOTIFICATION_ID, notification("Starting local dsh Host…", false));
    if (RootfsInstaller.isReady(new File(getFilesDir(), ROOTFS_NAME))) {
      DeviceBridge.start(this);
    }
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent == null ? ACTION_QUERY : intent.getAction();
    if (ACTION_STOP.equals(action)) {
      stopHost();
      stopSelf();
      return START_NOT_STICKY;
    }
    if (ACTION_QUERY.equals(action)) {
      broadcastState();
      return START_STICKY;
    }
    if (hostProcess == null && !starting) {
      stopping = false;
      startHost();
    } else {
      broadcastState();
    }
    return START_STICKY;
  }

  private void startHost() {
    File rootfs = new File(getFilesDir(), ROOTFS_NAME);
    if (!RootfsInstaller.isReady(rootfs)) {
      lastMessage = "rootfs is not installed";
      broadcast(STATE_ERROR, null, lastMessage);
      updateNotification("dsh rootfs missing", false);
      return;
    }
    currentUrl = null;
    starting = true;
    stopping = false;
    broadcast(STATE_STARTING, null, "");
    updateNotification("Starting local dsh Host…", false);
    new Thread(() -> {
      try {
        int port = findFreePort();
        if (port < 0) throw new IOException("no free port in " + MIN_PORT + "-" + MAX_PORT);
        hostStartedAt = System.currentTimeMillis();
        File runtime = new File(getFilesDir(), "runtime");
        prepareHook(runtime);
        File storagePatch = StorageConfig.writePatch(this, runtime);
        File loader = new File(rootfs, "lib/ld-linux-aarch64.so.1");
        File node = new File(rootfs, "opt/node24/bin/node");
        File dsh = new File(rootfs, "opt/node24/lib/node_modules/@deepseek-ai/dsh/lib/bin.js");
        File home = new File(rootfs, "home");
        File tmp = new File(rootfs, "tmp");
        home.mkdirs();
        tmp.mkdirs();
        String libPath = String.join(":",
            new File(rootfs, "lib/aarch64-linux-gnu").getAbsolutePath(),
            new File(rootfs, "usr/lib/aarch64-linux-gnu").getAbsolutePath(),
            new File(rootfs, "lib").getAbsolutePath(),
            new File(rootfs, "usr/lib").getAbsolutePath());
        List<String> command = new ArrayList<>();
        command.add(loader.getAbsolutePath());
        command.add("--library-path");
        command.add(libPath);
        command.add(node.getAbsolutePath());
        command.add(dsh.getAbsolutePath());
        command.add("web");
        command.add("--patch");
        command.add(storagePatch.getAbsolutePath());
        command.add("--no-open");
        command.add("--port");
        command.add(String.valueOf(port));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(home);
        builder.redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.put("CORTEX_ROOT", rootfs.getAbsolutePath());
        env.put("HOME", home.getAbsolutePath());
        env.put("DSH_HOME", new File(home, ".dsh").getAbsolutePath());
        env.put("TMPDIR", tmp.getAbsolutePath());
        env.put("LD_LIBRARY_PATH", libPath);
        env.put("LD_PRELOAD", new File(runtime, "libcortex-hook.so").getAbsolutePath());
        env.put("GLIBC_TUNABLES", "glibc.pthread.rseq=0");
        // Android provides no usable Linux sandbox backend; keep the permission
        // default and approval policy consistent with full access.
        env.put("DSH_PERMISSION_MODE", "danger-full-access");
        env.put("PATH", "/opt/node24/bin:/opt/node24/lib/node_modules/.bin:"
            + "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        String providerKey = ProviderSecretStore.load(this);
        if (!providerKey.isEmpty()) env.put(ProviderConfig.PROVIDER_ENV, providerKey);
        Log.i(TAG, "starting service host: " + String.join(" ", command));
        hostProcess = builder.start();
        outputThread = new Thread(this::readOutput, "dsh-output");
        outputThread.setDaemon(true);
        outputThread.start();
      } catch (Exception error) {
        Log.e(TAG, "service host start failed", error);
        starting = false;
        lastMessage = String.valueOf(error);
        broadcast(STATE_ERROR, null, lastMessage);
        updateNotification("dsh Host failed", false);
      }
    }, "dsh-start").start();
  }

  private void readOutput() {
    Process process = hostProcess;
    StringBuilder tail = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        Log.i(TAG, "host: " + line);
        tail.append(line).append('\n');
        if (tail.length() > 4000) tail.delete(0, tail.length() - 4000);
        if (line.contains("http://127.0.0.1:")) {
          currentUrl = line.substring(line.indexOf("http://127.0.0.1:")).trim();
          starting = false;
          consecutiveFailures = 0;
          broadcast(STATE_READY, currentUrl, "");
          updateNotification("dsh is running", true);
        }
      }
      int exit = process.waitFor();
      long uptime = System.currentTimeMillis() - hostStartedAt;
      hostProcess = null;
      starting = false;
      if (stopping) return;
      if (uptime > 15_000) consecutiveFailures = 0;
      consecutiveFailures++;
      if (consecutiveFailures <= 5) {
        int delay = Math.min(30, 1 << consecutiveFailures);
        lastMessage = "Host exited (" + exit + "), restarting in " + delay + "s\n\n" + tail;
        Log.w(TAG, "service host exited: " + exit + ", retry " + consecutiveFailures + " in " + delay + "s");
        broadcast(STATE_STARTING, null, lastMessage);
        updateNotification("Restarting dsh Host in " + delay + "s…", false);
        scheduleRestart(delay);
      } else {
        lastMessage = "Host failed repeatedly (" + exit + ")\n\n" + tail;
        Log.e(TAG, "service host failed repeatedly");
        broadcast(STATE_ERROR, null, lastMessage);
        updateNotification("dsh Host failed repeatedly", false);
      }
    } catch (Exception error) {
      if (stopping) {
        hostProcess = null;
        starting = false;
        return;
      }
      Log.e(TAG, "service host output failed", error);
      starting = false;
      lastMessage = String.valueOf(error);
      broadcast(STATE_ERROR, null, lastMessage);
      updateNotification("dsh Host output failed", false);
    }
  }

  private void broadcastState() {
    if (currentUrl != null) broadcast(STATE_READY, currentUrl, "");
    else if (hostProcess != null) broadcast(STATE_STARTING, null, "");
    else if (!lastMessage.isEmpty()) broadcast(STATE_EXITED, null, lastMessage);
    else broadcast(STATE_STARTING, null, "");
  }

  private void broadcast(String state, String url, String message) {
    Intent event = new Intent(EVENT).setPackage(getPackageName());
    event.putExtra(EXTRA_STATE, state);
    if (url != null) event.putExtra(EXTRA_URL, url);
    if (message != null) event.putExtra(EXTRA_MESSAGE, message);
    sendBroadcast(event);
  }

  private Notification notification(String text, boolean ready) {
    Intent open = new Intent(this, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent openIntent = PendingIntent.getActivity(
        this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stopIntent = PendingIntent.getService(
        this, 1, new Intent(this, HostKeepAliveService.class).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, CHANNEL)
        .setContentTitle(ready ? "dsh is running" : "dsh Host")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentIntent(openIntent)
        .addAction(new Notification.Action.Builder(
            null, "Stop", stopIntent).build())
        .setOngoing(true)
        .build();
  }

  private void updateNotification(String text, boolean ready) {
    getSystemService(NotificationManager.class)
        .notify(NOTIFICATION_ID, notification(text, ready));
  }

  private int findFreePort() {
    for (int port = MIN_PORT; port <= MAX_PORT; port++) {
      try (ServerSocket socket = new ServerSocket()) {
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("127.0.0.1", port));
        return port;
      } catch (IOException ignored) {
        // try the next candidate
      }
    }
    return -1;
  }

  private void scheduleRestart(int seconds) {
    new Thread(() -> {
      try {
        Thread.sleep(seconds * 1000L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return;
      }
      if (!stopping && hostProcess == null) startHost();
    }, "dsh-restart").start();
  }

  private void prepareHook(File runtimeDir) throws IOException {
    if (!runtimeDir.isDirectory() && !runtimeDir.mkdirs()) {
      throw new IOException("cannot create " + runtimeDir);
    }
    AssetManager assets = getAssets();
    for (String name : RUNTIME_ASSETS) {
      File target = new File(runtimeDir, name);
      try (InputStream input = assets.open(name);
           FileOutputStream output = new FileOutputStream(target)) {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
      }
      try {
        Os.chmod(target.getAbsolutePath(), 0755);
      } catch (android.system.ErrnoException error) {
        throw new IOException("cannot chmod " + target, error);
      }
    }
  }

  private void stopHost() {
    stopping = true;
    if (hostProcess != null) {
      hostProcess.destroy();
      hostProcess = null;
    }
    currentUrl = null;
    starting = false;
    stopForeground(true);
  }

  @Override public void onDestroy() {
    DeviceBridge.stop();
    stopHost();
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent intent) {
    return null;
  }
}
