package com.dshd.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal Android shell for the local dsh Cortex-style runtime.
 *
 * Cortex runs an Ubuntu 24.04 glibc userland directly on the Android kernel: it starts
 * ELF programs through the rootfs ld.so and maps /usr, /etc, ... with libcortex-hook.
 * No ptrace and no PRoot, which matters because the Android app seccomp filter breaks
 * PRoot's syscall translation (it returns ENOSYS for package.json reads).
 */
public final class MainActivity extends Activity {
  private static final String TAG = "dshd";
  private static final String ROOTFS_NAME = "rootfs";
  private static final String ROOTFS_VERSION_FILE = "rootfs.version";
  private static final String[] PRESERVED_HOME = {
      ".dsh", "Documents", "workspace", ".ssh", ".gnupg", ".npmrc"
  };
  private static final int REQUEST_FILE_CHOOSER = 44;
  private static final int REQUEST_ROOTFS = 41;
  private static final int REQUEST_WEB_PERMISSIONS = 42;
  private static final int REQUEST_DEVICE_PERMISSIONS = 45;
  private static final int REQUEST_GEOLOCATION = 46;
  private static final int REQUEST_NOTIFICATIONS = 47;
  static final String EXTRA_OPEN_PERMISSION = "openPermission";

  private WebView webView;
  private LinearLayout rootLayout;
  private LinearLayout actionRow;
  private BootOverlay bootOverlay;
  private long bootShownAt;
  private boolean revealPolling;
  private LinearLayout providerForm;
  private ScrollView providerScroll;
  private EditText providerUrl;
  private EditText providerKey;
  private EditText providerModel;
  private TextView providerStatus;
  private Button providerBack;
  private TextView status;
  private TextView settingsButton;
  private TextView storageButton;
  private TextView permissionsButton;
  private Button importButton;
  private Button downloadButton;
  private File rootfsDir;
  private String loadedHostUrl;
  private boolean receiverRegistered;
  private boolean rootfsInstalling;
  private boolean providerEditing;
  private PermissionRequest pendingWebRequest;
  private String[] pendingWebResources;
  private GeolocationPermissions.Callback pendingGeoCallback;
  private String pendingGeoOrigin;
  private AlertDialog permissionsDialog;
  private AlertDialog permissionConfirmDialog;
  private String pendingPermissionKey;
  private ValueCallback<Uri[]> filePathCallback;

  private final BroadcastReceiver hostReceiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      String state = intent.getStringExtra(HostKeepAliveService.EXTRA_STATE);
      if (HostKeepAliveService.STATE_READY.equals(state)) {
        String url = intent.getStringExtra(HostKeepAliveService.EXTRA_URL);
        if (url == null || url.equals(loadedHostUrl)) return;
        loadedHostUrl = url;
        status.setVisibility(View.GONE);
        actionRow.setVisibility(View.VISIBLE);
        settingsButton.setVisibility(View.VISIBLE);
        storageButton.setVisibility(View.VISIBLE);
        permissionsButton.setVisibility(View.VISIBLE);
        updateStorageLabel();
        importButton.setVisibility(View.GONE);
        downloadButton.setVisibility(View.GONE);
        if (providerScroll != null) providerScroll.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        if (bootOverlay != null) bootOverlay.setMessage("Rootfs ready");
        webView.loadUrl(url);
        maybeRequestNotificationPermission();
      } else if (HostKeepAliveService.STATE_ERROR.equals(state)
          || HostKeepAliveService.STATE_EXITED.equals(state)) {
        showStatus(intent.getStringExtra(HostKeepAliveService.EXTRA_MESSAGE), false);
        hideBootOverlay();
        if (!RootfsInstaller.isReady(rootfsDir)) showSetup();
      } else if (HostKeepAliveService.STATE_STARTING.equals(state)
          && webView.getUrl() == null) {
        showStatus("Starting local dsh Host…", false);
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    configureWindowForLaunch();
    handleDebugDownload(getIntent());

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(5, 7, 15));
    rootLayout = root;

    status = new TextView(this);
    status.setTextSize(12);
    status.setTextColor(Color.WHITE);
    status.setBackgroundColor(Color.rgb(24, 24, 24));
    status.setPadding(20, 16, 20, 16);
    status.setText("Preparing dsh runtime…");
    root.addView(status, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    actionRow = new LinearLayout(this);
    actionRow.setOrientation(LinearLayout.HORIZONTAL);
    actionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
    actionRow.setPadding(dp(8), dp(7), dp(8), dp(5));
    actionRow.setBackgroundColor(Color.rgb(7, 12, 22));
    actionRow.setVisibility(View.GONE);

    settingsButton = new TextView(this);
    settingsButton.setText("Provider");
    settingsButton.setTextSize(12);
    settingsButton.setSingleLine(true);
    settingsButton.setTextColor(Color.rgb(219, 230, 247));
    settingsButton.setTextSize(12);
    settingsButton.setGravity(android.view.Gravity.CENTER);
    settingsButton.setBackground(navBackground());
    settingsButton.setPadding(dp(8), dp(9), dp(8), dp(9));
    settingsButton.setVisibility(View.GONE);
    settingsButton.setOnClickListener(view -> showProviderSetup(true));
    actionRow.addView(settingsButton, navParams(0.8f));

    storageButton = new TextView(this);
    storageButton.setText("Workspace: Internal");
    storageButton.setTextSize(12);
    storageButton.setSingleLine(true);
    storageButton.setTextColor(Color.rgb(219, 230, 247));
    storageButton.setTextSize(12);
    storageButton.setGravity(android.view.Gravity.CENTER);
    storageButton.setBackground(navBackground());
    storageButton.setPadding(dp(8), dp(9), dp(8), dp(9));
    storageButton.setVisibility(View.GONE);
    storageButton.setOnClickListener(view -> showStorageDialog());
    actionRow.addView(storageButton, navParams(1.2f));

    permissionsButton = new TextView(this);
    permissionsButton.setText("Permissions");
    permissionsButton.setTextSize(12);
    permissionsButton.setSingleLine(true);
    permissionsButton.setTextColor(Color.rgb(219, 230, 247));
    permissionsButton.setTextSize(12);
    permissionsButton.setGravity(android.view.Gravity.CENTER);
    permissionsButton.setBackground(navBackground());
    permissionsButton.setPadding(dp(8), dp(9), dp(8), dp(9));
    permissionsButton.setVisibility(View.GONE);
    permissionsButton.setOnClickListener(view -> showPermissionsDialog());
    actionRow.addView(permissionsButton, navParams(1f));

    root.addView(actionRow, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    importButton = new Button(this);
    importButton.setText("Import rootfs (.tar.gz)");
    importButton.setVisibility(View.GONE);
    importButton.setOnClickListener(view -> pickRootfs());
    root.addView(importButton, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    downloadButton = new Button(this);
    downloadButton.setText("Download rootfs");
    downloadButton.setVisibility(View.GONE);
    downloadButton.setOnClickListener(view -> startDownload());
    root.addView(downloadButton, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    webView = new WebView(this);
    webView.setBackgroundColor(Color.rgb(5, 7, 15));
    WebView.setWebContentsDebuggingEnabled(true);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    webView.addJavascriptInterface(new PdfBridge(this), "DshdPdf");
    webView.setWebViewClient(new WebViewClient() {
      @Override public void onPageCommitVisible(WebView view, String url) {
        if (!isHostPage(url)) return;
        Log.i(TAG, "boot: first paint after " + bootElapsed() + " ms");
        armRevealPoll();
      }

      @Override public void onPageFinished(WebView view, String url) {
        if (!isHostPage(url)) return;
        Log.i(TAG, "boot: page finished after " + bootElapsed() + " ms");
        armRevealPoll();
      }
    });
    webView.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
        Log.i(TAG, "console: " + message.message() + " @"
            + message.sourceId() + ":" + message.lineNumber());
        return true;
      }

      @Override public void onPermissionRequest(final PermissionRequest request) {
        runOnUiThread(() -> handleWebPermission(request));
      }

      @Override public void onPermissionRequestCanceled(PermissionRequest request) {
        if (pendingWebRequest == request) {
          pendingWebRequest = null;
          pendingWebResources = null;
        }
      }

      @Override public void onGeolocationPermissionsShowPrompt(
          String origin, GeolocationPermissions.Callback callback) {
        runOnUiThread(() -> handleGeolocationPrompt(origin, callback));
      }

      @Override public boolean onShowFileChooser(WebView view,
          ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
        filePathCallback = callback;
        try {
          Intent intent = params.createIntent();
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(Intent.createChooser(intent, "选择文件"),
              REQUEST_FILE_CHOOSER);
          return true;
        } catch (Exception error) {
          Log.e(TAG, "file chooser failed", error);
          filePathCallback = null;
          return false;
        }
      }
    });
    root.addView(webView, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

    FrameLayout container = new FrameLayout(this);
    container.addView(root, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    bootOverlay = new BootOverlay(this);
    bootShownAt = SystemClock.uptimeMillis();
    container.addView(bootOverlay, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    setContentView(container);

    rootfsDir = new File(getFilesDir(), ROOTFS_NAME);
    handlePermissionIntent(getIntent());
    IntentFilter filter = new IntentFilter(HostKeepAliveService.EVENT);
    if (Build.VERSION.SDK_INT >= 33) {
      registerReceiver(hostReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
      registerReceiver(hostReceiver, filter);
    }
    receiverRegistered = true;
    boolean debuggable = (getApplicationInfo().flags
        & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    String debugArchive = debuggable ? getIntent().getStringExtra("rootfsArchive") : null;
    String debugUrl = debuggable ? getIntent().getStringExtra("rootfsUrl") : null;
    String debugSha = debuggable ? getIntent().getStringExtra("rootfsSha256") : null;
    if (debugUrl != null) {
      getIntent().removeExtra("rootfsUrl");
      downloadAndInstall(debugUrl, debugSha == null ? "" : debugSha);
    } else if (debugArchive != null) {
      getIntent().removeExtra("rootfsArchive");
      installRootfsFromFile(new File(debugArchive));
    } else if (!RootfsInstaller.isReady(rootfsDir)) {
      if (BuildConfig.ROOTFS_EMBEDDED) {
        installBundledRootfs(false);
      } else {
        showSetup();
      }
    } else if (BuildConfig.ROOTFS_EMBEDDED
        && !BuildConfig.ROOTFS_VERSION.equals(readRootfsVersion())) {
      // An APK update can carry a newer rootfs. Preserve user data and replace
      // the old tree before starting the Host.
      installBundledRootfs(true);
    } else {
      // Starting a foreground service from onCreate races the activity's
      // foreground transition on Android 12 and throws
      // BackgroundServiceStartNotAllowedException. onResume() starts it once
      // the activity is actually in front.
      if (ProviderConfig.isConfigured(rootfsDir, this)) {
        showStatus("Starting local dsh Host…", false);
      } else {
        showProviderSetup(false);
      }
    }
  }

  private void handleWebPermission(PermissionRequest request) {
    Log.i(TAG, "web permission request: " + Arrays.toString(request.getResources()));
    List<String> resources = Arrays.asList(request.getResources());
    boolean wantsAudio = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
    boolean wantsVideo = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
    if (!wantsAudio && !wantsVideo) {
      request.deny();
      return;
    }
    List<String> needed = new ArrayList<>();
    if (wantsAudio) needed.add(Manifest.permission.RECORD_AUDIO);
    if (wantsVideo) needed.add(Manifest.permission.CAMERA);
    if (hasAll(needed)) {
      grantWebResources(request, wantsAudio, wantsVideo);
      return;
    }
    if (pendingWebRequest != null) pendingWebRequest.deny();
    pendingWebRequest = request;
    pendingWebResources = needed.toArray(new String[0]);
    requestPermissions(pendingWebResources, REQUEST_WEB_PERMISSIONS);
  }

  private void grantWebResources(PermissionRequest request, boolean audio, boolean video) {
    List<String> grant = new ArrayList<>();
    if (audio) grant.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
    if (video) grant.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
    request.grant(grant.toArray(new String[0]));
  }

  private void handleGeolocationPrompt(String origin, GeolocationPermissions.Callback callback) {
    if (DevicePermissions.locationGranted(this)) {
      callback.invoke(origin, true, false);
      return;
    }
    if (pendingGeoCallback != null) pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
    pendingGeoCallback = callback;
    pendingGeoOrigin = origin;
    requestPermissions(DevicePermissions.required(this, DevicePermissions.LOCATION),
        REQUEST_GEOLOCATION);
  }

  private boolean hasAll(List<String> permissions) {
    for (String permission : permissions) {
      if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
    }
    return true;
  }

  @Override public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_WEB_PERMISSIONS) {
      PermissionRequest request = pendingWebRequest;
      String[] needed = pendingWebResources;
      pendingWebRequest = null;
      pendingWebResources = null;
      if (request == null) return;
      boolean granted = needed != null && hasAll(Arrays.asList(needed));
      if (granted) {
        List<String> resources = Arrays.asList(request.getResources());
        grantWebResources(request,
            resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE));
      } else {
        request.deny();
      }
      return;
    }
    if (requestCode == REQUEST_GEOLOCATION) {
      GeolocationPermissions.Callback callback = pendingGeoCallback;
      String origin = pendingGeoOrigin;
      pendingGeoCallback = null;
      pendingGeoOrigin = null;
      if (callback == null) return;
      callback.invoke(origin, DevicePermissions.locationGranted(this), false);
      return;
    }
    if (requestCode == REQUEST_DEVICE_PERMISSIONS) {
      String key = pendingPermissionKey;
      pendingPermissionKey = null;
      if (!DevicePermissions.isGranted(this, key)
          && !shouldShowRequestPermissionRationaleFor(key)) {
        showPermissionSettingsHint(key);
      }
      refreshPermissionsDialog();
      return;
    }
    if (requestCode == REQUEST_NOTIFICATIONS) return;
  }

  private boolean shouldShowRequestPermissionRationaleFor(String key) {
    for (String permission : DevicePermissions.required(this, key)) {
      if (shouldShowRequestPermissionRationale(permission)) return true;
    }
    return false;
  }

  private void requestDevicePermission(String key) {
    if (DevicePermissions.isGranted(this, key)) {
      Toast.makeText(this, DevicePermissions.label(key) + " is already granted.",
          Toast.LENGTH_SHORT).show();
      return;
    }
    if (DevicePermissions.isHighRisk(key)) {
      confirmHighRiskPermission(key);
      return;
    }
    performDevicePermissionRequest(key);
  }

  private void performDevicePermissionRequest(String key) {
    String[] required = DevicePermissions.required(this, key);
    if (required.length == 0) return;
    pendingPermissionKey = key;
    requestPermissions(required, REQUEST_DEVICE_PERMISSIONS);
  }

  /**
   * A second, app-level confirmation for sensitive permissions. Android's own
   * dialog follows only after the user explicitly continues here.
   */
  private void confirmHighRiskPermission(String key) {
    if (permissionConfirmDialog != null) return;
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("Confirm sensitive permission")
        .setMessage(DevicePermissions.highRiskMessage(key))
        .setPositiveButton("Continue", (ignored, which) -> new Handler(Looper.getMainLooper())
            .postDelayed(() -> performDevicePermissionRequest(key), 150))
        .setNegativeButton("Cancel", (ignored, which) ->
            Toast.makeText(this, "Permission was not requested.", Toast.LENGTH_SHORT).show())
        .create();
    dialog.setOnDismissListener(ignored -> permissionConfirmDialog = null);
    permissionConfirmDialog = dialog;
    dialog.show();
  }

  private void showPermissionSettingsHint(String key) {
    new AlertDialog.Builder(this)
        .setTitle(DevicePermissions.label(key))
        .setMessage("Android did not grant this permission. If the dialog no longer appears, "
            + "enable it in the system app settings.")
        .setPositiveButton("Open settings", (dialog, which) -> {
          Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.fromParts("package", getPackageName(), null));
          startActivity(intent);
        })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showPermissionsDialog() {
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    int pad = Math.round(18 * getResources().getDisplayMetrics().density);
    content.setPadding(pad, pad / 2, pad, 0);
    TextView intro = new TextView(this);
    intro.setText("Grant Android capabilities the agent is allowed to use. "
        + "Permissions can be revoked in system settings at any time.");
    intro.setTextSize(13);
    content.addView(intro);
    for (String key : DevicePermissions.KEYS) {
      Button row = new Button(this);
      row.setAllCaps(false);
      boolean granted = DevicePermissions.isGranted(this, key);
      row.setText(DevicePermissions.label(key) + (granted ? "  ✓ Granted" : "  — Grant"));
      row.setEnabled(!granted);
      if (!granted) row.setOnClickListener(view -> requestDevicePermission(key));
      content.addView(row);
      String detail = DevicePermissions.detail(key);
      if (!detail.isEmpty()) {
        TextView note = new TextView(this);
        note.setText(detail);
        note.setTextSize(11);
        note.setPadding(pad, 0, pad, pad / 4);
        content.addView(note);
      }
    }
    Button settings = new Button(this);
    settings.setAllCaps(false);
    settings.setText("Open Android app settings");
    settings.setOnClickListener(view -> startActivity(new Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", getPackageName(), null))));
    content.addView(settings);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    permissionsDialog = new AlertDialog.Builder(this)
        .setTitle("Android permissions")
        .setView(scroll)
        .setNegativeButton("Close", null)
        .create();
    permissionsDialog.setOnDismissListener(dialog -> permissionsDialog = null);
    permissionsDialog.show();
  }

  private void refreshPermissionsDialog() {
    if (permissionsDialog != null) {
      permissionsDialog.dismiss();
      showPermissionsDialog();
    }
  }

  private void maybeRequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return;
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED) return;
    android.content.SharedPreferences prefs = getSharedPreferences("dshd_permissions", MODE_PRIVATE);
    if (prefs.getBoolean("notification_asked", false)) return;
    prefs.edit().putBoolean("notification_asked", true).apply();
    requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS},
        REQUEST_NOTIFICATIONS);
  }

  private void startHostOrSetup() {
    migrateProviderKey();
    if (!ProviderConfig.isConfigured(rootfsDir, this)) showProviderSetup(false);
    else {
      if (providerScroll != null) providerScroll.setVisibility(View.GONE);
      if (bootOverlay != null && !bootOverlay.isDismissed()) {
        bootOverlay.setMessage("Starting local dsh Host…");
      }
      webView.setVisibility(View.VISIBLE);
      startHostService();
    }
  }

  private void startHostService() {
    sendHostAction(HostKeepAliveService.ACTION_START);
  }

  private void sendHostAction(String action) {
    Intent intent = new Intent(this, HostKeepAliveService.class).setAction(action);
    try {
      // HostKeepAliveService is a foreground service. startService() is subject
      // to Android 12 background-start restrictions even from a just-resumed
      // activity; startForegroundService() is the supported entry point.
      startForegroundService(intent);
    } catch (Exception error) {
      Log.e(TAG, "cannot start Host service", error);
      showStatus("Cannot start the local Host service: " + error, true);
    }
  }

  private void handlePermissionIntent(Intent intent) {
    if (intent == null) return;
    String permission = intent.getStringExtra(EXTRA_OPEN_PERMISSION);
    if (permission == null) return;
    intent.removeExtra(EXTRA_OPEN_PERMISSION);
    new Handler(Looper.getMainLooper()).postDelayed(() -> {
      if (permissionsDialog == null) showPermissionsDialog();
      if (Arrays.asList(DevicePermissions.KEYS).contains(permission)) {
        requestDevicePermission(permission);
      }
    }, 300);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!rootfsInstalling && RootfsInstaller.isReady(rootfsDir)) startHostOrSetup();
  }

  private void showProviderSetup(boolean editing) {
    providerEditing = editing;
    hideBootOverlay();
    actionRow.setVisibility(View.VISIBLE);
    settingsButton.setVisibility(View.GONE);
    storageButton.setVisibility(View.VISIBLE);
    permissionsButton.setVisibility(View.VISIBLE);
    updateStorageLabel();
    importButton.setVisibility(View.GONE);
    downloadButton.setVisibility(View.GONE);
    webView.setVisibility(View.GONE);
    if (providerForm == null) {
      providerForm = new LinearLayout(this);
      providerForm.setOrientation(LinearLayout.VERTICAL);
      providerForm.setBackgroundColor(Color.rgb(246, 248, 252));
      providerForm.setPadding(36, 32, 36, 32);
      providerBack = new Button(this);
      providerBack.setAllCaps(false);
      providerBack.setText("← Back to chat");
      providerBack.setOnClickListener(view -> hideProviderSetup());
      providerBack.setVisibility(View.GONE);
      providerForm.addView(providerBack);
      TextView title = new TextView(this);
      title.setText("Connect a model");
      title.setTextSize(22);
      title.setTextColor(Color.rgb(24, 24, 24));
      providerForm.addView(title);
      TextView hint = new TextView(this);
      hint.setText("Enter an OpenAI-compatible base URL and API key. "
          + "These stay in the app-private rootfs.");
      hint.setTextSize(13);
      hint.setPadding(0, 12, 0, 22);
      providerForm.addView(hint);
      providerUrl = new EditText(this);
      providerUrl.setHint("Base URL (https://…/v1)");
      providerUrl.setSingleLine(true);
      providerUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
      providerForm.addView(providerUrl);
      providerKey = new EditText(this);
      providerKey.setHint("API key");
      providerKey.setSingleLine(true);
      providerKey.setInputType(InputType.TYPE_CLASS_TEXT
          | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      providerKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
      providerForm.addView(providerKey);
      providerModel = new EditText(this);
      providerModel.setText(ProviderConfig.DEFAULT_MODEL);
      providerModel.setHint("Model id");
      providerModel.setSingleLine(true);
      providerForm.addView(providerModel);
      Button save = new Button(this);
      save.setText("Save and start dsh");
      save.setOnClickListener(view -> saveProvider());
      providerForm.addView(save);
      providerStatus = new TextView(this);
      providerStatus.setTextSize(12);
      providerStatus.setPadding(0, 12, 0, 0);
      providerForm.addView(providerStatus);
      providerScroll = new ScrollView(this);
      providerScroll.setBackgroundColor(Color.rgb(246, 248, 252));
      providerScroll.addView(providerForm);
      rootLayout.addView(providerScroll, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }
    if (editing) {
      if (providerUrl.getText().toString().isEmpty()) {
        String savedUrl = ProviderConfig.readBaseUrl(rootfsDir);
        if (!savedUrl.isEmpty()) providerUrl.setText(savedUrl);
      }
      if (providerModel.getText().toString().trim().isEmpty()
          || providerModel.getText().toString().equals(ProviderConfig.DEFAULT_MODEL)) {
        String savedModel = ProviderConfig.readModel(rootfsDir);
        if (!savedModel.isEmpty()) providerModel.setText(savedModel);
      }
      providerKey.setHint("API key (blank = keep)");
      providerBack.setVisibility(View.VISIBLE);
      status.setText("Provider settings");
      providerStatus.setText("");
    } else {
      providerKey.setHint("API key");
      providerBack.setVisibility(View.GONE);
      status.setText("Configure the model provider for this device.");
    }
    status.setVisibility(View.VISIBLE);
    providerScroll.setVisibility(View.VISIBLE);
    providerForm.setVisibility(View.VISIBLE);
  }

  private void migrateProviderKey() {
    try {
      if (!ProviderSecretStore.has(this)) {
        String legacy = ProviderConfig.readStoredKey(rootfsDir);
        if (!legacy.isEmpty()) {
          ProviderSecretStore.save(this, legacy);
          Log.i(TAG, "migrated provider key to Android Keystore");
        }
      }
      ProviderConfig.stripProviderKey(rootfsDir);
    } catch (Exception error) {
      Log.e(TAG, "provider key migration failed", error);
    }
  }

  private void hideProviderSetup() {
    if (providerScroll != null) providerScroll.setVisibility(View.GONE);
    providerBack.setVisibility(View.GONE);
    webView.setVisibility(View.VISIBLE);
    actionRow.setVisibility(View.VISIBLE);
    settingsButton.setVisibility(View.VISIBLE);
    storageButton.setVisibility(View.VISIBLE);
    permissionsButton.setVisibility(View.VISIBLE);
    status.setVisibility(View.GONE);
    if (loadedHostUrl == null && RootfsInstaller.isReady(rootfsDir)) startHostOrSetup();
  }

  @Override
  public void onBackPressed() {
    if (providerScroll != null && providerScroll.getVisibility() == View.VISIBLE
        && providerEditing) {
      hideProviderSetup();
      return;
    }
    super.onBackPressed();
  }

  private static String stripQuotes(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private void saveProvider() {
    String url = stripQuotes(providerUrl.getText().toString().trim());
    String modelText = stripQuotes(providerModel.getText().toString().trim());
    String key = providerKey.getText().toString().trim();
    boolean keepKey = providerEditing && key.isEmpty() && ProviderSecretStore.has(this);
    if (url.trim().isEmpty() || (key.isEmpty() && !keepKey)) {
      providerStatus.setText(providerEditing
          ? "Base URL is required (leave API key blank to keep it)."
          : "Base URL and API key are required.");
      return;
    }
    providerStatus.setText("Saving provider configuration…");
    new Thread(() -> {
      try {
        if (keepKey) {
          ProviderConfig.writeProfile(rootfsDir, url, providerModel.getText().toString());
        } else {
          ProviderConfig.write(rootfsDir, url, key, providerModel.getText().toString());
          ProviderSecretStore.save(this, key);
        }
        runOnUiThread(() -> {
          providerStatus.setText("Saved.");
          status.setVisibility(View.GONE);
          providerScroll.setVisibility(View.GONE);
          webView.setVisibility(View.VISIBLE);
          actionRow.setVisibility(View.VISIBLE);
          settingsButton.setVisibility(View.VISIBLE);
          storageButton.setVisibility(View.VISIBLE);
          permissionsButton.setVisibility(View.VISIBLE);
          updateStorageLabel();
          if (loadedHostUrl != null) restartHostForStorage();
          else startHostService();
        });
      } catch (Exception error) {
        Log.e(TAG, "provider config failed", error);
        runOnUiThread(() -> providerStatus.setText("Save failed: " + error));
      }
    }, "provider-config").start();
  }

  private void updateStorageLabel() {
    storageButton.setText("Storage: " + StorageConfig.currentLabel(this));
  }

  private void showStorageDialog() {
    final File removable = StorageConfig.removableDir(this);
    final File external = StorageConfig.externalDir(this);
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    int pad = Math.round(20 * getResources().getDisplayMetrics().density);
    content.setPadding(pad, pad / 2, pad, 0);

    TextView note = new TextView(this);
    note.setText("Applies to new Workspaces. Existing Workspaces are not moved. "
        + "The Linux rootfs and app data stay in internal storage because shared "
        + "storage is mounted noexec.");
    note.setTextSize(13);
    content.addView(note);

    Button internal = new Button(this);
    internal.setAllCaps(false);
    internal.setText("Internal app storage" + (StorageConfig.MODE_INTERNAL.equals(StorageConfig.mode(this)) ? "  ✓" : ""));
    content.addView(internal);

    Button sd = new Button(this);
    sd.setAllCaps(false);
    String externalLabel = removable != null
        ? "SD card: " + removable.getAbsolutePath()
        : "External app storage: " + (external == null ? "unavailable" : external.getAbsolutePath());
    sd.setText(externalLabel + (StorageConfig.MODE_EXTERNAL.equals(StorageConfig.mode(this)) ? "  ✓" : ""));
    sd.setEnabled(removable != null || external != null);
    content.addView(sd);

    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("Workspace storage")
        .setView(content)
        .setNegativeButton("Cancel", null)
        .create();
    internal.setOnClickListener(view -> {
      dialog.dismiss();
      selectStorage(StorageConfig.MODE_INTERNAL);
    });
    sd.setOnClickListener(view -> {
      dialog.dismiss();
      selectStorage(StorageConfig.MODE_EXTERNAL);
    });
    dialog.show();
  }

  private void selectStorage(String mode) {
    StorageConfig.setMode(this, mode);
    updateStorageLabel();
    boolean formVisible = providerScroll != null
        && providerScroll.getVisibility() == View.VISIBLE;
    if (ProviderConfig.isConfigured(rootfsDir, this) && !formVisible) {
      restartHostForStorage();
    } else if (providerStatus != null) {
      providerStatus.setText("Workspace storage will be applied when dsh starts.");
    }
  }

  private void restartHostForStorage() {
    status.setVisibility(View.VISIBLE);
    status.setText("Restarting dsh Host on the selected workspace storage…");
    sendHostAction(HostKeepAliveService.ACTION_STOP);
    new Handler(Looper.getMainLooper()).postDelayed(
        () -> sendHostAction(HostKeepAliveService.ACTION_START), 700);
  }

  private String rootfsUrl() {
    String url = getString(R.string.rootfs_download_url).trim();
    return url.isEmpty() ? BuildConfig.ROOTFS_URL : url;
  }

  private void showSetup() {
    hideBootOverlay();
    actionRow.setVisibility(View.GONE);
    showStatus("dsh runtime is not installed yet.\n\n"
        + "Select a rootfs .tar.gz archive to install it once.\n"
        + "Expected files: lib/ld-linux-aarch64.so.1, opt/node24/bin/node, "
        + "opt/node24/lib/node_modules/@deepseek-ai/dsh/lib/bin.js", false);
    importButton.setVisibility(View.VISIBLE);
    String url = rootfsUrl();
    downloadButton.setVisibility(url.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void pickRootfs() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, REQUEST_ROOTFS);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_FILE_CHOOSER) {
      if (filePathCallback == null) return;
      Uri[] results = null;
      if (resultCode == RESULT_OK && data != null) {
        android.content.ClipData clip = data.getClipData();
        if (clip != null) {
          results = new Uri[clip.getItemCount()];
          for (int i = 0; i < clip.getItemCount(); i += 1) {
            results[i] = clip.getItemAt(i).getUri();
          }
        } else if (data.getData() != null) {
          results = new Uri[] {data.getData()};
        }
      }
      filePathCallback.onReceiveValue(results);
      filePathCallback = null;
      return;
    }
    if (requestCode != REQUEST_ROOTFS || resultCode != RESULT_OK || data == null) return;
    Uri uri = data.getData();
    if (uri == null) return;
    importButton.setVisibility(View.GONE);
    new Thread(() -> {
      try (InputStream input = getContentResolver().openInputStream(uri)) {
        if (input == null) throw new IllegalStateException("cannot open " + uri);
        RootfsInstaller.install(input, rootfsDir, -1,
            (message, percent) -> runOnUiThread(() -> showStatus(message, false)));
        writeRootfsVersion("external");
        runOnUiThread(this::startHostOrSetup);
      } catch (Exception error) {
        Log.e(TAG, "rootfs import failed", error);
        runOnUiThread(() -> {
          showStatus("Rootfs import failed: " + error + "\n\nSelect another archive.", false);
          importButton.setVisibility(View.VISIBLE);
        });
      }
    }, "rootfs-import").start();
  }

  private void startDownload() {
    String url = rootfsUrl();
    String sha = getString(R.string.rootfs_sha256).trim();
    if (sha.isEmpty()) sha = BuildConfig.ROOTFS_SHA256;
    if (url.isEmpty()) {
      showStatus("No rootfs download URL is configured in this build.", false);
      return;
    }
    downloadAndInstall(url, sha);
  }

  private void downloadAndInstall(String url, String sha256) {
    importButton.setVisibility(View.GONE);
    downloadButton.setVisibility(View.GONE);
    new Thread(() -> {
      File archive = new File(getCacheDir(), "rootfs-download.tar.gz");
      try {
        RootfsInstaller.download(url, sha256, archive,
            (message, percent) -> runOnUiThread(() -> showStatus(message, false)));
        try (InputStream input = new FileInputStream(archive)) {
          RootfsInstaller.install(input, rootfsDir, archive.length(),
              (message, percent) -> runOnUiThread(() -> showStatus(message, false)));
        }
        writeRootfsVersion("external");
        //noinspection ResultOfMethodCallIgnored
        archive.delete();
        runOnUiThread(this::startHostOrSetup);
      } catch (Exception error) {
        Log.e(TAG, "rootfs download failed", error);
        //noinspection ResultOfMethodCallIgnored
        archive.delete();
        runOnUiThread(() -> {
          showStatus("Rootfs download failed: " + error
              + "\n\nCheck the URL/network or select a local archive.", false);
          showSetup();
        });
      }
    }, "rootfs-download").start();
  }

  private void installRootfsFromFile(File archive) {
    if (!archive.isFile()) {
      showStatus("Rootfs archive not found: " + archive, false);
      showSetup();
      return;
    }
    importButton.setVisibility(View.GONE);
    new Thread(() -> {
      try (InputStream input = new FileInputStream(archive)) {
        RootfsInstaller.install(input, rootfsDir, archive.length(),
            (message, percent) -> runOnUiThread(() -> showStatus(message, false)));
        writeRootfsVersion("external");
        runOnUiThread(this::startHostOrSetup);
      } catch (Exception error) {
        Log.e(TAG, "rootfs import failed", error);
        runOnUiThread(() -> {
          showStatus("Rootfs import failed: " + error, false);
          importButton.setVisibility(View.VISIBLE);
        });
      }
    }, "rootfs-import").start();
  }

  /**
   * Import the rootfs packaged in this APK. On an APK update, preserve the
   * user-facing home data before replacing the tree.
   */
  private void installBundledRootfs(boolean migrate) {
    if (rootfsInstalling) return;
    rootfsInstalling = true;
    importButton.setVisibility(View.GONE);
    downloadButton.setVisibility(View.GONE);
    showStatus(migrate ? "Updating bundled rootfs…" : "Installing bundled rootfs…", false);
    new Thread(() -> {
      File preserve = null;
      try {
        if (migrate || new File(rootfsDir, "home").isDirectory()) {
          preserve = preserveUserData();
        }
        long totalBytes = bundledRootfsLength();
        try (InputStream input = getAssets().open("rootfs.tar.xz")) {
          RootfsInstaller.install(input, rootfsDir, totalBytes,
              (message, percent) -> runOnUiThread(() -> showStatus(message, false)));
        }
        if (preserve != null) restoreUserData(preserve);
        writeRootfsVersion(BuildConfig.ROOTFS_VERSION);
        RootfsInstaller.deleteTree(preserve);
        runOnUiThread(() -> {
          rootfsInstalling = false;
          startHostOrSetup();
        });
      } catch (Exception error) {
        Log.e(TAG, "bundled rootfs install failed", error);
        rootfsInstalling = false;
        File finalPreserve = preserve;
        runOnUiThread(() -> {
          String backup = finalPreserve != null && finalPreserve.exists()
              ? "\n\nUser data backup kept at " + finalPreserve.getAbsolutePath() : "";
          showStatus("Bundled rootfs install failed: " + error + backup, false);
          showSetup();
        });
      }
    }, "bundled-rootfs").start();
  }

  private File preserveUserData() throws Exception {
    File preserve = new File(getFilesDir(),
        "rootfs-userdata." + System.currentTimeMillis());
    if (!preserve.isDirectory() && !preserve.mkdirs()) {
      throw new java.io.IOException("cannot create " + preserve);
    }
    File home = new File(rootfsDir, "home");
    for (String name : PRESERVED_HOME) {
      File source = new File(home, name);
      if (!source.exists()) continue;
      RootfsInstaller.copyTree(source, new File(preserve, name));
    }
    return preserve;
  }

  private void restoreUserData(File preserve) throws Exception {
    File home = new File(rootfsDir, "home");
    for (String name : PRESERVED_HOME) {
      File source = new File(preserve, name);
      if (!source.exists()) continue;
      File target = new File(home, name);
      RootfsInstaller.deleteTree(target);
      RootfsInstaller.copyTree(source, target);
    }
    // The old bridge token/port belongs to the previous Host process.
    //noinspection ResultOfMethodCallIgnored
    new File(home, ".dsh/android-bridge.json").delete();
  }

  private String readRootfsVersion() {
    File file = new File(getFilesDir(), ROOTFS_VERSION_FILE);
    if (!file.isFile()) return "";
    try (InputStream input = new FileInputStream(file)) {
      byte[] data = new byte[(int) Math.min(file.length(), 4096L)];
      int read = input.read(data);
      return read <= 0 ? "" : new String(data, 0, read, StandardCharsets.UTF_8).trim();
    } catch (Exception error) {
      Log.e(TAG, "cannot read rootfs version", error);
      return "";
    }
  }

  private void writeRootfsVersion(String version) throws Exception {
    File file = new File(getFilesDir(), ROOTFS_VERSION_FILE);
    try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
      output.write(version.getBytes(StandardCharsets.UTF_8));
    }
  }

  private void showStatus(String message, boolean retryable) {
    runOnUiThread(() -> {
      status.setText(message);
      status.setVisibility(View.VISIBLE);
      if (bootOverlay != null && !bootOverlay.isDismissed()) {
        bootOverlay.setMessage(message);
      }
      if (retryable) {
        status.setOnClickListener(view -> {
          status.setOnClickListener(null);
          startHostService();
        });
      }
    });
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleDebugDownload(intent);
    handlePermissionIntent(intent);
  }

  /** Debug-only hook to exercise the resumable downloader without replacing the rootfs. */
  private void handleDebugDownload(Intent intent) {
    if (intent == null) return;
    String url = intent.getStringExtra("debugDownloadUrl");
    if (url == null || url.isEmpty()) return;
    if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
    String name = intent.getStringExtra("debugDownloadName");
    if (name == null || name.isEmpty()) name = "debug-download.bin";
    String sha = intent.getStringExtra("debugDownloadSha");
    File destination = new File(getCacheDir(), name);
    String finalName = name;
    String finalSha = sha;
    new Thread(() -> {
      try {
        RootfsInstaller.download(url, finalSha, destination,
            (message, percent) -> Log.i(TAG, "debug-download: " + message));
        Log.i(TAG, "debug-download: complete " + destination.length() + " bytes "
            + finalName);
      } catch (Exception error) {
        Log.e(TAG, "debug-download failed", error);
      }
    }, "debug-download").start();
  }

  private void configureWindowForLaunch() {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    getWindow().setStatusBarColor(Color.rgb(5, 7, 15));
    getWindow().setNavigationBarColor(Color.rgb(5, 7, 15));
    int flags = getWindow().getDecorView().getSystemUiVisibility();
    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    getWindow().getDecorView().setSystemUiVisibility(flags);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable navBackground() {
    GradientDrawable background = new GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        new int[] {0xFF142238, 0xFF0D1727});
    background.setCornerRadius(dp(10));
    background.setStroke(dp(1), 0xFF263B5C);
    return background;
  }

  private LinearLayout.LayoutParams navParams(float weight) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    params.setMargins(dp(3), 0, dp(3), 0);
    return params;
  }

  private void hideBootOverlay() {
    if (bootOverlay != null && !bootOverlay.isDismissed()) bootOverlay.dismiss();
  }

  private long bundledRootfsLength() {
    try (android.content.res.AssetFileDescriptor descriptor =
             getAssets().openFd("rootfs.tar.xz")) {
      return descriptor.getLength();
    } catch (Exception ignored) {
      try (InputStream input = getAssets().open("rootfs.tar.xz")) {
        return input.available();
      } catch (Exception error) {
        return -1;
      }
    }
  }

  private void armRevealPoll() {
    if (bootOverlay == null || bootOverlay.isDismissed() || revealPolling) return;
    revealPolling = true;
    pollReveal(SystemClock.uptimeMillis());
  }

  private void pollReveal(long startedAt) {
    if (bootOverlay == null || bootOverlay.isDismissed()) {
      revealPolling = false;
      return;
    }
    String script = "(function(){try{"
        + "if(document.querySelector('[data-composer-input]'))return true;"
        + "var t=document.body?document.body.innerText:'';"
        + "return document.readyState==='complete'&&t.length>80"
        + "&&!/Loading plugins/i.test(t)&&!/Loading history/i.test(t);"
        + "}catch(e){return false}})()";
    webView.evaluateJavascript(script, value -> {
      if (bootOverlay == null || bootOverlay.isDismissed()) {
        revealPolling = false;
        return;
      }
      long now = SystemClock.uptimeMillis();
      long minWait = Math.max(0, 1200 - (now - bootShownAt));
      boolean ready = "true".equals(value);
      if ((ready && minWait == 0) || now - startedAt >= 15000) {
        revealPolling = false;
        webView.postDelayed(this::finishBoot, minWait);
      } else {
        webView.postDelayed(() -> pollReveal(startedAt), 220);
      }
    });
  }

  private long bootElapsed() {
    return SystemClock.uptimeMillis() - bootShownAt;
  }

  private boolean isHostPage(String url) {
    return url != null && loadedHostUrl != null
        && (url.startsWith(loadedHostUrl) || loadedHostUrl.startsWith(url));
  }

  private void finishBoot() {
    if (bootOverlay != null && !bootOverlay.isDismissed()) bootOverlay.dismiss();
    actionRow.setVisibility(View.VISIBLE);
    status.setVisibility(View.GONE);
    Log.i(TAG, "boot: Web UI ready after " + bootElapsed() + " ms");
  }

  @Override
  protected void onDestroy() {
    if (receiverRegistered) {
      unregisterReceiver(hostReceiver);
      receiverRegistered = false;
    }
    if (webView != null) {
      webView.loadUrl("about:blank");
      webView.destroy();
    }
    super.onDestroy();
  }
}
