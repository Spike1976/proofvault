package com.aintcloud.proofvault

import android.content.Context
import android.os.Build
import java.text.DateFormat
import java.util.Date

/** Transparent device diagnostics; does not claim two-sided audio support. */
object CompatibilityReport {
 fun create(context: Context): String {
  val preferences = CapturePreferences(context)
  val folder = RecordingFolderConnector(context)
  val mode = if (folder.isConnected()) "Folder connected" else "No folder connected"
  return """
ProofVault compatibility report
Generated: ${DateFormat.getDateTimeInstance().format(Date())}
Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
Manufacturer: ${Build.MANUFACTURER}
Model: ${Build.MODEL}
Recording source: ${preferences.source().label}
Recording folder: $mode
Foreground microphone recording: User-initiated only
Automatic capture of both sides of cellular calls: NOT VERIFIED
Accessibility interception: Not implemented
Note: Selecting a source cannot bypass operating system restrictions.
""".trimIndent()
 }
}
