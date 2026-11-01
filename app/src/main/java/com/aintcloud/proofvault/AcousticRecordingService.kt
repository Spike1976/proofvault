package com.aintcloud.proofvault

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Opt-in acoustic recording of device microphone including audible speakerphone sound.
 * Does not bypass Android call capture restrictions or force speakerphone routing.
 */
class AcousticRecordingService : Service() {
 companion object {
  const val START = "com.aintcloud.proofvault.ACOUSTIC_START"
  const val STOP = "com.aintcloud.proofvault.ACOUSTIC_STOP"
  const val STATUS = "com.aintcloud.proofvault.ACOUSTIC_STATUS"
  @Volatile var active = false
  @Volatile var peak = 0
  @Volatile var silentSeconds = 0
  @Volatile var lastResult: String? = null
 }
 private val running = AtomicBoolean(false)
 private var input: AudioRecord? = null
 private var worker: Thread? = null
 private val sampleRate = 16000
 private val channel = "acoustic_capture"
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onCreate() {
  super.onCreate()
  getSystemService(NotificationManager::class.java).createNotificationChannel(
   NotificationChannel(channel, "Acoustic recording", NotificationManager.IMPORTANCE_LOW))
 }
 private fun notification(): Notification {
  val stop = PendingIntent.getService(this, 29, Intent(this, AcousticRecordingService::class.java).setAction(STOP),
   PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this,channel)
   .setSmallIcon(android.R.drawable.ic_btn_speak_now)
   .setContentTitle("ProofVault acoustic microphone recording")
   .setContentText("Recording only sound actually received by microphone")
   .addAction(android.R.drawable.ic_media_pause,"Stop and save",stop)
   .setOngoing(true).build()
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if (intent?.action == STOP) { stopCapture(); stopSelf(); return START_NOT_STICKY }
  if (intent?.action != START || active) return START_NOT_STICKY
  if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
   lastResult = "Microphone permission missing"
   stopSelf(); return START_NOT_STICKY
  }
  try {
   startForeground(329,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
   val minimum = AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
   require(minimum > 0)
   val preferredSource = if (Build.VERSION.SDK_INT >= 24) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
   val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
    .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
   fun open(source: Int): AudioRecord = AudioRecord.Builder().setAudioSource(source)
    .setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum * 2, 8192)).build()
   var recorder = open(preferredSource)
   if (recorder.state != AudioRecord.STATE_INITIALIZED) {
    recorder.release()
    recorder = open(MediaRecorder.AudioSource.MIC)
   }
   require(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
   input = recorder
   recorder.startRecording()
   require(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
   running.set(true); active = true; peak = 0; silentSeconds = 0; lastResult = null
   val output = VaultRepository(this).newRecording().let { File(it.parentFile,it.nameWithoutExtension+".wav") }
   worker = Thread {
    var countBytes = 0L
    var totalSamples = 0L
    var silentBytes = 0L
    var nonSilent = 0L
    try {
     RandomAccessFile(output,"rw").use { file ->
      wavHeader(file,0)
      val buffer = ByteArray(8192)
      while(running.get()) {
       val n = recorder.read(buffer,0,buffer.size)
       if(n < 0) throw IllegalStateException("Microphone read failure $n")
       if(n == 0) continue
       file.write(buffer,0,n); countBytes += n
       var localPeak = 0
       var i = 0
       while(i+1<n) {
        val sample = ((buffer[i+1].toInt() shl 8) or (buffer[i].toInt() and 255)).toShort().toInt()
        localPeak = maxOf(localPeak,abs(sample))
        if(abs(sample)>96) nonSilent++
        totalSamples++
        i+=2
       }
       peak = localPeak
       if(localPeak<96) silentBytes += n.toLong() else silentBytes = 0L
       silentSeconds = (silentBytes / (sampleRate * 2)).toInt()
      }
      wavHeader(file,countBytes)
     }
     if(countBytes>0L && totalSamples>0) {
      EvidenceLedger(this).register(output)
      lastResult = if(nonSilent==0L) "Recording saved but microphone received silence. Check call routing." else
       "Acoustic WAV saved. Audible parties still need verification."
     } else {
      output.delete()
      lastResult = "No microphone data received"
     }
    } catch(t: Throwable) {
     lastResult = "Acoustic recording failed: ${t.message}"
     // Retain partially written evidence for recovery when present.
    } finally {
     running.set(false); active = false
     runCatching { recorder.release() }
     sendBroadcast(Intent(STATUS).setPackage(packageName))
     stopSelf()
    }
   }.also { it.start() }
  } catch(t: Throwable) {
   lastResult = "Cannot start acoustic recorder: ${t.message}"
   stopCapture()
   stopSelf()
  }
  return START_NOT_STICKY
 }
 private fun wavHeader(file: RandomAccessFile, bytes: Long) {
  file.seek(0)
  fun u32(n: Long) { repeat(4) { file.write(((n ushr (it*8)) and 255L).toInt()) } }
  fun u16(n: Int) { file.write(n and 255); file.write((n ushr 8) and 255) }
  file.writeBytes("RIFF"); u32(bytes+36); file.writeBytes("WAVEfmt ")
  u32(16); u16(1); u16(1); u32(sampleRate.toLong()); u32((sampleRate*2).toLong())
  u16(2); u16(16); file.writeBytes("data"); u32(bytes)
 }
 private fun stopCapture() {
  running.set(false)
  runCatching { input?.stop() }
 }
 override fun onDestroy() {
  stopCapture()
  super.onDestroy()
 }
}
