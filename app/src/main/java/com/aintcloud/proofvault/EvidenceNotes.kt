package com.aintcloud.proofvault

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Small local metadata store for editable evidence notes.
 * File keys use a digest of the basename, avoiding arbitrary preference keys.
 * Stored in private app preferences; not independently encrypted.
 */
class EvidenceNotes(context: Context) {
 private val preferences = context.getSharedPreferences("evidence_notes", Context.MODE_PRIVATE)
 private fun key(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(file.name.toByteArray(Charsets.UTF_8))
  return digest.joinToString("") { "%02x".format(it) }
 }
 fun get(file: File): String = preferences.getString(key(file), "") ?: ""
 fun set(file: File, notes: String) { preferences.edit().putString(key(file), notes.take(20_000)).apply() }
 fun remove(file: File) { preferences.edit().remove(key(file)).apply() }
}
