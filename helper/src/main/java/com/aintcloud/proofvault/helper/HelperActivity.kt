package com.aintcloud.proofvault.helper

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Explicit, separate call-state helper. No audio interception or accessibility capture. */
class HelperActivity : Activity() {
 private lateinit var status: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,48,32,32) }
  fun button(label: String, action: () -> Unit) {
   root.addView(Button(this).apply { text = label; setOnClickListener { action() } })
  }
  root.addView(TextView(this).apply {
   text = "ProofVault Companion Helper"
   textSize = 22f
  })
  root.addView(TextView(this).apply {
   text = "Detects connected cellular calls and offers a user-triggered recorder action. It cannot grant access to protected call audio."
  })
  status = TextView(this)
  root.addView(status)
  button("Grant call and notification permissions") {
   val permissions = mutableListOf(Manifest.permission.READ_PHONE_STATE)
   if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
   requestPermissions(permissions.toTypedArray(), 101)
  }
  button("Enable call prompts") {
   getSharedPreferences("helper", MODE_PRIVATE).edit().putBoolean("enabled",true).apply()
   update()
  }
  button("Disable call prompts") {
   getSharedPreferences("helper", MODE_PRIVATE).edit().putBoolean("enabled",false).apply()
   update()
  }
  button("Open ProofVault") {
   val launch = packageManager.getLaunchIntentForPackage("com.aintcloud.proofvault")
   if (launch != null) startActivity(launch)
   else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.aintcloud.proofvault")))
  }
  setContentView(root)
  update()
 }
 override fun onResume() { super.onResume(); if (::status.isInitialized) update() }
 private fun update() {
  val enabled = getSharedPreferences("helper", MODE_PRIVATE).getBoolean("enabled", false)
  val permitted = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
  status.text = "Call alerts: ${if(enabled) "enabled" else "disabled"}; Phone permission: ${if(permitted) "granted" else "missing"}"
 }
}
