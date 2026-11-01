package com.aintcloud.proofvault

import android.content.Context
import android.net.Uri
import android.content.Intent
import androidx.documentfile.provider.DocumentFile

/**
 * Universal document-provider connector for recording folders.
 * Works with any installed provider exposing recordings through the
 * Android Storage Access Framework. Never accesses protected call audio.
 */
class RecordingFolderConnector(private val context: Context) {
 private val preferences = context.getSharedPreferences("proofvault_folder_connector", Context.MODE_PRIVATE)
 fun isConnected(): Boolean = preferences.getString("tree_uri", null) != null
 fun connect(uri: Uri) {
  context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  val previous = preferences.getString("tree_uri", null)
  val edit = preferences.edit().putString("tree_uri", uri.toString())
  if (previous != uri.toString()) edit.remove("imported_ids")
  edit.apply()
 }
 fun disconnect() { 
  preferences.getString("tree_uri", null)?.let { uri ->
   runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
  }
  preferences.edit().remove("tree_uri").remove("imported_ids").apply()
 }
 fun sync(): SyncResult {
  val uri = preferences.getString("tree_uri", null) ?: return SyncResult(0, 0, "No folder connected")
  val root = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: return SyncResult(0, 0, "Folder unavailable")
  if (!root.canRead()) return SyncResult(0, 0, "Folder permission unavailable")
  val vault = VaultRepository(context)
  val seen = preferences.getStringSet("imported_ids", emptySet()).orEmpty().toMutableSet()
  var added = 0
  var skipped = 0
  var failures = 0
  // Bounded scan to avoid very large provider trees or recursive folder loops.
  fun scan(folder: DocumentFile, depth: Int) {
   if (depth > 3 || added + skipped + failures >= 1500) return
   for (file in folder.listFiles()) {
    if (added + skipped + failures >= 1500) break
    if (file.isDirectory) { scan(file, depth + 1); continue }
    if (!file.isFile) continue
    val ext = file.name.orEmpty().substringAfterLast('.', "").lowercase()
    if (ext !in setOf("m4a", "mp3", "wav", "aac", "ogg")) continue
    // Ignore empty and actively modified recordings; retry at a later sync.
    val size = file.length()
    val modified = file.lastModified()
    if (size <= 0L || (modified > 0L && System.currentTimeMillis() - modified < 60_000L)) { skipped++; continue }
    val key = file.uri.toString() + ":" + modified + ":" + size
    if (key in seen) { skipped++; continue }
    try {
     vault.importAudio(file.uri)
     seen.add(key)
     added++
    } catch (_: Exception) { failures++ }
   }
  }
  scan(root, 0)
  preferences.edit().putStringSet("imported_ids", seen).apply()
  return SyncResult(added, skipped, if (failures > 0) "$failures file(s) could not be imported" else null)
 }
 data class SyncResult(val imported: Int, val alreadySeen: Int, val warning: String?)
}
