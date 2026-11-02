package com.aintcloud.proofvault

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-call PCM capture.
 *
 * Probes public audio sources and keeps the first session that is not
 * telephony-silenced and not digital-zero. Writes WAV, dropping only a leading
 * run of exact zeros. Does not use accessibility interception or privileged
 * capture permissions. If every source is silenced, no evidence file is kept.
 */
class CallCaptureEngine(private val context: Context) {
 data class Report(val accepted: Boolean, val sourceLabel: String, val summary: String)

 @Volatile var systemSilenced = false
 @Volatile var peak = 0
 @Volatile var nonSilentSamples = 0L
  private set

 private val running = AtomicBoolean(false)
 private val holdWrites = AtomicBoolean(false)
 private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 private val main = Handler(Looper.getMainLooper())
 private var worker: Thread? = null
 private var input: AudioRecord? = null
 private var callback: AudioManager.AudioRecordingCallback? = null

 fun begin(output: File, onProbe: (Report) -> Unit) {
  check(worker == null) { "Capture already started" }
  running.set(true)
  worker = Thread {
   try {
    probeAndRecord(output, onProbe)
   } catch (t: Throwable) {
    output.delete()
    val report = Report(false, "None", "Call capture failed: ${t.message}")
    main.post { onProbe(report) }
   } finally {
    releaseInput()
   }
  }.also { it.name = "proofvault-call-capture"; it.start() }
 }

 fun setPaused(paused: Boolean) { holdWrites.set(paused) }

 fun stop() {
  running.set(false)
  runCatching { input?.stop() }
  runCatching { worker?.join(4000) }
 }

 private fun probeAndRecord(output: File, onProbe: (Report) -> Unit) {
  val inCall = inCall()
  val attempts = mutableListOf<String>()
  for (candidate in candidates()) {
   if (!running.get()) break
   val opened = open(candidate) ?: run {
    attempts += "${candidate.label}: unavailable"
    continue
   }
   val chunk = readWindow(opened.audio, 320)
   val silenced = opened.silenced || sessionSilenced(opened.audio.audioSessionId)
   systemSilenced = silenced
   peak = chunk.peak
   attempts += "${candidate.label}: peak=${chunk.peak} silenced=$silenced"
   if (!CallCapturePolicy.accept(silenced, chunk.peak, inCall)) {
    releaseOpened(opened)
    continue
   }
   input = opened.audio
   val label = candidate.label
   val summary = "Capturing $label (peak ${chunk.peak}). Listen back before treating this as both sides. " +
    attempts.joinToString("; ")
   main.post { onProbe(Report(true, label, summary)) }
   acceptedLoop(opened, output, chunk)
   if (nonSilentSamples == 0L) output.delete()
   return
  }
  output.delete()
  if (!running.get()) return
  val why = if (inCall)
   "Telephony silenced every capture source during this call. Samsung Phone's own recorder is the supported two-sided path on this device; link the Recordings/Call folder. "
  else
   "No microphone source delivered audio. "
  val report = Report(false, "None", why + attempts.joinToString("; "))
  main.post { onProbe(report) }
 }

 private fun acceptedLoop(opened: Opened, output: File, first: Chunk) {
  val frame = opened.frameBytes
  RandomAccessFile(output, "rw").use { file ->
   writeHeader(file, 0, opened.sampleRate, opened.channels)
   var dataBytes = 0L
   fun commit(pcm: ByteArray, length: Int) {
    if (length <= 0 || holdWrites.get()) return
    val offset = if (dataBytes == 0L) CallCapturePolicy.firstAudibleOffset(pcm, length, frame) else 0
    val count = length - offset
    if (count <= 0) return
    file.write(pcm, offset, count)
    dataBytes += count
    var i = offset
    while (i + 1 < offset + count) {
     val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 255)).toShort().toInt()
     val mag = kotlin.math.abs(sample)
     if (mag > peak) peak = mag
     if (mag > 0) nonSilentSamples++
     i += 2
    }
   }
   commit(first.pcm, first.length)
   val buffer = ByteArray(8192)
   var sinceHeader = 0L
   while (running.get()) {
    val n = opened.audio.read(buffer, 0, buffer.size)
    if (n < 0) break
    if (n == 0) continue
    val silencedNow = opened.silenced || sessionSilenced(opened.audio.audioSessionId)
    systemSilenced = silencedNow
    if (silencedNow) continue
    commit(buffer, n)
    sinceHeader += n
    if (sinceHeader > opened.sampleRate.toLong() * opened.channels * 2) {
     val pos = file.filePointer
     writeHeader(file, dataBytes, opened.sampleRate, opened.channels)
     file.seek(pos)
     sinceHeader = 0
    }
   }
   val pos = file.filePointer
   writeHeader(file, dataBytes, opened.sampleRate, opened.channels)
   file.seek(pos)
  }
 }

 private fun candidates(): List<Candidate> {
  val uplink = Candidate("Voice uplink", MediaRecorder.AudioSource.VOICE_UPLINK, mono = true, relax = false)
  val downlink = Candidate("Voice downlink", MediaRecorder.AudioSource.VOICE_DOWNLINK, mono = true, relax = false)
  return listOf(
   Candidate("Voice call", MediaRecorder.AudioSource.VOICE_CALL, mono = true, relax = false),
   Candidate("Voice call stereo", MediaRecorder.AudioSource.VOICE_CALL, mono = false, relax = false),
   uplink,
   downlink,
   Candidate("Unprocessed microphone", MediaRecorder.AudioSource.UNPROCESSED, mono = true, relax = true),
   Candidate("Microphone", MediaRecorder.AudioSource.MIC, mono = true, relax = true),
   Candidate("Voice recognition", MediaRecorder.AudioSource.VOICE_RECOGNITION, mono = true, relax = true),
   Candidate("Voice communication", MediaRecorder.AudioSource.VOICE_COMMUNICATION, mono = true, relax = false)
  )
 }

 private fun open(candidate: Candidate): Opened? {
  val channelMask = if (candidate.mono) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
  val channels = if (candidate.mono) 1 else 2
  val rate = 16000
  val min = AudioRecord.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
  if (min <= 0) return null
  val format = AudioFormat.Builder()
   .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
   .setSampleRate(rate)
   .setChannelMask(channelMask)
   .build()
  val audio = try {
   AudioRecord.Builder()
    .setAudioSource(candidate.source)
    .setAudioFormat(format)
    .setBufferSizeInBytes(maxOf(min * 2, 8192))
    .build()
  } catch (_: Throwable) {
   return null
  }
  if (audio.state != AudioRecord.STATE_INITIALIZED) {
   audio.release()
   return null
  }
  val opened = Opened(audio, candidate.label, rate, channels, channels * 2, false)
  watch(opened)
  if (candidate.relax) relaxProcessing(audio.audioSessionId)
  return try {
   audio.startRecording()
   if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
    releaseOpened(opened)
    null
   } else opened
  } catch (_: Throwable) {
   releaseOpened(opened)
   null
  }
 }

 private fun watch(opened: Opened) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
  callback?.let { runCatching { audioManager.unregisterAudioRecordingCallback(it) } }
  val cb = object : AudioManager.AudioRecordingCallback() {
   override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
    val mine = configs.filter { it.clientAudioSessionId == opened.audio.audioSessionId }
    if (mine.isNotEmpty()) opened.silenced = mine.any { it.isClientSilenced }
   }
  }
  callback = cb
  audioManager.registerAudioRecordingCallback(cb, main)
 }

 private fun sessionSilenced(sessionId: Int): Boolean {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
  val configs = audioManager.activeRecordingConfigurations
  val mine = configs.filter { it.clientAudioSessionId == sessionId }
  return mine.isNotEmpty() && mine.any { it.isClientSilenced }
 }

 private fun readWindow(audio: AudioRecord, millis: Int): Chunk {
  val bytes = ByteArray(8192)
  val started = System.currentTimeMillis()
  var filled = 0
  var peak = 0
  while (running.get() && System.currentTimeMillis() - started < millis && filled + 2 < bytes.size) {
   val n = audio.read(bytes, filled, bytes.size - filled)
   if (n < 0) break
   if (n == 0) continue
   var i = filled
   val end = filled + n
   while (i + 1 < end) {
    val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 255)).toShort().toInt()
    peak = maxOf(peak, kotlin.math.abs(sample))
    i += 2
   }
   filled = end
  }
  return Chunk(bytes, filled, peak)
 }

 private fun relaxProcessing(sessionId: Int) {
  runCatching { AcousticEchoCanceler.create(sessionId)?.enabled = false }
  runCatching { NoiseSuppressor.create(sessionId)?.enabled = false }
  runCatching { AutomaticGainControl.create(sessionId)?.enabled = false }
 }

 private fun inCall(): Boolean {
  val mode = audioManager.mode
  return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
 }

 private fun releaseOpened(opened: Opened) {
  if (input === opened.audio) input = null
  runCatching { opened.audio.stop() }
  runCatching { opened.audio.release() }
 }

 private fun releaseInput() {
  callback?.let { runCatching { audioManager.unregisterAudioRecordingCallback(it) } }
  callback = null
  val audio = input
  input = null
  if (audio != null) {
   runCatching { audio.stop() }
   runCatching { audio.release() }
  }
 }

 private fun writeHeader(file: RandomAccessFile, dataBytes: Long, sampleRate: Int, channels: Int) {
  file.seek(0)
  fun u32(n: Long) { repeat(4) { file.write(((n ushr (8 * it)) and 255).toInt()) } }
  fun u16(n: Int) { file.write(n and 255); file.write((n ushr 8) and 255) }
  val block = channels * 2
  file.writeBytes("RIFF"); u32(dataBytes + 36); file.writeBytes("WAVEfmt ")
  u32(16); u16(1); u16(channels); u32(sampleRate.toLong()); u32((sampleRate * block).toLong())
  u16(block); u16(16); file.writeBytes("data"); u32(dataBytes)
 }

 private class Opened(
  val audio: AudioRecord,
  val label: String,
  val sampleRate: Int,
  val channels: Int,
  val frameBytes: Int,
  @Volatile var silenced: Boolean
 )
 private data class Candidate(val label: String, val source: Int, val mono: Boolean, val relax: Boolean)
 private data class Chunk(val pcm: ByteArray, val length: Int, val peak: Int)
}
