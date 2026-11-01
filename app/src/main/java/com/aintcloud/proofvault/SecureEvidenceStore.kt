package com.aintcloud.proofvault

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Stores evidence encrypted with Android Keystore AES-GCM.
 * Temporary plaintext is created only for authorized playback/export.
 * Caller must delete plaintext in a finally block.
 */
class SecureEvidenceStore(private val context: Context) {
 private val crypto = VaultCrypto()
 private val root = File(context.filesDir, "secure_evidence").apply { mkdirs() }
 fun seal(plaintext: File): File {
  require(plaintext.isFile && plaintext.length() > 0L)
  val dest = File(root, UUID.randomUUID().toString() + ".pvault")
  crypto.encrypt(plaintext, dest)
  require(dest.isFile && dest.length() > 32L)
  EvidenceLedger(context).register(dest)
  return dest
 }
 fun openTemporary(encrypted: File): File {
  require(owns(encrypted)) { "Not managed vault evidence" }
  val temporary = File.createTempFile("proofvault-", ".m4a", context.cacheDir)
  try { crypto.decrypt(encrypted, temporary); return temporary }
  catch (t: Throwable) { temporary.delete(); throw t }
 }
 fun owns(file: File) = runCatching { file.canonicalFile.parentFile == root.canonicalFile }.getOrDefault(false)
 fun list(): List<File> = root.listFiles()?.filter { it.isFile && it.extension == "pvault" }?.sortedByDescending { it.lastModified() } ?: emptyList()
 fun delete(file: File): Boolean {
  if (!owns(file)) return false
  val result = file.delete()
  if (result) EvidenceLedger(context).remove(file)
  return result
 }
}
