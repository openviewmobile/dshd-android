package com.dshd.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Minimal Android shell for the local dsh Cortex-style runtime.
 *
 * Cortex runs an Ubuntu 24.04 glibc userland directly on the Android kernel: it starts
 * ELF programs through the rootfs ld.so and maps /usr, /etc, ... with libcortex-hook.
 * No ptrace and no PRoot, which matters because the Android app seccomp filter breaks
 * PRoot's syscall translation (it returns ENOSYS for package.json reads).
 */
class MainActivity : Activity() {
    private var webView: WebView? = null
    private lateinit var rootLayout: LinearLayout
    private lateinit var actionRow: LinearLayout
    private var bootOverlay: BootOverlay? = null
    private var bootShownAt: Long = 0
    private var revealPolling = false
    private var providerForm: LinearLayout? = null
    private var providerScroll: ScrollView? = null
    private lateinit var providerUrl: EditText
    private lateinit var providerKey: EditText
    private lateinit var providerModel: EditText
    private lateinit var providerStatus: TextView
    private lateinit var providerBack: Button
    private lateinit var status: TextView
    private lateinit var settingsButton: TextView
    private lateinit var storageButton: TextView
    private lateinit var permissionsButton: TextView
    private lateinit var importButton: Button
    private lateinit var downloadButton: Button
    private lateinit var rootfsDir: File
    private var loadedHostUrl: String? = null
    private var receiverRegistered = false
    private var rootfsInstalling = false
    private var providerEditing = false
    private var pendingWebRequest: PermissionRequest? = null
    private var pendingWebResources: Array<String>? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private var permissionsDialog: AlertDialog? = null
    private var permissionConfirmDialog: AlertDialog? = null
    private var pendingPermissionKey: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val hostReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent ?: return
            val state = event.getStringExtra(HostKeepAliveService.EXTRA_STATE)
            if (HostKeepAliveService.STATE_READY == state) {
                val url = event.getStringExtra(HostKeepAliveService.EXTRA_URL)
                if (url == null || url == loadedHostUrl) return
                loadedHostUrl = url
                status.visibility = View.GONE
                actionRow.visibility = View.VISIBLE
                settingsButton.visibility = View.VISIBLE
                storageButton.visibility = View.VISIBLE
                permissionsButton.visibility = View.VISIBLE
                updateStorageLabel()
                importButton.visibility = View.GONE
                downloadButton.visibility = View.GONE
                providerScroll?.visibility = View.GONE
                webView?.visibility = View.VISIBLE
                val overlay = bootOverlay
                if (overlay != null) overlay.setMessage("Rootfs ready")
                webView?.loadUrl(url)
                maybeRequestNotificationPermission()
            } else if (HostKeepAliveService.STATE_ERROR == state
                || HostKeepAliveService.STATE_EXITED == state
            ) {
                showStatus(event.getStringExtra(HostKeepAliveService.EXTRA_MESSAGE), false)
                hideBootOverlay()
                if (!RootfsInstaller.isReady(rootfsDir)) showSetup()
            } else if (HostKeepAliveService.STATE_STARTING == state
                && webView?.url == null
            ) {
                showStatus("Starting local dsh Host…", false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configureWindowForLaunch()
        handleDebugDownload(intent)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.rgb(5, 7, 15))
        rootLayout = root

        status = TextView(this)
        status.setTextSize(12f)
        status.setTextColor(Color.WHITE)
        status.setBackgroundColor(Color.rgb(24, 24, 24))
        status.setPadding(20, 16, 20, 16)
        status.setText("Preparing dsh runtime…")
        root.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        actionRow = LinearLayout(this)
        actionRow.orientation = LinearLayout.HORIZONTAL
        actionRow.gravity = Gravity.CENTER_VERTICAL
        actionRow.setPadding(dp(8), dp(7), dp(8), dp(5))
        actionRow.setBackgroundColor(Color.rgb(7, 12, 22))
        actionRow.visibility = View.GONE

        settingsButton = TextView(this)
        settingsButton.setText("Provider")
        settingsButton.setTextSize(12f)
        settingsButton.setSingleLine(true)
        settingsButton.setTextColor(Color.rgb(219, 230, 247))
        settingsButton.setTextSize(12f)
        settingsButton.gravity = Gravity.CENTER
        settingsButton.setBackground(navBackground())
        settingsButton.setPadding(dp(8), dp(9), dp(8), dp(9))
        settingsButton.visibility = View.GONE
        settingsButton.setOnClickListener { showProviderSetup(true) }
        actionRow.addView(settingsButton, navParams(0.8f))

        storageButton = TextView(this)
        storageButton.setText("Workspace: Internal")
        storageButton.setTextSize(12f)
        storageButton.setSingleLine(true)
        storageButton.setTextColor(Color.rgb(219, 230, 247))
        storageButton.setTextSize(12f)
        storageButton.gravity = Gravity.CENTER
        storageButton.setBackground(navBackground())
        storageButton.setPadding(dp(8), dp(9), dp(8), dp(9))
        storageButton.visibility = View.GONE
        storageButton.setOnClickListener { showStorageDialog() }
        actionRow.addView(storageButton, navParams(1.2f))

        permissionsButton = TextView(this)
        permissionsButton.setText("Permissions")
        permissionsButton.setTextSize(12f)
        permissionsButton.setSingleLine(true)
        permissionsButton.setTextColor(Color.rgb(219, 230, 247))
        permissionsButton.setTextSize(12f)
        permissionsButton.gravity = Gravity.CENTER
        permissionsButton.setBackground(navBackground())
        permissionsButton.setPadding(dp(8), dp(9), dp(8), dp(9))
        permissionsButton.visibility = View.GONE
        permissionsButton.setOnClickListener { showPermissionsDialog() }
        actionRow.addView(permissionsButton, navParams(1f))

        root.addView(
            actionRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        importButton = Button(this)
        importButton.setText("Import rootfs (.tar.gz)")
        importButton.visibility = View.GONE
        importButton.setOnClickListener { pickRootfs() }
        root.addView(
            importButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        downloadButton = Button(this)
        downloadButton.setText("Download rootfs")
        downloadButton.visibility = View.GONE
        downloadButton.setOnClickListener { startDownload() }
        root.addView(
            downloadButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val web = WebView(this)
        web.setBackgroundColor(Color.rgb(5, 7, 15))
        WebView.setWebContentsDebuggingEnabled(true)
        val settings: WebSettings = web.settings
        settings.setJavaScriptEnabled(true)
        settings.setDomStorageEnabled(true)
        settings.setAllowFileAccess(false)
        settings.setAllowContentAccess(false)
        web.addJavascriptInterface(PdfBridge(this), "DshdPdf")
        web.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                if (!isHostPage(url)) return
                Log.i(TAG, "boot: first paint after " + bootElapsed() + " ms")
                armRevealPoll()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isHostPage(url)) return
                Log.i(TAG, "boot: page finished after " + bootElapsed() + " ms")
                armRevealPoll()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                val entry = message ?: return true
                Log.i(
                    TAG,
                    "console: " + entry.message() + " @" +
                        entry.sourceId() + ":" + entry.lineNumber()
                )
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val pending = request ?: return
                runOnUiThread { handleWebPermission(pending) }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (pendingWebRequest === request) {
                    pendingWebRequest = null
                    pendingWebResources = null
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val pending = callback ?: return
                runOnUiThread { handleGeolocationPrompt(origin, pending) }
            }

            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: WebChromeClient.FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                if (params == null) {
                    filePathCallback = null
                    return false
                }
                try {
                    val intent = params.createIntent()
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    startActivityForResult(
                        Intent.createChooser(intent, "选择文件"),
                        REQUEST_FILE_CHOOSER
                    )
                    return true
                } catch (error: Exception) {
                    Log.e(TAG, "file chooser failed", error)
                    filePathCallback = null
                    return false
                }
            }
        }
        webView = web
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val container = FrameLayout(this)
        container.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val overlay = BootOverlay(this)
        bootOverlay = overlay
        bootShownAt = SystemClock.uptimeMillis()
        container.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(container)

        rootfsDir = File(filesDir, ROOTFS_NAME)
        handlePermissionIntent(intent)
        val filter = IntentFilter(HostKeepAliveService.EVENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(hostReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(hostReceiver, filter)
        }
        receiverRegistered = true
        val debuggable = (applicationInfo.flags
            and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val debugArchive = if (debuggable) intent.getStringExtra("rootfsArchive") else null
        val debugUrl = if (debuggable) intent.getStringExtra("rootfsUrl") else null
        val debugSha = if (debuggable) intent.getStringExtra("rootfsSha256") else null
        if (debugUrl != null) {
            intent.removeExtra("rootfsUrl")
            downloadAndInstall(debugUrl, debugSha ?: "")
        } else if (debugArchive != null) {
            intent.removeExtra("rootfsArchive")
            installRootfsFromFile(File(debugArchive))
        } else if (!RootfsInstaller.isReady(rootfsDir)) {
            if (BuildConfig.ROOTFS_EMBEDDED) {
                installBundledRootfs(false)
            } else {
                showSetup()
            }
        } else if (BuildConfig.ROOTFS_EMBEDDED
            && BuildConfig.ROOTFS_VERSION != readRootfsVersion()
        ) {
            // An APK update can carry a newer rootfs. Preserve user data and replace
            // the old tree before starting the Host.
            installBundledRootfs(true)
        } else {
            // Starting a foreground service from onCreate races the activity's
            // foreground transition on Android 12 and throws
            // BackgroundServiceStartNotAllowedException. onResume() starts it once
            // the activity is actually in front.
            if (ProviderConfig.isConfigured(rootfsDir, this)) {
                showStatus("Starting local dsh Host…", false)
            } else {
                showProviderSetup(false)
            }
        }
    }

    private fun handleWebPermission(request: PermissionRequest) {
        Log.i(TAG, "web permission request: " + request.resources.contentToString())
        val resources = request.resources.toList()
        val wantsAudio = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val wantsVideo = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        if (!wantsAudio && !wantsVideo) {
            request.deny()
            return
        }
        val needed = ArrayList<String>()
        if (wantsAudio) needed.add(Manifest.permission.RECORD_AUDIO)
        if (wantsVideo) needed.add(Manifest.permission.CAMERA)
        if (hasAll(needed)) {
            grantWebResources(request, wantsAudio, wantsVideo)
            return
        }
        pendingWebRequest?.deny()
        pendingWebRequest = request
        val required = needed.toTypedArray()
        pendingWebResources = required
        requestPermissions(required, REQUEST_WEB_PERMISSIONS)
    }

    private fun grantWebResources(request: PermissionRequest, audio: Boolean, video: Boolean) {
        val grant = ArrayList<String>()
        if (audio) grant.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        if (video) grant.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        request.grant(grant.toTypedArray())
    }

    private fun handleGeolocationPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback
    ) {
        if (DevicePermissions.locationGranted(this)) {
            callback.invoke(origin, true, false)
            return
        }
        pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
        pendingGeoCallback = callback
        pendingGeoOrigin = origin
        requestPermissions(
            DevicePermissions.required(this, DevicePermissions.LOCATION),
            REQUEST_GEOLOCATION
        )
    }

    private fun hasAll(permissions: List<String>): Boolean {
        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WEB_PERMISSIONS) {
            val request = pendingWebRequest
            val needed = pendingWebResources
            pendingWebRequest = null
            pendingWebResources = null
            if (request == null) return
            val granted = needed != null && hasAll(needed.toList())
            if (granted) {
                val resources = request.resources.toList()
                grantWebResources(
                    request,
                    resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
                    resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                )
            } else {
                request.deny()
            }
            return
        }
        if (requestCode == REQUEST_GEOLOCATION) {
            val callback = pendingGeoCallback
            val origin = pendingGeoOrigin
            pendingGeoCallback = null
            pendingGeoOrigin = null
            if (callback == null) return
            callback.invoke(origin, DevicePermissions.locationGranted(this), false)
            return
        }
        if (requestCode == REQUEST_DEVICE_PERMISSIONS) {
            val key = pendingPermissionKey
            pendingPermissionKey = null
            if (key != null
                && !DevicePermissions.isGranted(this, key)
                && !shouldShowRequestPermissionRationaleFor(key)
            ) {
                showPermissionSettingsHint(key)
            }
            refreshPermissionsDialog()
            return
        }
        if (requestCode == REQUEST_NOTIFICATIONS) return
    }

    private fun shouldShowRequestPermissionRationaleFor(key: String): Boolean {
        for (permission in DevicePermissions.required(this, key)) {
            if (shouldShowRequestPermissionRationale(permission)) return true
        }
        return false
    }

    private fun requestDevicePermission(key: String) {
        if (DevicePermissions.isGranted(this, key)) {
            Toast.makeText(
                this, DevicePermissions.label(key) + " is already granted.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (DevicePermissions.isHighRisk(key)) {
            confirmHighRiskPermission(key)
            return
        }
        performDevicePermissionRequest(key)
    }

    private fun performDevicePermissionRequest(key: String) {
        val required = DevicePermissions.required(this, key)
        if (required.isEmpty()) return
        pendingPermissionKey = key
        requestPermissions(required, REQUEST_DEVICE_PERMISSIONS)
    }

    /**
     * A second, app-level confirmation for sensitive permissions. Android's own
     * dialog follows only after the user explicitly continues here.
     */
    private fun confirmHighRiskPermission(key: String) {
        if (permissionConfirmDialog != null) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("Confirm sensitive permission")
            .setMessage(DevicePermissions.highRiskMessage(key))
            .setPositiveButton("Continue") { _, _ ->
                Handler(Looper.getMainLooper())
                    .postDelayed({ performDevicePermissionRequest(key) }, 150L)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Permission was not requested.", Toast.LENGTH_SHORT).show()
            }
            .create()
        dialog.setOnDismissListener { permissionConfirmDialog = null }
        permissionConfirmDialog = dialog
        dialog.show()
    }

    private fun showPermissionSettingsHint(key: String) {
        AlertDialog.Builder(this)
            .setTitle(DevicePermissions.label(key))
            .setMessage(
                "Android did not grant this permission. If the dialog no longer appears, " +
                    "enable it in the system app settings."
            )
            .setPositiveButton("Open settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionsDialog() {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val pad = Math.round(18 * resources.displayMetrics.density)
        content.setPadding(pad, pad / 2, pad, 0)
        val intro = TextView(this)
        intro.setText(
            "Grant Android capabilities the agent is allowed to use. " +
                "Permissions can be revoked in system settings at any time."
        )
        intro.setTextSize(13f)
        content.addView(intro)
        for (key in DevicePermissions.KEYS) {
            val row = Button(this)
            row.setAllCaps(false)
            val granted = DevicePermissions.isGranted(this, key)
            row.setText(DevicePermissions.label(key) + if (granted) "  ✓ Granted" else "  — Grant")
            row.isEnabled = !granted
            if (!granted) row.setOnClickListener { requestDevicePermission(key) }
            content.addView(row)
            val detail = DevicePermissions.detail(key)
            if (detail.isNotEmpty()) {
                val note = TextView(this)
                note.setText(detail)
                note.setTextSize(11f)
                note.setPadding(pad, 0, pad, pad / 4)
                content.addView(note)
            }
        }
        val settings = Button(this)
        settings.setAllCaps(false)
        settings.setText("Open Android app settings")
        settings.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
        content.addView(settings)
        val scroll = ScrollView(this)
        scroll.addView(content)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Android permissions")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener { permissionsDialog = null }
        permissionsDialog = dialog
        dialog.show()
    }

    private fun refreshPermissionsDialog() {
        val dialog = permissionsDialog
        if (dialog != null) {
            dialog.dismiss()
            showPermissionsDialog()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("dshd_permissions", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notification_asked", false)) return
        prefs.edit().putBoolean("notification_asked", true).apply()
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS
        )
    }

    private fun startHostOrSetup() {
        migrateProviderKey()
        if (!ProviderConfig.isConfigured(rootfsDir, this)) {
            showProviderSetup(false)
        } else {
            providerScroll?.visibility = View.GONE
            val overlay = bootOverlay
            if (overlay != null && !overlay.isDismissed()) {
                overlay.setMessage("Starting local dsh Host…")
            }
            webView?.visibility = View.VISIBLE
            startHostService()
        }
    }

    private fun startHostService() {
        sendHostAction(HostKeepAliveService.ACTION_START)
    }

    private fun sendHostAction(action: String) {
        val intent = Intent(this, HostKeepAliveService::class.java).setAction(action)
        try {
            // HostKeepAliveService is a foreground service. startService() is subject
            // to Android 12 background-start restrictions even from a just-resumed
            // activity; startForegroundService() is the supported entry point.
            startForegroundService(intent)
        } catch (error: Exception) {
            Log.e(TAG, "cannot start Host service", error)
            showStatus("Cannot start the local Host service: " + error, true)
        }
    }

    private fun handlePermissionIntent(intent: Intent?) {
        if (intent == null) return
        val permission = intent.getStringExtra(EXTRA_OPEN_PERMISSION) ?: return
        intent.removeExtra(EXTRA_OPEN_PERMISSION)
        Handler(Looper.getMainLooper()).postDelayed({
            if (permissionsDialog == null) showPermissionsDialog()
            if (DevicePermissions.KEYS.contains(permission)) {
                requestDevicePermission(permission)
            }
        }, 300L)
    }

    override fun onResume() {
        super.onResume()
        if (!rootfsInstalling && RootfsInstaller.isReady(rootfsDir)) startHostOrSetup()
    }

    private fun showProviderSetup(editing: Boolean) {
        providerEditing = editing
        hideBootOverlay()
        actionRow.visibility = View.VISIBLE
        settingsButton.visibility = View.GONE
        storageButton.visibility = View.VISIBLE
        permissionsButton.visibility = View.VISIBLE
        updateStorageLabel()
        importButton.visibility = View.GONE
        downloadButton.visibility = View.GONE
        webView?.visibility = View.GONE
        if (providerForm == null) {
            val form = LinearLayout(this)
            form.orientation = LinearLayout.VERTICAL
            form.setBackgroundColor(Color.rgb(246, 248, 252))
            form.setPadding(36, 32, 36, 32)
            providerBack = Button(this)
            providerBack.setAllCaps(false)
            providerBack.setText("← Back to chat")
            providerBack.setOnClickListener { hideProviderSetup() }
            providerBack.visibility = View.GONE
            form.addView(providerBack)
            val title = TextView(this)
            title.setText("Connect a model")
            title.setTextSize(22f)
            title.setTextColor(Color.rgb(24, 24, 24))
            form.addView(title)
            val hint = TextView(this)
            hint.setText(
                "Enter an OpenAI-compatible base URL and API key. " +
                    "These stay in the app-private rootfs."
            )
            hint.setTextSize(13f)
            hint.setPadding(0, 12, 0, 22)
            form.addView(hint)
            providerUrl = EditText(this)
            providerUrl.setHint("Base URL (https://…/v1)")
            providerUrl.setSingleLine(true)
            providerUrl.setInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            )
            form.addView(providerUrl)
            providerKey = EditText(this)
            providerKey.setHint("API key")
            providerKey.setSingleLine(true)
            providerKey.setInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            )
            providerKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO)
            form.addView(providerKey)
            providerModel = EditText(this)
            providerModel.setText(ProviderConfig.DEFAULT_MODEL)
            providerModel.setHint("Model id")
            providerModel.setSingleLine(true)
            form.addView(providerModel)
            val save = Button(this)
            save.setText("Save and start dsh")
            save.setOnClickListener { saveProvider() }
            form.addView(save)
            providerStatus = TextView(this)
            providerStatus.setTextSize(12f)
            providerStatus.setPadding(0, 12, 0, 0)
            form.addView(providerStatus)
            val scroll = ScrollView(this)
            scroll.setBackgroundColor(Color.rgb(246, 248, 252))
            scroll.addView(form)
            providerScroll = scroll
            rootLayout.addView(
                scroll,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            providerForm = form
        }
        if (editing) {
            if (providerUrl.text.toString().isEmpty()) {
                val savedUrl = ProviderConfig.readBaseUrl(rootfsDir)
                if (savedUrl.isNotEmpty()) providerUrl.setText(savedUrl)
            }
            if (providerModel.text.toString().trim().isEmpty()
                || providerModel.text.toString() == ProviderConfig.DEFAULT_MODEL
            ) {
                val savedModel = ProviderConfig.readModel(rootfsDir)
                if (savedModel.isNotEmpty()) providerModel.setText(savedModel)
            }
            providerKey.setHint("API key (blank = keep)")
            providerBack.visibility = View.VISIBLE
            status.setText("Provider settings")
            providerStatus.setText("")
        } else {
            providerKey.setHint("API key")
            providerBack.visibility = View.GONE
            status.setText("Configure the model provider for this device.")
        }
        status.visibility = View.VISIBLE
        providerScroll?.visibility = View.VISIBLE
        providerForm?.visibility = View.VISIBLE
    }

    private fun migrateProviderKey() {
        try {
            if (!ProviderSecretStore.has(this)) {
                val legacy = ProviderConfig.readStoredKey(rootfsDir)
                if (legacy.isNotEmpty()) {
                    ProviderSecretStore.save(this, legacy)
                    Log.i(TAG, "migrated provider key to Android Keystore")
                }
            }
            ProviderConfig.stripProviderKey(rootfsDir)
        } catch (error: Exception) {
            Log.e(TAG, "provider key migration failed", error)
        }
    }

    private fun hideProviderSetup() {
        providerScroll?.visibility = View.GONE
        providerBack.visibility = View.GONE
        webView?.visibility = View.VISIBLE
        actionRow.visibility = View.VISIBLE
        settingsButton.visibility = View.VISIBLE
        storageButton.visibility = View.VISIBLE
        permissionsButton.visibility = View.VISIBLE
        status.visibility = View.GONE
        if (loadedHostUrl == null && RootfsInstaller.isReady(rootfsDir)) startHostOrSetup()
    }

    override fun onBackPressed() {
        val scroll = providerScroll
        if (scroll != null && scroll.visibility == View.VISIBLE && providerEditing) {
            hideProviderSetup()
            return
        }
        super.onBackPressed()
    }

    private fun saveProvider() {
        val url = stripQuotes(providerUrl.text.toString().trim())
        val key = providerKey.text.toString().trim()
        val keepKey = providerEditing && key.isEmpty() && ProviderSecretStore.has(this)
        if (url.trim().isEmpty() || (key.isEmpty() && !keepKey)) {
            providerStatus.setText(
                if (providerEditing) {
                    "Base URL is required (leave API key blank to keep it)."
                } else {
                    "Base URL and API key are required."
                }
            )
            return
        }
        providerStatus.setText("Saving provider configuration…")
        Thread({
            try {
                if (keepKey) {
                    ProviderConfig.writeProfile(rootfsDir, url, providerModel.text.toString())
                } else {
                    ProviderConfig.write(rootfsDir, url, key, providerModel.text.toString())
                    ProviderSecretStore.save(this, key)
                }
                runOnUiThread {
                    providerStatus.setText("Saved.")
                    status.visibility = View.GONE
                    providerScroll?.visibility = View.GONE
                    webView?.visibility = View.VISIBLE
                    actionRow.visibility = View.VISIBLE
                    settingsButton.visibility = View.VISIBLE
                    storageButton.visibility = View.VISIBLE
                    permissionsButton.visibility = View.VISIBLE
                    updateStorageLabel()
                    if (loadedHostUrl != null) restartHostForStorage() else startHostService()
                }
            } catch (error: Exception) {
                Log.e(TAG, "provider config failed", error)
                runOnUiThread { providerStatus.setText("Save failed: " + error) }
            }
        }, "provider-config").start()
    }

    private fun updateStorageLabel() {
        storageButton.setText("Storage: " + StorageConfig.currentLabel(this))
    }

    private fun showStorageDialog() {
        val removable = StorageConfig.removableDir(this)
        val external = StorageConfig.externalDir(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val pad = Math.round(20 * resources.displayMetrics.density)
        content.setPadding(pad, pad / 2, pad, 0)

        val note = TextView(this)
        note.setText(
            "Applies to new Workspaces. Existing Workspaces are not moved. " +
                "The Linux rootfs and app data stay in internal storage because shared " +
                "storage is mounted noexec."
        )
        note.setTextSize(13f)
        content.addView(note)

        val mode = StorageConfig.mode(this)

        val internal = Button(this)
        internal.setAllCaps(false)
        internal.setText(
            "Internal app storage" +
                if (StorageConfig.MODE_INTERNAL == mode) "  ✓" else ""
        )
        content.addView(internal)

        val sd = Button(this)
        sd.setAllCaps(false)
        val externalLabel = if (removable != null) {
            "SD card: " + removable.absolutePath
        } else {
            "External app storage: " + if (external == null) "unavailable" else external.absolutePath
        }
        sd.setText(
            externalLabel + if (StorageConfig.MODE_EXTERNAL == mode) "  ✓" else ""
        )
        sd.isEnabled = removable != null || external != null
        content.addView(sd)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Workspace storage")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .create()
        internal.setOnClickListener {
            dialog.dismiss()
            selectStorage(StorageConfig.MODE_INTERNAL)
        }
        sd.setOnClickListener {
            dialog.dismiss()
            selectStorage(StorageConfig.MODE_EXTERNAL)
        }
        dialog.show()
    }

    private fun selectStorage(mode: String) {
        StorageConfig.setMode(this, mode)
        updateStorageLabel()
        val scroll = providerScroll
        val formVisible = scroll != null && scroll.visibility == View.VISIBLE
        if (ProviderConfig.isConfigured(rootfsDir, this) && !formVisible) {
            restartHostForStorage()
        } else if (::providerStatus.isInitialized) {
            providerStatus.setText("Workspace storage will be applied when dsh starts.")
        }
    }

    private fun restartHostForStorage() {
        status.visibility = View.VISIBLE
        status.setText("Restarting dsh Host on the selected workspace storage…")
        sendHostAction(HostKeepAliveService.ACTION_STOP)
        Handler(Looper.getMainLooper()).postDelayed(
            { sendHostAction(HostKeepAliveService.ACTION_START) }, 700L
        )
    }

    private fun rootfsUrl(): String {
        val url = getString(R.string.rootfs_download_url).trim()
        return if (url.isEmpty()) BuildConfig.ROOTFS_URL else url
    }

    private fun showSetup() {
        hideBootOverlay()
        actionRow.visibility = View.GONE
        showStatus(
            "dsh runtime is not installed yet.\n\n" +
                "Select a rootfs .tar.gz archive to install it once.\n" +
                "Expected files: lib/ld-linux-aarch64.so.1, opt/node24/bin/node, " +
                "opt/node24/lib/node_modules/@deepseek-ai/dsh/lib/bin.js", false
        )
        importButton.visibility = View.VISIBLE
        val url = rootfsUrl()
        downloadButton.visibility = if (url.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun pickRootfs() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        startActivityForResult(intent, REQUEST_ROOTFS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_CHOOSER) {
            val callback = filePathCallback ?: return
            var results: Array<Uri>? = null
            if (resultCode == Activity.RESULT_OK && data != null) {
                val clip = data.clipData
                if (clip != null) {
                    results = Array(clip.itemCount) { clip.getItemAt(it).uri }
                } else {
                    val single = data.data
                    if (single != null) results = arrayOf(single)
                }
            }
            callback.onReceiveValue(results)
            filePathCallback = null
            return
        }
        if (requestCode != REQUEST_ROOTFS || resultCode != Activity.RESULT_OK || data == null) {
            return
        }
        val uri = data.data ?: return
        importButton.visibility = View.GONE
        Thread({
            try {
                val input: InputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("cannot open $uri")
                input.use { stream ->
                    RootfsInstaller.install(stream, rootfsDir, -1L) { message, _ ->
                        runOnUiThread { showStatus(message, false) }
                    }
                }
                writeRootfsVersion("external")
                runOnUiThread { startHostOrSetup() }
            } catch (error: Exception) {
                Log.e(TAG, "rootfs import failed", error)
                runOnUiThread {
                    showStatus("Rootfs import failed: $error\n\nSelect another archive.", false)
                    importButton.visibility = View.VISIBLE
                }
            }
        }, "rootfs-import").start()
    }

    private fun startDownload() {
        var sha = getString(R.string.rootfs_sha256).trim()
        if (sha.isEmpty()) sha = BuildConfig.ROOTFS_SHA256
        val url = rootfsUrl()
        if (url.isEmpty()) {
            showStatus("No rootfs download URL is configured in this build.", false)
            return
        }
        downloadAndInstall(url, sha)
    }

    private fun downloadAndInstall(url: String, sha256: String) {
        importButton.visibility = View.GONE
        downloadButton.visibility = View.GONE
        Thread({
            val archive = File(cacheDir, "rootfs-download.tar.gz")
            try {
                RootfsInstaller.download(url, sha256, archive) { message, _ ->
                    runOnUiThread { showStatus(message, false) }
                }
                FileInputStream(archive).use { input ->
                    RootfsInstaller.install(input, rootfsDir, archive.length()) { message, _ ->
                        runOnUiThread { showStatus(message, false) }
                    }
                }
                writeRootfsVersion("external")
                archive.delete()
                runOnUiThread { startHostOrSetup() }
            } catch (error: Exception) {
                Log.e(TAG, "rootfs download failed", error)
                archive.delete()
                runOnUiThread {
                    showStatus(
                        "Rootfs download failed: $error" +
                            "\n\nCheck the URL/network or select a local archive.", false
                    )
                    showSetup()
                }
            }
        }, "rootfs-download").start()
    }

    private fun installRootfsFromFile(archive: File) {
        if (!archive.isFile) {
            showStatus("Rootfs archive not found: " + archive, false)
            showSetup()
            return
        }
        importButton.visibility = View.GONE
        Thread({
            try {
                FileInputStream(archive).use { input ->
                    RootfsInstaller.install(input, rootfsDir, archive.length()) { message, _ ->
                        runOnUiThread { showStatus(message, false) }
                    }
                }
                writeRootfsVersion("external")
                runOnUiThread { startHostOrSetup() }
            } catch (error: Exception) {
                Log.e(TAG, "rootfs import failed", error)
                runOnUiThread {
                    showStatus("Rootfs import failed: " + error, false)
                    importButton.visibility = View.VISIBLE
                }
            }
        }, "rootfs-import").start()
    }

    /**
     * Import the rootfs packaged in this APK. On an APK update, preserve the
     * user-facing home data before replacing the tree.
     */
    private fun installBundledRootfs(migrate: Boolean) {
        if (rootfsInstalling) return
        rootfsInstalling = true
        importButton.visibility = View.GONE
        downloadButton.visibility = View.GONE
        showStatus(if (migrate) "Updating bundled rootfs…" else "Installing bundled rootfs…", false)
        Thread({
            var preserve: File? = null
            try {
                if (migrate || File(rootfsDir, "home").isDirectory) {
                    preserve = preserveUserData()
                }
                val totalBytes = bundledRootfsLength()
                assets.open("rootfs.tar.xz").use { input ->
                    RootfsInstaller.install(input, rootfsDir, totalBytes) { message, _ ->
                        runOnUiThread { showStatus(message, false) }
                    }
                }
                val backup = preserve
                if (backup != null) restoreUserData(backup)
                writeRootfsVersion(BuildConfig.ROOTFS_VERSION)
                RootfsInstaller.deleteTree(backup)
                runOnUiThread {
                    rootfsInstalling = false
                    startHostOrSetup()
                }
            } catch (error: Exception) {
                Log.e(TAG, "bundled rootfs install failed", error)
                rootfsInstalling = false
                val finalPreserve = preserve
                runOnUiThread {
                    val backup = if (finalPreserve != null && finalPreserve.exists()) {
                        "\n\nUser data backup kept at " + finalPreserve.absolutePath
                    } else {
                        ""
                    }
                    showStatus("Bundled rootfs install failed: " + error + backup, false)
                    showSetup()
                }
            }
        }, "bundled-rootfs").start()
    }

    private fun preserveUserData(): File {
        val preserve = File(
            filesDir,
            "rootfs-userdata." + System.currentTimeMillis()
        )
        if (!preserve.isDirectory && !preserve.mkdirs()) {
            throw IOException("cannot create $preserve")
        }
        val home = File(rootfsDir, "home")
        for (name in PRESERVED_HOME) {
            val source = File(home, name)
            if (!source.exists()) continue
            RootfsInstaller.copyTree(source, File(preserve, name))
        }
        return preserve
    }

    private fun restoreUserData(preserve: File) {
        val home = File(rootfsDir, "home")
        for (name in PRESERVED_HOME) {
            val source = File(preserve, name)
            if (!source.exists()) continue
            val target = File(home, name)
            RootfsInstaller.deleteTree(target)
            RootfsInstaller.copyTree(source, target)
        }
        // The old bridge token/port belongs to the previous Host process.
        File(home, ".dsh/android-bridge.json").delete()
    }

    private fun readRootfsVersion(): String {
        val file = File(filesDir, ROOTFS_VERSION_FILE)
        if (!file.isFile) return ""
        try {
            FileInputStream(file).use { input ->
                val data = ByteArray(minOf(file.length(), 4096L).toInt())
                val read = input.read(data)
                return if (read <= 0) {
                    ""
                } else {
                    String(data, 0, read, StandardCharsets.UTF_8).trim()
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "cannot read rootfs version", error)
            return ""
        }
    }

    private fun writeRootfsVersion(version: String) {
        val file = File(filesDir, ROOTFS_VERSION_FILE)
        FileOutputStream(file).use { output ->
            output.write(version.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun showStatus(message: String?, retryable: Boolean) {
        runOnUiThread {
            status.setText(message)
            status.visibility = View.VISIBLE
            val overlay = bootOverlay
            if (overlay != null && !overlay.isDismissed()) {
                overlay.setMessage(message)
            }
            if (retryable) {
                status.setOnClickListener {
                    status.setOnClickListener(null)
                    startHostService()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugDownload(intent)
        handlePermissionIntent(intent)
    }

    /** Debug-only hook to exercise the resumable downloader without replacing the rootfs. */
    private fun handleDebugDownload(intent: Intent?) {
        if (intent == null) return
        val url = intent.getStringExtra("debugDownloadUrl")
        if (url.isNullOrEmpty()) return
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val rawName = intent.getStringExtra("debugDownloadName")
        val finalName = if (rawName.isNullOrEmpty()) "debug-download.bin" else rawName
        val finalSha = intent.getStringExtra("debugDownloadSha")
        val destination = File(cacheDir, finalName)
        Thread({
            try {
                RootfsInstaller.download(url, finalSha, destination) { message, _ ->
                    Log.i(TAG, "debug-download: " + message)
                }
                Log.i(
                    TAG,
                    "debug-download: complete " + destination.length() + " bytes " +
                        finalName
                )
            } catch (error: Exception) {
                Log.e(TAG, "debug-download failed", error)
            }
        }, "debug-download").start()
    }

    private fun configureWindowForLaunch() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.rgb(5, 7, 15)
        window.navigationBarColor = Color.rgb(5, 7, 15)
        var flags = window.decorView.systemUiVisibility
        flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        if (Build.VERSION.SDK_INT >= 26) {
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    private fun navBackground(): GradientDrawable {
        val background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF142238.toInt(), 0xFF0D1727.toInt())
        )
        background.cornerRadius = dp(10).toFloat()
        background.setStroke(dp(1), 0xFF263B5C.toInt())
        return background
    }

    private fun navParams(weight: Float): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, weight
        )
        params.setMargins(dp(3), 0, dp(3), 0)
        return params
    }

    private fun hideBootOverlay() {
        val overlay = bootOverlay
        if (overlay != null && !overlay.isDismissed()) overlay.dismiss()
    }

    private fun bundledRootfsLength(): Long {
        try {
            assets.openFd("rootfs.tar.xz").use { descriptor ->
                return descriptor.length
            }
        } catch (ignored: Exception) {
            try {
                assets.open("rootfs.tar.xz").use { input ->
                    return input.available().toLong()
                }
            } catch (error: Exception) {
                return -1L
            }
        }
    }

    private fun armRevealPoll() {
        val overlay = bootOverlay
        if (overlay == null || overlay.isDismissed() || revealPolling) return
        revealPolling = true
        pollReveal(SystemClock.uptimeMillis())
    }

    private fun pollReveal(startedAt: Long) {
        val overlay = bootOverlay
        if (overlay == null || overlay.isDismissed()) {
            revealPolling = false
            return
        }
        val script = "(function(){try{" +
            "if(document.querySelector('[data-composer-input]'))return true;" +
            "var t=document.body?document.body.innerText:'';" +
            "return document.readyState==='complete'&&t.length>80" +
            "&&!/Loading plugins/i.test(t)&&!/Loading history/i.test(t);" +
            "}catch(e){return false}})()"
        webView?.evaluateJavascript(script) { value ->
            val current = bootOverlay
            if (current == null || current.isDismissed()) {
                revealPolling = false
            } else {
                val now = SystemClock.uptimeMillis()
                val minWait = maxOf(0L, 1200L - (now - bootShownAt))
                val ready = "true" == value
                if ((ready && minWait == 0L) || now - startedAt >= 15000L) {
                    revealPolling = false
                    webView?.postDelayed({ finishBoot() }, minWait)
                } else {
                    webView?.postDelayed({ pollReveal(startedAt) }, 220L)
                }
            }
        }
    }

    private fun bootElapsed(): Long {
        return SystemClock.uptimeMillis() - bootShownAt
    }

    private fun isHostPage(url: String?): Boolean {
        val host = loadedHostUrl ?: return false
        return url != null && (url.startsWith(host) || host.startsWith(url))
    }

    private fun finishBoot() {
        val overlay = bootOverlay
        if (overlay != null && !overlay.isDismissed()) overlay.dismiss()
        actionRow.visibility = View.VISIBLE
        status.visibility = View.GONE
        Log.i(TAG, "boot: Web UI ready after " + bootElapsed() + " ms")
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(hostReceiver)
            receiverRegistered = false
        }
        val web = webView
        if (web != null) {
            web.loadUrl("about:blank")
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "dshd"
        private const val ROOTFS_NAME = "rootfs"
        private const val ROOTFS_VERSION_FILE = "rootfs.version"
        private val PRESERVED_HOME = arrayOf(
            ".dsh", "Documents", "workspace", ".ssh", ".gnupg", ".npmrc"
        )
        private const val REQUEST_FILE_CHOOSER = 44
        private const val REQUEST_ROOTFS = 41
        private const val REQUEST_WEB_PERMISSIONS = 42
        private const val REQUEST_DEVICE_PERMISSIONS = 45
        private const val REQUEST_GEOLOCATION = 46
        private const val REQUEST_NOTIFICATIONS = 47

        @JvmField
        val EXTRA_OPEN_PERMISSION = "openPermission"

        private fun stripQuotes(value: String): String {
            if (value.length >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))
            ) {
                return value.substring(1, value.length - 1)
            }
            return value
        }
    }
}
