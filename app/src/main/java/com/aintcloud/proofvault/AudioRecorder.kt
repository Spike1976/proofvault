package com.aintcloud.proofvault

import android.media.MediaRecorder
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.content.Context
import java.io.File

/** Records device microphone, not protected remote call audio. */
class AudioRecorder(private val context: Context) {
 private var recorder: MediaRecorder? = null
 private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 @Volatile var systemSilenced = false
  private set
 private val captureCallback = object : AudioManager.AudioRecordingCallback() {
  override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // A matching capture session, when visible, explicitly reports system-enforced silence.
    val sessions = configs.filter { it.clientAudioSource == activeSource }
    if (sessions.isNotEmpty()) systemSilenced = sessions.any { it.isClientSilenced }
   }
  }
 }
 private var activeSource = MediaRecorder.AudioSource.MIC
 fun start(file: File, source: CaptureSource = CaptureSource.MICROPHONE) {
  check(recorder == null) { "Recording already started" }
  @Suppress("DEPRECATION")
  val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
  try {
   activeSource = source.id
   systemSilenced = false
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    audioManager.registerAudioRecordingCallback(captureCallback, android.os.Handler(android.os.Looper.getMainLooper()))
   }
   r.setAudioSource(source.id)
   r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
   r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
   r.setAudioEncodingBitRate(128000)
   r.setAudioSamplingRate(44100)
   r.setOutputFile(file.absolutePath)
   r.prepare()
   r.start()
   recorder = r
  } catch (t: Throwable) {
   runCatching { audioManager.unregisterAudioRecordingCallback(captureCallback) }
   r.release(); file.delete(); throw t
  }
 }
 fun pause() { recorder?.pause() ?: error("No active recording") }
 fun resume() { recorder?.resume() ?: error("No active recording") }
 fun stop() {
  val r = recorder ?: return
  recorder = null
  try { r.stop() } finally {
   runCatching { audioManager.unregisterAudioRecordingCallback(captureCallback) }
   r.reset(); r.release()
  }
 }
 /** Returns a nonnegative peak amplitude when MediaRecorder can sample the microphone. */
 fun peakAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
 fun isRecording() = recorder != null
}
