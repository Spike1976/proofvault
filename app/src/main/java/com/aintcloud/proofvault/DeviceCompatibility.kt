package com.aintcloud.proofvault

import android.os.Build

/**
 * Device-family routing is advisory. Never infer that a model alone
 * grants access to the cellular downlink or to another app's private files.
 */
object DeviceCompatibility {
 enum class Family { SAMSUNG, PIXEL, XIAOMI, ONEPLUS_OPPO, VIVO, MOTOROLA, GENERIC }
 enum class Route { OEM_OR_DIALER, GOOGLE_PHONE, DOCUMENT_PROVIDER, MANUAL_MICROPHONE }
 data class Profile(
  val family: Family,
  val label: String,
  val route: Route,
  val instructions: String,
  val caveat: String
 )
 fun detect(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND, model: String = Build.MODEL): Profile {
  val m = manufacturer.lowercase()
  val b = brand.lowercase()
  val modelName = model.lowercase()
  return when {
   m.contains("samsung") || b.contains("samsung") ->
    Profile(Family.SAMSUNG, "Samsung Galaxy", Route.OEM_OR_DIALER,
     "Check Samsung Phone > Settings > Record calls. Enable supported recording options, then share recordings to ProofVault or link an accessible recording folder.",
     "Native auto-record availability depends on firmware, market, and dialer. No direct protected call-audio access.")
   m.contains("google") || modelName.startsWith("pixel") ->
    Profile(Family.PIXEL, "Google Pixel", Route.GOOGLE_PHONE,
     "Open Phone by Google > Settings > Call recording. Where supported, configure Always record and share saved recordings to ProofVault.",
     "Pixel 6+ with Android 14+ is the documented baseline; carrier, region and app version affect features.")
   listOf("xiaomi", "redmi", "poco").any { m.contains(it) || b.contains(it) } ->
    Profile(Family.XIAOMI, "Xiaomi / Redmi / POCO", Route.OEM_OR_DIALER,
     "Check whether your dialer is Google Phone or the manufacturer's dialer. Enable supported native recording and share files or link an exposed recording folder.",
     "Recording availability varies with device model, country, carrier, and installed dialer.")
   listOf("oneplus", "oppo", "realme").any { m.contains(it) || b.contains(it) } ->
    Profile(Family.ONEPLUS_OPPO, "OnePlus / OPPO / Realme", Route.OEM_OR_DIALER,
     "Identify your default Phone app, check its call recording settings, and connect a user-accessible recordings folder or use Share to ProofVault.",
     "Some variants use Google Phone; other variants use manufacturer calling apps. No universal audio API.")
   listOf("vivo", "iqoo").any { m.contains(it) || b.contains(it) } ->
    Profile(Family.VIVO, "Vivo / iQOO", Route.OEM_OR_DIALER,
     "Check the default dialer's supported recording settings. Import recordings via the system share sheet or authorized file provider.",
     "Options vary by firmware and region; folder access must be explicitly granted.")
   m.contains("motorola") || b.contains("motorola") ->
    Profile(Family.MOTOROLA, "Motorola", Route.GOOGLE_PHONE,
     "If Phone by Google is installed, look under Phone settings for supported recording options. Import through Share.",
     "Not all Motorola models, carriers or countries expose Google Phone recording.")
   else ->
    Profile(Family.GENERIC, "Other Android device", Route.DOCUMENT_PROVIDER,
     "Check your device Phone app for native recording. Share existing recordings or link an accessible recording folder in ProofVault.",
     "No device support is assumed without testing. Microphone capture does not guarantee remote audio.")
  }
 }
}
