package com.aintcloud.proofvault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Private app-scoped storage; app-managed encryption is a future milestone. */
class VaultRepository(private val context: Context) {
 private val folder get() = File(context.filesDir, "recordings").apply { mkdirs() }
 fun newRecording(): File = File(folder, "Recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a")
 fun list(): List<File> = folder.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("m4a", "mp3", "wav", "aac", "ogg") }?.sortedByDescending { it.lastModified() } ?: emptyList()
 fun valid(file: File): Boolean = runCatching { file.canonicalFile.parentFile == folder.canonicalFile }.getOrDefault(false)
 fun delete(file: File): Boolean = valid(file) && file.delete()
 fun importAudio(uri: Uri): File {
  val label = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
   val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
   if (i >= 0 && cursor.moveToFirst()) cursor.getString(i) else null
  } ?: "imported_audio"
  val ext = label.substringAfterLast('.', "m4a").lowercase().takeIf { it in setOf("m4a", "mp3", "wav", "aac", "ogg") } ?: "m4a"
  val destination = File(folder, "Import_${System.currentTimeMillis()}.$ext")
  try {
   context.contentResolver.openInputStream(uri).use { input ->
    requireNotNull(input) { "Cannot open selected file" }
    FileOutputStream(destination).use { output -> input.copyTo(output) }
   }
  } catch (t: Throwable) { destination.delete(); throw t }
  return destination
 }
 fun sha256(file: File): String {
  require(valid(file))
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
   val buffer = ByteArray(8192)
   while (true) {
    val n = input.read(buffer)
    if (n < 0) break
    digest.update(buffer, 0, n)
   }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
 }
}
