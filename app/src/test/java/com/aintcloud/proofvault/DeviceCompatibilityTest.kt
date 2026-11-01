package com.aintcloud.proofvault

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCompatibilityTest {
 @Test fun samsungProfile() {
  assertEquals(DeviceCompatibility.Family.SAMSUNG, DeviceCompatibility.detect("samsung", "samsung", "SM-S928U").family)
 }
 @Test fun pixelProfile() {
  assertEquals(DeviceCompatibility.Route.GOOGLE_PHONE, DeviceCompatibility.detect("Google", "google", "Pixel 10").route)
 }
 @Test fun redmiProfile() {
  assertEquals(DeviceCompatibility.Family.XIAOMI, DeviceCompatibility.detect("Xiaomi", "Redmi", "Note").family)
 }
 @Test fun onePlusProfile() {
  assertEquals(DeviceCompatibility.Family.ONEPLUS_OPPO, DeviceCompatibility.detect("OnePlus", "OnePlus", "13").family)
 }
 @Test fun motorolaProfile() {
  assertEquals(DeviceCompatibility.Family.MOTOROLA, DeviceCompatibility.detect("motorola", "motorola", "edge").family)
 }
 @Test fun fallbackProfile() {
  assertEquals(DeviceCompatibility.Family.GENERIC, DeviceCompatibility.detect("unknown", "unknown", "test").family)
 }
}
