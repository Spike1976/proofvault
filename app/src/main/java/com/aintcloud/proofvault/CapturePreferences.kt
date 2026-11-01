package com.aintcloud.proofvault

import android.content.Context
import android.media.MediaRecorder
import android.os.Build

/** Experimental sources are not guaranteed to include remote phone audio. */
enum class CaptureSource(val label: String, val id: Int) {
 MICROPHONE("Microphone (recommended)", MediaRecorder.AudioSource.MIC),
 VOICE_RECOGNITION("Voice recognition", MediaRecorder.AudioSource.VOICE_RECOGNITION),
 VOICE_COMMUNICATION("Voice communication", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
 VOICE_CALL("Voice call (may require privileged access)", MediaRecorder.AudioSource.VOICE_CALL),
 UNPROCESSED("Unprocessed (if available)", MediaRecorder.AudioSource.UNPROCESSED);

 companion object {
  fun fromName(name: String?): CaptureSource = entries.find { it.name == name } ?: MICROPHONE
 }
}

class CapturePreferences(context: Context) {
 private val pref = context.getSharedPreferences("capture_compatibility", Context.MODE_PRIVATE)
 fun source() = CaptureSource.fromName(pref.getString("source", null))
 fun select(source: CaptureSource) { pref.edit().putString("source", source.name).apply() }
 fun diagnostic(context: Context): String =
  "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); " +
  "Manufacturer ${Build.MANUFACTURER}; Model ${Build.MODEL}; " +
  "Selected ${source().label}. This does not prove remote-call capture."
}
