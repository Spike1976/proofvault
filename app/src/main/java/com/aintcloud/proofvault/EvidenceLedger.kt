package com.aintcloud.proofvault

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Local baseline SHA-256 index. Detects accidental changes to registered evidence.
 * Not tamper-proof: a person with control of the device/app data could alter the index.
 */
class EvidenceLedger(context: Context) {
 private val prefs = context.getSharedPreferences("proofvault_integrity_v1", Context.MODE_PRIVATE)
 private fun key(file: File) = "hash_" + file.name
 fun register(file: File) {
  require(file.isFile && file.length() > 0L) { "Cannot register empty or missing evidence" }
  prefs.edit().putString(key(file), EvidenceIntegrity.sha256(file)).commit()
 }
 fun status(file: File): Status {
  if (!file.isFile) return Status.MISSING
  val baseline = prefs.getString(key(file), null) ?: return Status.UNINDEXED
  return if (MessageDigest.isEqual(baseline.toByteArray(), EvidenceIntegrity.sha256(file).toByteArray()))
   Status.VERIFIED else Status.CHANGED
 }
 fun remove(file: File) { prefs.edit().remove(key(file)).apply() }
 enum class Status { VERIFIED, CHANGED, UNINDEXED, MISSING }
}
