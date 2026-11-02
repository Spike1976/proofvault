package com.aintcloud.proofvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallCapturePolicyTest {
 @Test fun inCallDigitalZerosAreRejected() {
  assertEquals(CallCapturePolicy.Verdict.REJECT_IN_CALL_ZEROS, CallCapturePolicy.verdict(false, 0, true))
  assertFalse(CallCapturePolicy.accept(false, 0, true))
 }

 @Test fun silencedSessionIsRejectedEvenWithEnergy() {
  assertEquals(CallCapturePolicy.Verdict.REJECT_SILENCED, CallCapturePolicy.verdict(true, 4000, true))
  assertFalse(CallCapturePolicy.accept(true, 4000, false))
 }

 @Test fun liveInCallEnergyIsAccepted() {
  assertTrue(CallCapturePolicy.accept(false, 120, true))
 }

 @Test fun quietOffCallMicrophoneIsAccepted() {
  assertTrue(CallCapturePolicy.accept(false, 0, false))
 }

 @Test fun leadingExactZerosAreTrimmedAndLaterSilenceIsKept() {
  val pcm = byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0)
  assertEquals(4, CallCapturePolicy.firstAudibleOffset(pcm, pcm.size, 2))
 }

 @Test fun stereoTrimStaysFrameAligned() {
  val pcm = byteArrayOf(0, 0, 0, 0, 0, 0, 1, 0)
  assertEquals(4, CallCapturePolicy.firstAudibleOffset(pcm, pcm.size, 4))
 }
}
