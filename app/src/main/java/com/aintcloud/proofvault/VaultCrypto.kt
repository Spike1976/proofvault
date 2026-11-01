package com.aintcloud.proofvault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption for new evidence at rest. Ciphertext format:
 * 4-byte magic (PV01), 1-byte IV length, IV bytes, encrypted content and GCM tag.
 * Original input is deleted only after the encrypted output is fully written.
 * Not yet integrated with audio recording or the vault UI.
 */
class VaultCrypto {
 private val alias = "proofvault_evidence_v1"
 private fun key(): SecretKey {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(alias, null) as? SecretKey)?.let { return it }
  val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
  generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
   .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setKeySize(256).build())
  return generator.generateKey()
 }
 fun encrypt(input: File, output: File) {
  require(input.canonicalPath != output.canonicalPath)
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE, key())
  try {
   FileOutputStream(output).use { raw ->
    raw.write(byteArrayOf(80, 86, 48, 49))
    raw.write(cipher.iv.size)
    raw.write(cipher.iv)
    javax.crypto.CipherOutputStream(raw, cipher).use { encrypted ->
     FileInputStream(input).use { it.copyTo(encrypted) }
    }
   }
  } catch (t: Throwable) { output.delete(); throw t }
 }
 fun decrypt(input: File, output: File) {
  require(input.canonicalPath != output.canonicalPath)
  try {
   FileInputStream(input).use { raw ->
    val magic = ByteArray(4)
    require(raw.read(magic) == 4 && magic.contentEquals(byteArrayOf(80,86,48,49))) { "Invalid vault format" }
    val n = raw.read()
    require(n in 12..16)
    val iv = ByteArray(n)
    require(raw.read(iv) == n)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
    javax.crypto.CipherInputStream(raw, cipher).use { decrypted ->
     FileOutputStream(output).use { decrypted.copyTo(it) }
    }
   }
  } catch (t: Throwable) { output.delete(); throw t }
 }
}
