package com.aintcloud.proofvault

/**
 * Pure decisions for in-call capture. No Android dependencies, so unit tests
 * can lock the silence rules without a device.
 *
 * On Samsung Android 12+ the telephony HAL zero-fills other capture clients
 * while MODE_IN_CALL is active (dumpsys: "src:MIC silenced"). Those samples
 * are exact zeros, not a quiet room. A file of that audio is not evidence.
 */
object CallCapturePolicy {
 const val ENERGY_FLOOR = 96

 enum class Verdict { ACCEPT, REJECT_SILENCED, REJECT_IN_CALL_ZEROS }

 fun verdict(silenced: Boolean, peak: Int, inCall: Boolean): Verdict = when {
  silenced -> Verdict.REJECT_SILENCED
  inCall && peak <= 0 -> Verdict.REJECT_IN_CALL_ZEROS
  else -> Verdict.ACCEPT
 }

 fun accept(silenced: Boolean, peak: Int, inCall: Boolean): Boolean =
  verdict(silenced, peak, inCall) == Verdict.ACCEPT

 /**
  * Byte offset of the first frame that is not exact digital silence.
  * Natural pauses after that point must be kept.
  */
 fun firstAudibleOffset(pcm: ByteArray, length: Int, frameBytes: Int): Int {
  if (frameBytes <= 0 || length < frameBytes) return 0
  val limit = length - (length % frameBytes)
  var i = 0
  while (i + frameBytes <= limit) {
   var audible = false
   var b = 0
   while (b < frameBytes) {
    if (pcm[i + b] != 0.toByte()) { audible = true; break }
    b++
   }
   if (audible) return i
   i += frameBytes
  }
  return limit
 }
}
