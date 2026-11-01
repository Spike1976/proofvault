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
  const val CALL_START = "com.aintcloud.proofvault.CALL_START"
  const val CALL_STOP = "com.aintcloud.proofvault.CALL_STOP"
  @Volatile var callSession = false
  const val STOP = "com.aintcloud.proofvault.STOP"
  const val PAUSE = "com.aintcloud.proofvault.PAUSE"
  const val RESUME = "com.aintcloud.proofvault.RESUME"
  const val STATUS = "com.aintcloud.proofvault.STATUS"
  const val CHANNEL = "recording_status"
  @Volatile var recording = false
  @Volatile var paused = false
  @Volatile var lastError: String? = null
  @Volatile var lastSource: String = "None"
  @Volatile var liveAmplitude = 0
  @Volatile var silentSeconds = 0
  @Volatile var systemMuted = false
 }
 private lateinit var recorder: AudioRecorder
 private var file: File? = null
 private val monitor = Handler(Looper.getMainLooper())
 private val meter = object : Runnable {
  override fun run() {
   if (!recording) return
   if (!paused) {
    liveAmplitude = recorder.peakAmplitude()
    systemMuted = recorder.systemSilenced
    if (systemMuted) lastError = "Android is silencing ProofVault microphone capture during this session. Other call audio cannot be forced by an app permission."
    if (liveAmplitude < 50) silentSeconds += 2 else silentSeconds = 0
    if (silentSeconds >= 8) lastError = "No microphone signal for $silentSeconds seconds. Check audio routing."
   }
   sendBroadcast(Intent(STATUS).setPackage(packageName))
   monitor.postDelayed(this, 2000)
  }
 }
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
   START, CALL_START -> if (!recording) {
    try {
     startForeground(100, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
     lastError = null
     file = VaultRepository(this).newRecording()
     val chosenSource = if (intent.action == CALL_START) CapturePreferences(this).source() else CaptureSource.MICROPHONE
     lastSource = chosenSource.label
     recorder.start(file!!, chosenSource)
     recording = true; paused = false
     liveAmplitude = 0; silentSeconds = 0; systemMuted = false
     monitor.removeCallbacks(meter); monitor.postDelayed(meter, 2000)
     callSession = intent.action == CALL_START
    } catch (t: Throwable) {
     lastError = "Recording source $lastSource failed: ${t.message}. Check permission, phone audio routing, and other recording apps."
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
   STOP, CALL_STOP -> {
    if (intent.action == CALL_STOP && !callSession) return START_NOT_STICKY
    if (recording) {
     try {
      recorder.stop()
      file?.let { completed ->
       val check = RecordingQuality.inspect(completed)
       if (check.playable) EvidenceLedger(this).register(completed)
       else lastError = "Audio file could not be verified: ${check.message}"
      }
     } catch (e: Exception) {
      lastError = "Recording could not be finalized: ${e.message}"
     }
    }
    monitor.removeCallbacks(meter)
    recording = false; paused = false; callSession = false; file = null
    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
   }
  }
  sendBroadcast(Intent(STATUS).setPackage(packageName))
  return START_NOT_STICKY
 }
 override fun onDestroy() {
  monitor.removeCallbacks(meter)
  if (recording) {
   try {
    recorder.stop()
    file?.let { EvidenceLedger(this).register(it) }
   } catch (e: Exception) { lastError = "Interrupted recording: ${e.message}" }
   recording = false; paused = false; callSession = false
  }
  super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder? = null
}
