package com.aintcloud.proofvault

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** User-selected export of evidence with SHA-256 manifest; unencrypted ZIP. */
class EvidenceExport(private val context: Context) {
 fun export(files: List<File>, destination: Uri) {
  val vault = VaultRepository(context)
  require(files.all { vault.valid(it) }) { "File outside vault" }
  val output = context.contentResolver.openOutputStream(destination) ?: error("Cannot open export destination")
  output.use {
   ZipOutputStream(it).use { zip ->
    val names = mutableSetOf<String>()
    val manifest = StringBuilder("ProofVault evidence export\\nSHA-256 | filename\\n")
    files.forEach { file ->
     val base = file.name.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
     var name = base
     var index = 1
     while (!names.add(name)) { name = "${index++}_$base" }
     zip.putNextEntry(ZipEntry("evidence/$name"))
     file.inputStream().use { input -> input.copyTo(zip) }
     zip.closeEntry()
     manifest.append(EvidenceIntegrity.sha256(file)).append(" | ").append(name).append('\\n')
    }
    zip.putNextEntry(ZipEntry("manifest.txt"))
    zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
    zip.closeEntry()
   }
  }
 }
}
