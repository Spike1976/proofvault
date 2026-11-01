package com.aintcloud.proofvault

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/** Lossless container conversion for AAC audio to an audio-only MP4. */
object AudioMp4Exporter {
 fun convert(input: File, destination: File) {
  require(input.isFile && input.length() > 0)
  val extractor = MediaExtractor()
  var muxer: MediaMuxer? = null
  try {
   extractor.setDataSource(input.absolutePath)
   val track = (0 until extractor.trackCount).firstOrNull {
    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
   } ?: throw IllegalArgumentException("Recording has no supported audio stream")
   val format = extractor.getTrackFormat(track)
   val mime = format.getString(MediaFormat.KEY_MIME)
   require(mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
    "MP4 conversion currently supports AAC recordings. Export original for WAV/MP3 audio."
   }
   extractor.selectTrack(track)
   muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
   val outputTrack = muxer.addTrack(format)
   muxer.start()
   val max = format.getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
   val buffer = java.nio.ByteBuffer.allocateDirect(max.coerceIn(65536, 4 * 1024 * 1024))
   val info = android.media.MediaCodec.BufferInfo()
   while (true) {
    buffer.clear()
    val size = extractor.readSampleData(buffer, 0)
    if (size < 0) break
    info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
    muxer.writeSampleData(outputTrack, buffer, info)
    extractor.advance()
   }
  } catch (e: Exception) {
   destination.delete()
   throw e
  } finally {
   try { muxer?.stop() } catch (_: Exception) {}
   muxer?.release()
   extractor.release()
  }
 }
 private fun MediaFormat.getIntegerOrDefault(key: String, fallback: Int): Int =
  if (containsKey(key)) getInteger(key) else fallback
}
