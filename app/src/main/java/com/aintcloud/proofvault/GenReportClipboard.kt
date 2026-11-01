package com.aintcloud.proofvault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object GenReportClipboard {
 fun copy(context: Context, report: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("ProofVault compatibility report", report))
 }
}
