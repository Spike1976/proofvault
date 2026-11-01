package com.aintcloud.proofvault

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Basic audio sanity checks. A file containing audio does not establish
 * that the remote participant can be heard.
 */
object RecordingQuality {
 data class Check(val playable: Boolean, val durationMs: Long, val message: String)
 fun inspect(file: File): Check {
  if (!file.isFile || file.length() == 0L) return Check(false, 0, "Missing or empty file")
  val retriever = MediaMetadataRetriever()
  return try {
   retriever.setDataSource(file.absolutePath)
   val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
   if (duration <= 0L) Check(false, duration, "No readable audio duration")
   else if (duration < 1500L) Check(true, duration, "Very short recording; verify audio")
   else Check(true, duration, "Audio readable; both parties not verified")
  } catch (e: Exception) {
   Check(false, 0, "Cannot decode audio metadata")
  } finally { runCatching { retriever.release() } }
 }
}
