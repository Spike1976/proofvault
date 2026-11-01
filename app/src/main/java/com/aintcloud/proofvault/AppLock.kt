package com.aintcloud.proofvault

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Device-authenticated app lock. Only protects app access, not raw storage encryption. */
object AppLock {
 private const val STORE = "proofvault_settings"
 private const val KEY = "app_lock_enabled"
 fun enabled(context: Context): Boolean = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean(KEY, false)
 fun setEnabled(context: Context, value: Boolean) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
 fun available(context: Context): Boolean {
  val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
  if (!keyguard.isDeviceSecure) return false
  val methods = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
  return BiometricManager.from(context).canAuthenticate(methods) == BiometricManager.BIOMETRIC_SUCCESS
 }
 fun authenticate(activity: FragmentActivity, title: String, success: () -> Unit, failure: (String) -> Unit) {
  val methods = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
  val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
   object : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { success() }
    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
     failure(errString.toString())
    }
   })
  prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
   .setTitle(title)
   .setSubtitle("Verify your identity to access ProofVault")
   .setAllowedAuthenticators(methods)
   .build())
 }
}
