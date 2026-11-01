package com.aintcloud.proofvault

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager

/** Reads the platform-designated default dialer. Does not access call audio. */
object DialerInspector {
 data class Result(val packageName: String?, val label: String, val advice: String)
 fun inspect(context: Context): Result {
  val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
  val name = telecom.defaultDialerPackage
  val label = when (name) {
   "com.google.android.dialer" -> "Phone by Google"
   "com.samsung.android.dialer" -> "Samsung Phone"
   null -> "Default dialer not reported"
   else -> name
  }
  val advice = when (name) {
   "com.google.android.dialer" -> "Look for call recording options within Phone by Google. Feature availability depends on device and region."
   "com.samsung.android.dialer" -> "Look for Record calls in Samsung Phone settings. Available options depend on device firmware and region."
   else -> "Check your calling app's settings. ProofVault can import recordings shared or exposed through the Android document picker."
  }
  return Result(name, label, advice)
 }
}
