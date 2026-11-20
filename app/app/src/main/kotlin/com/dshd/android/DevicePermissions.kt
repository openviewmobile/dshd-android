package com.dshd.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.ArrayList
import java.util.LinkedHashMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Android runtime permissions exposed by the native shell and the agent bridge. */
class DevicePermissions private constructor() {
  companion object {
    const val MICROPHONE = "microphone"
    const val CAMERA = "camera"
    const val LOCATION = "location"
    const val NOTIFICATIONS = "notifications"
    const val BLUETOOTH = "bluetooth"
    const val PHONE = "phone"
    const val CONTACTS = "contacts"

    val KEYS: Array<String> = arrayOf(
      MICROPHONE,
      CAMERA,
      LOCATION,
      NOTIFICATIONS,
      BLUETOOTH,
      PHONE,
      CONTACTS
    )

    fun label(key: String): String {
      return when (key) {
        MICROPHONE -> "Microphone"
        CAMERA -> "Camera"
        LOCATION -> "Location"
        NOTIFICATIONS -> "Notifications"
        BLUETOOTH -> "Bluetooth devices"
        PHONE -> "Phone calls"
        CONTACTS -> "Contacts (read)"
        else -> key
      }
    }

    /**
     * Permissions that expose the user or act outside the app. Asking for them
     * goes through an in-app confirmation before Android's own dialog.
     */
    fun isHighRisk(key: String?): Boolean {
      return PHONE == key || CONTACTS == key || LOCATION == key || BLUETOOTH == key
    }

    fun highRiskMessage(key: String): String {
      return when (key) {
        PHONE ->
          "The agent will be able to place calls directly from this phone. " +
            "The number is dialed without another prompt. Android will ask for " +
            "the phone-call permission next."
        CONTACTS ->
          "The agent will be able to read names and phone numbers from your " +
            "contacts. Access is read-only, but it can search the whole address book. " +
            "Android will ask for the contacts permission next."
        LOCATION ->
          "The agent will be able to ask for your current location while the app " +
            "is in use. There is no background tracking. Android will ask for the " +
            "location permission next."
        BLUETOOTH ->
          "The agent will be able to scan for nearby Bluetooth devices. " +
            "Android will ask for the nearby-devices permission next."
        else -> ""
      }
    }

    fun detail(key: String): String {
      return when (key) {
        BLUETOOTH ->
          "Scan and list nearby BLE devices; Android 11 and older also need location."
        PHONE ->
          "The agent can place a call directly. The number is dialed immediately."
        CONTACTS ->
          "The agent can search names and phone numbers. Read-only."
        LOCATION ->
          "One-shot foreground location; precise if allowed, approximate accepted."
        else -> ""
      }
    }

    fun required(context: Context?, key: String): Array<String> {
      return when (key) {
        MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
        CAMERA -> arrayOf(Manifest.permission.CAMERA)
        LOCATION -> arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION
        )
        NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) {
          arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          emptyArray()
        }
        BLUETOOTH -> {
          val permissions = ArrayList<String>()
          permissions.add(Manifest.permission.BLUETOOTH)
          permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
          if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
          } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
          }
          permissions.toTypedArray()
        }
        PHONE -> arrayOf(Manifest.permission.CALL_PHONE)
        CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
        else -> emptyArray()
      }
    }

    fun isGranted(context: Context, key: String): Boolean {
      return when (key) {
        NOTIFICATIONS ->
          Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        LOCATION -> locationGranted(context)
        BLUETOOTH -> {
          if (Build.VERSION.SDK_INT >= 31) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
              PackageManager.PERMISSION_GRANTED &&
              context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
              PackageManager.PERMISSION_GRANTED
          } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) ==
              PackageManager.PERMISSION_GRANTED &&
              context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) ==
              PackageManager.PERMISSION_GRANTED &&
              fineLocationGranted(context)
          }
        }
        else -> {
          for (permission in required(context, key)) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
              return false
            }
          }
          true
        }
      }
    }

    fun locationGranted(context: Context): Boolean {
      return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    }

    fun fineLocationGranted(context: Context): Boolean {
      return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    }

    /** Android 11 and older expose BLE scan results only with a fine-location grant. */
    fun bluetoothScanGranted(context: Context): Boolean {
      if (Build.VERSION.SDK_INT >= 31) {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
          PackageManager.PERMISSION_GRANTED
      }
      return fineLocationGranted(context)
    }

    @Throws(JSONException::class)
    fun snapshot(context: Context): JSONArray {
      val array = JSONArray()
      for (key in KEYS) {
        val row = JSONObject()
        row.put("name", key)
        row.put("label", label(key))
        row.put("granted", isGranted(context, key))
        row.put("detail", detail(key))
        if (BLUETOOTH == key) {
          row.put("scanReady", bluetoothScanGranted(context))
        }
        array.put(row)
      }
      return array
    }

    fun state(context: Context): Map<String, String> {
      val result = LinkedHashMap<String, String>()
      for (key in KEYS) result.put(key, if (isGranted(context, key)) "granted" else "denied")
      return result
    }
  }
}
