package com.aintcloud.proofvault

import java.io.File
import java.security.MessageDigest

/** Deterministic streaming SHA-256 for evidence integrity verification. */
object EvidenceIntegrity {
 fun sha256(file: File): String {
  require(file.isFile) { "Evidence file does not exist" }
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().buffered().use { stream ->
   val buffer = ByteArray(8192)
   while (true) {
    val count = stream.read(buffer)
    if (count == -1) break
    digest.update(buffer, 0, count)
   }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
 }
}
