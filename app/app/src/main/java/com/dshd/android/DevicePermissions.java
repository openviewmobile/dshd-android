package com.dshd.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Android runtime permissions exposed by the native shell and the agent bridge. */
final class DevicePermissions {
  static final String MICROPHONE = "microphone";
  static final String CAMERA = "camera";
  static final String LOCATION = "location";
  static final String NOTIFICATIONS = "notifications";
  static final String BLUETOOTH = "bluetooth";
  static final String PHONE = "phone";
  static final String CONTACTS = "contacts";

  static final String[] KEYS = {
      MICROPHONE, CAMERA, LOCATION, NOTIFICATIONS, BLUETOOTH, PHONE, CONTACTS
  };

  private DevicePermissions() {}

  static String label(String key) {
    switch (key) {
      case MICROPHONE: return "Microphone";
      case CAMERA: return "Camera";
      case LOCATION: return "Location";
      case NOTIFICATIONS: return "Notifications";
      case BLUETOOTH: return "Bluetooth devices";
      case PHONE: return "Phone calls";
      case CONTACTS: return "Contacts (read)";
      default: return key;
    }
  }

  /**
   * Permissions that expose the user or act outside the app. Asking for them
   * goes through an in-app confirmation before Android's own dialog.
   */
  static boolean isHighRisk(String key) {
    return PHONE.equals(key) || CONTACTS.equals(key)
        || LOCATION.equals(key) || BLUETOOTH.equals(key);
  }

  static String highRiskMessage(String key) {
    switch (key) {
      case PHONE:
        return "The agent will be able to place calls directly from this phone. "
            + "The number is dialed without another prompt. Android will ask for "
            + "the phone-call permission next.";
      case CONTACTS:
        return "The agent will be able to read names and phone numbers from your "
            + "contacts. Access is read-only, but it can search the whole address book. "
            + "Android will ask for the contacts permission next.";
      case LOCATION:
        return "The agent will be able to ask for your current location while the app "
            + "is in use. There is no background tracking. Android will ask for the "
            + "location permission next.";
      case BLUETOOTH:
        return "The agent will be able to scan for nearby Bluetooth devices. "
            + "Android will ask for the nearby-devices permission next.";
      default:
        return "";
    }
  }

  static String detail(String key) {
    switch (key) {
      case BLUETOOTH:
        return "Scan and list nearby BLE devices; Android 11 and older also need location.";
      case PHONE:
        return "The agent can place a call directly. The number is dialed immediately.";
      case CONTACTS:
        return "The agent can search names and phone numbers. Read-only.";
      case LOCATION:
        return "One-shot foreground location; precise if allowed, approximate accepted.";
      default:
        return "";
    }
  }

  static String[] required(Context context, String key) {
    switch (key) {
      case MICROPHONE:
        return new String[] {Manifest.permission.RECORD_AUDIO};
      case CAMERA:
        return new String[] {Manifest.permission.CAMERA};
      case LOCATION:
        return new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
      case NOTIFICATIONS:
        return Build.VERSION.SDK_INT >= 33
            ? new String[] {Manifest.permission.POST_NOTIFICATIONS}
            : new String[0];
      case BLUETOOTH: {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.BLUETOOTH);
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        if (Build.VERSION.SDK_INT >= 31) {
          permissions.add(Manifest.permission.BLUETOOTH_SCAN);
          permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
          permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return permissions.toArray(new String[0]);
      }
      case PHONE:
        return new String[] {Manifest.permission.CALL_PHONE};
      case CONTACTS:
        return new String[] {Manifest.permission.READ_CONTACTS};
      default:
        return new String[0];
    }
  }

  static boolean isGranted(Context context, String key) {
    switch (key) {
      case NOTIFICATIONS:
        return Build.VERSION.SDK_INT < 33
            || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
      case LOCATION:
        return locationGranted(context);
      case BLUETOOTH:
        if (Build.VERSION.SDK_INT >= 31) {
          return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                  == PackageManager.PERMISSION_GRANTED
              && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                  == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED
            && context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN)
                == PackageManager.PERMISSION_GRANTED
            && fineLocationGranted(context);
      default:
        for (String permission : required(context, key)) {
          if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            return false;
          }
        }
        return true;
    }
  }

  static boolean locationGranted(Context context) {
    return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
  }

  static boolean fineLocationGranted(Context context) {
    return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED;
  }

  /** Android 11 and older expose BLE scan results only with a fine-location grant. */
  static boolean bluetoothScanGranted(Context context) {
    if (Build.VERSION.SDK_INT >= 31) {
      return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
          == PackageManager.PERMISSION_GRANTED;
    }
    return fineLocationGranted(context);
  }

  static JSONArray snapshot(Context context) throws JSONException {
    JSONArray array = new JSONArray();
    for (String key : KEYS) {
      JSONObject row = new JSONObject();
      row.put("name", key);
      row.put("label", label(key));
      row.put("granted", isGranted(context, key));
      row.put("detail", detail(key));
      if (BLUETOOTH.equals(key)) {
        row.put("scanReady", bluetoothScanGranted(context));
      }
      array.put(row);
    }
    return array;
  }

  static Map<String, String> state(Context context) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String key : KEYS) result.put(key, isGranted(context, key) ? "granted" : "denied");
    return result;
  }
}
