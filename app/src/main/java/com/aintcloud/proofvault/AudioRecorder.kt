package com.aintcloud.proofvault

import android.media.MediaRecorder
import android.os.Build
import android.content.Context
import java.io.File

/** Records device microphone, not protected remote call audio. */
class AudioRecorder(private val context: Context) {
 private var recorder: MediaRecorder? = null
 fun start(file: File) {
  check(recorder == null) { "Recording already started" }
  @Suppress("DEPRECATION")
  val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
  try {
   r.setAudioSource(MediaRecorder.AudioSource.MIC)
   r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
   r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
   r.setAudioEncodingBitRate(128000)
   r.setAudioSamplingRate(44100)
   r.setOutputFile(file.absolutePath)
   r.prepare()
   r.start()
   recorder = r
  } catch (t: Throwable) { r.release(); file.delete(); throw t }
 }
 fun stop() {
  val r = recorder ?: return
  recorder = null
  try { r.stop() } finally { r.reset(); r.release() }
 }
 fun isRecording() = recorder != null
}
