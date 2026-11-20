package com.dshd.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePermissionsTest {
  @Test
  fun `high risk permissions require confirmation`() {
    assertTrue(DevicePermissions.isHighRisk(DevicePermissions.PHONE))
    assertTrue(DevicePermissions.isHighRisk(DevicePermissions.CONTACTS))
    assertTrue(DevicePermissions.isHighRisk(DevicePermissions.LOCATION))
    assertTrue(DevicePermissions.isHighRisk(DevicePermissions.BLUETOOTH))
  }

  @Test
  fun `ordinary permissions are not high risk`() {
    assertFalse(DevicePermissions.isHighRisk(DevicePermissions.MICROPHONE))
    assertFalse(DevicePermissions.isHighRisk(DevicePermissions.CAMERA))
    assertFalse(DevicePermissions.isHighRisk(DevicePermissions.NOTIFICATIONS))
    assertFalse(DevicePermissions.isHighRisk(null))
  }

  @Test
  fun `labels cover every exposed key`() {
    assertEquals("Microphone", DevicePermissions.label(DevicePermissions.MICROPHONE))
    assertEquals("Camera", DevicePermissions.label(DevicePermissions.CAMERA))
    assertEquals("Location", DevicePermissions.label(DevicePermissions.LOCATION))
    assertEquals("Notifications", DevicePermissions.label(DevicePermissions.NOTIFICATIONS))
    assertEquals("Bluetooth devices", DevicePermissions.label(DevicePermissions.BLUETOOTH))
    assertEquals("Phone calls", DevicePermissions.label(DevicePermissions.PHONE))
    assertEquals("Contacts (read)", DevicePermissions.label(DevicePermissions.CONTACTS))
  }
}
