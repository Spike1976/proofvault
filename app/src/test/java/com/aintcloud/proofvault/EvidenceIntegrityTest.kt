package com.aintcloud.proofvault

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EvidenceIntegrityTest {
 @Test fun computesKnownSha256() {
  val f = File.createTempFile("proofvault-integrity", ".txt")
  try {
   f.writeText("abc")
   assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", EvidenceIntegrity.sha256(f))
  } finally { f.delete() }
 }
 @Test fun rejectsMissingFile() {
  val f = File(System.getProperty("java.io.tmpdir"), "missing-proofvault-${System.nanoTime()}")
  try {
   EvidenceIntegrity.sha256(f)
   fail("Expected missing file to be rejected")
  } catch (_: IllegalArgumentException) { }
 }
}
