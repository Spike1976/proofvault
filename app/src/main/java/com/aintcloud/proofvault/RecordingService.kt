package com.aintcloud.proofvault

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.File

/** User-initiated microphone recording, with persistent visible notification. */
class RecordingService : Service() {
 companion object {
  const val START = "com.aintcloud.proofvault.START"
  const val STOP = "com.aintcloud.proofvault.STOP"
  const val PAUSE = "com.aintcloud.proofvault.PAUSE"
  const val RESUME = "com.aintcloud.proofvault.RESUME"
  const val STATUS = "com.aintcloud.proofvault.STATUS"
  const val CHANNEL = "recording_status"
  @Volatile var recording = false
  @Volatile var paused = false
 }
 private lateinit var recorder: AudioRecorder
 private var file: File? = null
 override fun onCreate() {
  super.onCreate()
  recorder = AudioRecorder(this)
  val manager = getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel(CHANNEL, "Active microphone recording", NotificationManager.IMPORTANCE_LOW))
 }
 private fun notification() : Notification {
  val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this, CHANNEL)
   .setSmallIcon(android.R.drawable.ic_btn_speak_now)
   .setContentTitle(if (paused) "ProofVault recording paused" else "ProofVault recording microphone")
   .setContentText("Tap to return to ProofVault")
   .setContentIntent(open)
   .addAction(android.R.drawable.ic_media_pause, "Stop & save", stop)
   .setOngoing(true).build()
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  when (intent?.action) {
   START -> if (!recording) {
    try {
     startForeground(100, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
     file = VaultRepository(this).newRecording()
     recorder.start(file!!, CapturePreferences(this).source())
     recording = true; paused = false
    } catch (t: Throwable) {
     file?.delete(); file = null; recording = false; paused = false
     stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
   }
   PAUSE -> if (recording && !paused) {
    runCatching { recorder.pause(); paused = true; getSystemService(NotificationManager::class.java).notify(100, notification()) }
   }
   RESUME -> if (recording && paused) {
    runCatching { recorder.resume(); paused = false; getSystemService(NotificationManager::class.java).notify(100, notification()) }
   }
   STOP -> {
    if (recording) {
     try { recorder.stop() } catch (_: Exception) { file?.delete() }
    }
    recording = false; paused = false; file = null
    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
   }
  }
  sendBroadcast(Intent(STATUS).setPackage(packageName))
  return START_NOT_STICKY
 }
 override fun onDestroy() {
  if (recording) {
   try { recorder.stop() } catch (_: Exception) { file?.delete() }
   recording = false; paused = false
  }
  super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder? = null
}
