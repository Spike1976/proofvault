package com.aintcloud.proofvault

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.*
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.RandomAccessFile

/**
 * Records only playback audio that apps allow Android to capture.
 * Requires explicit MediaProjection consent on each session.
 * Cellular/VoIP call audio may not be exposed by the platform.
 */
class PlaybackCaptureService : Service() {
 companion object {
  const val START = "com.aintcloud.proofvault.PLAYBACK_START"
  const val STOP = "com.aintcloud.proofvault.PLAYBACK_STOP"
  const val EXTRA_RESULT = "projection_result"
  const val EXTRA_DATA = "projection_data"
  @Volatile var active = false
 }
 private var capture: AudioRecord? = null
 private var projection: MediaProjection? = null
 private var worker: Thread? = null
 private var output: File? = null
 private val sampleRate = 44100
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onCreate() {
  super.onCreate()
  getSystemService(NotificationManager::class.java).createNotificationChannel(
   NotificationChannel("playback_capture", "Playback audio recording", NotificationManager.IMPORTANCE_LOW))
 }
 private fun notice(): Notification {
  val stop = PendingIntent.getService(this, 32,
   Intent(this, PlaybackCaptureService::class.java).setAction(STOP),
   PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this, "playback_capture")
   .setSmallIcon(android.R.drawable.ic_btn_speak_now)
   .setContentTitle("ProofVault capturing permitted app audio")
   .setContentText("Only audio Android permits apps to share. Tap Stop to save.")
   .addAction(android.R.drawable.ic_media_pause, "Stop and save", stop)
   .setOngoing(true).build()
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if (intent?.action == STOP) {
   finishCapture()
   stopSelf()
   return START_NOT_STICKY
  }
  if (intent?.action != START || active) return START_NOT_STICKY
  try {
   startForeground(220, notice(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
   val data = if (Build.VERSION.SDK_INT >= 33)
    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
   else @Suppress("DEPRECATION") (intent.getParcelableExtra(EXTRA_DATA) as? Intent)
   requireNotNull(data) { "Projection consent missing" }
   val resultCode = intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED)
   require(resultCode == Activity.RESULT_OK)
   val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   projection = manager.getMediaProjection(resultCode, data)
   val currentProjection = requireNotNull(projection)
   currentProjection.registerCallback(object: MediaProjection.Callback() {
    override fun onStop() { finishCapture(); stopSelf() }
   }, Handler(Looper.getMainLooper()))
   val config = AudioPlaybackCaptureConfiguration.Builder(currentProjection)
    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
    .addMatchingUsage(AudioAttributes.USAGE_GAME)
    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
    .build()
   val min = AudioRecord.getMinBufferSize(sampleRate,
    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
   require(min > 0)
   val audio = AudioRecord.Builder()
    .setAudioPlaybackCaptureConfig(config)
    .setAudioFormat(AudioFormat.Builder()
     .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
     .setSampleRate(sampleRate)
     .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
    .setBufferSizeInBytes(maxOf(min * 2, 16384))
    .build()
   require(audio.state == AudioRecord.STATE_INITIALIZED)
   capture = audio
   output = VaultRepository(this).newRecording().let { File(it.parentFile, it.nameWithoutExtension + ".wav") }
   active = true
   audio.startRecording()
   val target = requireNotNull(output)
   worker = Thread {
    try {
     RandomAccessFile(target, "rw").use { file ->
      writeHeader(file, 0)
      val buffer = ByteArray(8192)
      var length = 0L
      while (active) {
       val count = audio.read(buffer, 0, buffer.size)
       if (count > 0) { file.write(buffer, 0, count); length += count }
       else if (count < 0) break
      }
      writeHeader(file, length)
     }
     if (target.length() > 44) EvidenceLedger(this).register(target) else target.delete()
    } catch (_: Exception) { target.delete() }
   }.also { it.start() }
  } catch (_: Exception) {
   finishCapture()
   stopSelf()
  }
  return START_NOT_STICKY
 }
 private fun writeHeader(file: RandomAccessFile, size: Long) {
  file.seek(0)
  fun word(n: Long) { repeat(4) { file.write(((n ushr (8 * it)) and 255).toInt()) } }
  fun half(n: Int) { file.write(n and 255); file.write((n ushr 8) and 255) }
  file.writeBytes("RIFF"); word(size + 36); file.writeBytes("WAVEfmt ")
  word(16); half(1); half(1); word(sampleRate.toLong())
  word((sampleRate * 2).toLong()); half(2); half(16)
  file.writeBytes("data"); word(size)
 }
 @Synchronized private fun finishCapture() {
  active = false
  val audio = capture
  capture = null
  runCatching { audio?.stop() }
  runCatching { audio?.release() }
  val p = projection
  projection = null
  runCatching { p?.stop() }
 }
 override fun onDestroy() { finishCapture(); super.onDestroy() }
}
