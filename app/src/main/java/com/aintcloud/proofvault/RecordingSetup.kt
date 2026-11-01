package com.aintcloud.proofvault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** Device-specific setup diagnostics. Does not promise protected remote-call audio. */
object RecordingSetup {
 data class Check(val title: String, val ok: Boolean, val detail: String)
 fun checks(context: Context): List<Check> {
  fun permitted(permission: String) =
   ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
  val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  val inputs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_INPUTS).map { device ->
   when (device.type) {
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in microphone"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset"
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
    else -> "Audio input type ${device.type}"
   }
  }.distinct().joinToString(", ") }.getOrDefault("Unable to identify audio inputs")
  val sources = CapturePreferences(context).source()
  return listOf(
   Check("Microphone", permitted(Manifest.permission.RECORD_AUDIO),
    "Needed for user-started recording; Android does not offer permanent always-on microphone authorization."),
   Check("Call detection", permitted(Manifest.permission.READ_PHONE_STATE),
    "Detects call state, but grants no access to the other participant's audio."),
   Check("Notifications", Build.VERSION.SDK_INT < 33 || permitted(Manifest.permission.POST_NOTIFICATIONS),
    "Needed to show recording status and call action on supported versions."),
   Check("Battery optimization", power.isIgnoringBatteryOptimizations(context.packageName),
    "Restricted battery policies may disrupt long recording sessions."),
   Check("Audio input", true, inputs),
   Check("Call source", true, sources.label + ". Device compatibility must be tested on a real call.")
  )
 }
}
