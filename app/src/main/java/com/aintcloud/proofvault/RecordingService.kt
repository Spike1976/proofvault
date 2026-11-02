package com.aintcloud.proofvault

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.AudioManager
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
  @Volatile var captureReport: String = ""
 }
 private lateinit var recorder: AudioRecorder
 private var engine: CallCaptureEngine? = null
 private var file: File? = null
 private val monitor = Handler(Looper.getMainLooper())
 private val meter = object : Runnable {
  override fun run() {
   if (!recording) return
   if (!paused) {
    liveAmplitude = engine?.peak ?: recorder.peakAmplitude()
    systemMuted = engine?.systemSilenced ?: recorder.systemSilenced
    if (systemMuted) lastError = "Android is silencing this capture. Samples from a silenced session are not kept."
    if (liveAmplitude < 50) silentSeconds += 2 else silentSeconds = 0
    if (silentSeconds >= 8 && engine == null) lastError = "No microphone signal for $silentSeconds seconds. Check audio routing."
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
   .setContentTitle(when {
    paused -> "ProofVault recording paused"
    callSession -> "ProofVault call capture"
    else -> "ProofVault recording microphone"
   })
   .setContentText("Tap to return to ProofVault")
   .setContentIntent(open)
   .addAction(android.R.drawable.ic_media_pause, "Stop & save", stop)
   .setOngoing(true).build()
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  when (intent?.action) {
   START, CALL_START -> if (!recording) {
    val audioMode = (getSystemService(AUDIO_SERVICE) as AudioManager).mode
    val inCall = intent.action == CALL_START ||
     audioMode == AudioManager.MODE_IN_CALL || audioMode == AudioManager.MODE_IN_COMMUNICATION
    try {
     startForeground(100, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
     lastError = null
     liveAmplitude = 0; silentSeconds = 0; systemMuted = false
     if (inCall) startCallCapture() else startMicrophone()
    } catch (t: Throwable) {
     lastError = "Recording source $lastSource failed: ${t.message}. Check permission, phone audio routing, and other recording apps."
     file?.delete(); file = null; recording = false; paused = false; engine = null
     stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
   }
   PAUSE -> if (recording && !paused) {
    runCatching {
     engine?.setPaused(true) ?: recorder.pause()
     paused = true
     getSystemService(NotificationManager::class.java).notify(100, notification())
    }
   }
   RESUME -> if (recording && paused) {
    runCatching {
     engine?.setPaused(false) ?: recorder.resume()
     paused = false
     getSystemService(NotificationManager::class.java).notify(100, notification())
    }
   }
   STOP, CALL_STOP -> {
    if (intent.action == CALL_STOP && !callSession) return START_NOT_STICKY
    finishRecording()
    monitor.removeCallbacks(meter)
    recording = false; paused = false; callSession = false; file = null; engine = null
    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
   }
  }
  sendBroadcast(Intent(STATUS).setPackage(packageName))
  return START_NOT_STICKY
 }
 private fun startMicrophone() {
  file = VaultRepository(this).newRecording()
  val chosen = CaptureSource.MICROPHONE
  lastSource = chosen.label
  captureReport = ""
  recorder.start(file!!, chosen)
  recording = true
  paused = false
  callSession = false
  engine = null
  monitor.removeCallbacks(meter)
  monitor.postDelayed(meter, 2000)
 }
 private fun startCallCapture() {
  val placeholder = VaultRepository(this).newRecording()
  val wav = File(placeholder.parentFile, placeholder.nameWithoutExtension + ".wav")
  placeholder.delete()
  file = wav
  val capture = CallCaptureEngine(this)
  engine = capture
  recording = true
  paused = false
  callSession = true
  lastSource = "Probing call audio"
  captureReport = ""
  getSystemService(NotificationManager::class.java).notify(100, notification())
  monitor.removeCallbacks(meter)
  monitor.postDelayed(meter, 2000)
  capture.begin(wav) { report ->
   if (engine !== capture) return@begin
   captureReport = report.summary
   if (!report.accepted) {
    lastSource = "Blocked"
    lastError = report.summary
    systemMuted = true
    wav.delete()
    file = null
    recording = false
    paused = false
    callSession = false
    engine = null
    monitor.removeCallbacks(meter)
    sendBroadcast(Intent(STATUS).setPackage(packageName))
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
   } else {
    lastSource = report.sourceLabel
    lastError = null
    sendBroadcast(Intent(STATUS).setPackage(packageName))
   }
  }
 }
 private fun finishRecording() {
  if (!recording && engine == null) return
  val capture = engine
  val completed = file
  if (capture != null) {
   capture.stop()
   engine = null
   if (completed != null && completed.isFile && completed.length() > 44) {
    val check = RecordingQuality.inspect(completed)
    if (check.playable) EvidenceLedger(this).register(completed)
    else {
     lastError = "Audio file could not be verified: ${check.message}"
     completed.delete()
    }
   } else if (lastError == null) {
    lastError = captureReport.ifBlank { "No call audio was captured." }
   }
  } else if (recording) {
   try {
    recorder.stop()
    completed?.let { saved ->
     val check = RecordingQuality.inspect(saved)
     if (check.playable) EvidenceLedger(this).register(saved)
     else lastError = "Audio file could not be verified: ${check.message}"
    }
   } catch (e: Exception) {
    lastError = "Recording could not be finalized: ${e.message}"
   }
  }
 }
 override fun onDestroy() {
  monitor.removeCallbacks(meter)
  if (recording || engine != null) {
   try { finishRecording() } catch (e: Exception) { lastError = "Interrupted recording: ${e.message}" }
   recording = false; paused = false; callSession = false; engine = null
  }
  super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder? = null
}
