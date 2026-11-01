package com.aintcloud.proofvault

import android.Manifest
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * User-enabled call-state awareness. Does not intercept telephone audio or
 * launch an Android 14+ microphone foreground service from the background.
 * Opens the app for a user-initiated recording action instead.
 */
class CallStateReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
  val preferences = context.getSharedPreferences("proofvault_settings", Context.MODE_PRIVATE)
  if (!preferences.getBoolean("call_assist_enabled", false)) return
  if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
  val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
  if (state != TelephonyManager.EXTRA_STATE_OFFHOOK) return

  val manager = context.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(
   NotificationChannel("call_assist", "Call recording assistance", NotificationManager.IMPORTANCE_HIGH)
  )
  val open = PendingIntent.getActivity(
   context, 41,
   Intent(context, MainActivity::class.java).putExtra("open_record", true)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
   PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )
  val notification = NotificationCompat.Builder(context, "call_assist")
   .setSmallIcon(android.R.drawable.ic_menu_call)
   .setContentTitle("ProofVault: Call detected")
   .setContentText("Tap to open the recorder. Call audio may be inaccessible on this device.")
   .setContentIntent(open)
   .setAutoCancel(true)
   .build()
  if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
   manager.notify(4102, notification)
  }
 }
}
