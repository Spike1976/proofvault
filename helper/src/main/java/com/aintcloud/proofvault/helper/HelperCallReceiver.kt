package com.aintcloud.proofvault.helper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Independent, opt-in call-state helper with an explicit ProofVault launch action.
 * No covert audio recording and no launch of a protected microphone service.
 */
class HelperCallReceiver: BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
  if (!context.getSharedPreferences("helper", Context.MODE_PRIVATE).getBoolean("enabled",false)) return
  if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
  val manager = context.getSystemService(NotificationManager::class.java)
  val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
  if (state == TelephonyManager.EXTRA_STATE_IDLE) {
   manager.cancel(4201)
   return
  }
  if (state != TelephonyManager.EXTRA_STATE_OFFHOOK) return
  if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
  manager.createNotificationChannel(NotificationChannel("helper_calls","ProofVault call prompts",NotificationManager.IMPORTANCE_HIGH))
  val launch = context.packageManager.getLaunchIntentForPackage("com.aintcloud.proofvault")
   ?: Intent(context, HelperActivity::class.java)
  val pi = PendingIntent.getActivity(context, 101, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification = android.app.Notification.Builder(context,"helper_calls")
   .setSmallIcon(android.R.drawable.ic_menu_call)
   .setContentTitle("Call connected")
   .setContentText("Open ProofVault to start a supported recording")
   .setContentIntent(pi)
   .setAutoCancel(true)
   .build()
  manager.notify(4201,notification)
 }
}
