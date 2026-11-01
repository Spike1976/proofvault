package com.aintcloud.proofvault

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.text.DateFormat
import java.util.Date

private val ink = Color(0xFF07141E)
private val panel = Color(0xFF112534)
private val teal = Color(0xFF27E5D5)
private val muted = Color(0xFF98AFBC)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme = darkColorScheme(primary = teal, background = ink, surface = panel)) {
    ProofVaultApp()
   }
  }
 }
}

@Composable
fun ProofVaultApp() {
 val context = LocalContext.current
 val vault = remember { VaultRepository(context) }
 val recorder = remember { AudioRecorder(context) }
 var tab by remember { mutableStateOf("Home") }
 var recordings by remember { mutableStateOf(vault.list()) }
 var query by remember { mutableStateOf("") }
 var isRecording by remember { mutableStateOf(false) }
 var currentFile by remember { mutableStateOf<File?>(null) }
 var playingFile by remember { mutableStateOf<String?>(null) }
 var player by remember { mutableStateOf<MediaPlayer?>(null) }
 var error by remember { mutableStateOf<String?>(null) }
 val refresh = { recordings = vault.list() }
 val startRecording = {
  try {
   val file = vault.newRecording()
   recorder.start(file)
   currentFile = file
   isRecording = true
  } catch (e: Exception) { error = e.message ?: "Unable to start recording" }
 }
 val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
  if (granted) startRecording() else error = "Microphone permission is required."
 }
 val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if (uri != null) {
   try { vault.importAudio(uri); refresh(); tab = "Vault" }
   catch (e: Exception) { error = e.message ?: "Import failed" }
  }
 }
 funPlayPlaceholder()
 DisposableEffect(Unit) {
  onDispose {
   player?.release()
   if (recorder.isRecording()) runCatching { recorder.stop() }
  }
 }
 fun play(file: File) {
  try {
   player?.release()
   player = null
   if (playingFile == file.name) {
    playingFile = null
   } else {
    val newPlayer = MediaPlayer()
    newPlayer.setDataSource(file.absolutePath)
    newPlayer.prepare()
    newPlayer.setOnCompletionListener {
     it.release()
     player = null
     playingFile = null
    }
    newPlayer.start()
    player = newPlayer
    playingFile = file.name
   }
  } catch (e: Exception) {
   playingFile = null
   error = e.message ?: "Playback failed"
  }
 }
 Scaffold(containerColor = ink, bottomBar = {
  NavigationBar(containerColor = panel) {
   listOf("Home", "Record", "Vault", "Settings").forEach { page ->
    val icon = when (page) {
     "Home" -> Icons.Default.Home
     "Record" -> Icons.Default.Mic
     "Vault" -> Icons.Default.Folder
     else -> Icons.Default.Settings
    }
    NavigationBarItem(
     selected = tab == page,
     onClick = { tab = page },
     icon = { Icon(icon, contentDescription = page) },
     label = { Text(page) }
    )
   }
  }
 }) { padding ->
  Column(
   modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
   verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
   Text("ProofVault", style = MaterialTheme.typography.headlineMedium, color = teal)
   when (tab) {
    "Home" -> {
     Text("Your Evidence Vault", style = MaterialTheme.typography.titleLarge)
     ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(20.dp)) {
       Text("${recordings.size} saved recordings", style = MaterialTheme.typography.headlineSmall)
       Text("Stored privately on this device", color = muted)
      }
     }
     Button(onClick = { tab = "Record" }, modifier = Modifier.fillMaxWidth()) { Text("Record Audio") }
     OutlinedButton(onClick = { importAudio.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import Audio") }
     Text("Recent recordings", style = MaterialTheme.typography.titleMedium)
     LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(recordings.take(5), key = { it.absolutePath }) { file ->
       EvidenceItem(file, playingFile == file.name, onPlay = { play(file) },
        onDelete = {
         player?.release(); player = null; playingFile = null
         vault.delete(file); refresh()
        })
      }
     }
    }
    "Record" -> {
     Text("Microphone Recorder", style = MaterialTheme.typography.titleLarge)
     Text("Capture device microphone audio. Two-sided phone call capture is not supported.", color = muted)
     Spacer(Modifier.height(24.dp))
     Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      FilledIconButton(
       onClick = {
        if (isRecording) {
         try {
          recorder.stop()
          isRecording = false
          currentFile = null
          refresh()
         } catch (e: Exception) {
          isRecording = false
          currentFile?.delete()
          currentFile = null
          error = "Recording failed: ${e.message}"
         }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
         startRecording()
        } else {
         micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
       },
       modifier = Modifier.size(120.dp),
       colors = IconButtonDefaults.filledIconButtonColors(containerColor = teal, contentColor = ink)
      ) {
       Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = if (isRecording) "Stop recording" else "Start recording", modifier = Modifier.size(56.dp))
      }
     }
     Text(if (isRecording) "Recording. Tap to stop and save." else "Tap to begin recording.", color = teal)
     Text("Version 0.1 requires the app to remain open during recording.", color = muted)
    }
    "Vault" -> {
     Text("Vault Library", style = MaterialTheme.typography.titleLarge)
     OutlinedTextField(query, { query = it }, label = { Text("Search recordings") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
     OutlinedButton(onClick = { importAudio.launch(arrayOf("audio/*")) }) { Text("Import Audio") }
     val visible = recordings.filter { it.name.contains(query, ignoreCase = true) }
     Text("${visible.size} items", color = muted)
     LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(visible, key = { it.absolutePath }) { file ->
       EvidenceItem(file, playingFile == file.name, onPlay = { play(file) },
        onDelete = {
         player?.release(); player = null; playingFile = null
         vault.delete(file); refresh()
        })
      }
     }
    }
    "Settings" -> {
     Text("Settings & Security", style = MaterialTheme.typography.titleLarge)
     InfoCard("Privacy", "Recordings remain in app-private local storage. No cloud or telemetry.")
     InfoCard("Recording", "Manual device-microphone capture only in v0.1.0.")
     InfoCard("Security", "Biometric lock and app-managed file encryption are planned, not implemented.")
     InfoCard("About", "ProofVault v0.1.0 • Ain't Cloud Software")
    }
   }
  }
 }
 if (error != null) AlertDialog(
  onDismissRequest = { error = null },
  title = { Text("ProofVault") },
  text = { Text(error ?: "") },
  confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }
 )
}

@Composable
private fun InfoCard(title: String, subtitle: String) {
 ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
   Text(title, style = MaterialTheme.typography.titleMedium)
   Text(subtitle, color = muted)
  }
 }
}

@Composable
private fun EvidenceItem(file: File, playing: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
 var confirmDelete by remember { mutableStateOf(false) }
 ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
  Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
   Icon(Icons.Default.GraphicEq, null, tint = teal)
   Spacer(Modifier.width(10.dp))
   Column(Modifier.weight(1f)) {
    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text("${DateFormat.getDateTimeInstance().format(Date(file.lastModified()))} • ${file.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = muted)
   }
   IconButton(onClick = onPlay) {
    Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = if (playing) "Stop" else "Play", tint = teal)
   }
   IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = muted) }
  }
 }
 if (confirmDelete) AlertDialog(
  onDismissRequest = { confirmDelete = false },
  title = { Text("Delete recording?") },
  text = { Text("Permanently delete ${file.name}? This cannot be undone.") },
  confirmButton = { TextButton(onClick = { onDelete(); confirmDelete = false }) { Text("Delete") } },
  dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
 )
}

@Composable
private fun funPlayPlaceholder() {}
