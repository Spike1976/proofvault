package com.aintcloud.proofvault

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import java.io.File
import java.text.DateFormat
import java.util.Date

private val ink = Color(0xFF07141E)
private val panel = Color(0xFF112534)
private val teal = Color(0xFF27E5D5)
private val muted = Color(0xFF98AFBC)

class MainActivity : FragmentActivity() {
 private var unlocked by mutableStateOf(false)
 private var authStarted = false
 private var lockEnabled by mutableStateOf(false)
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
   @Suppress("DEPRECATION")
   val shared = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
   if (shared != null) {
    try {
     VaultRepository(this).importAudio(shared)
     Toast.makeText(this, "Audio imported into ProofVault", Toast.LENGTH_LONG).show()
    } catch (error: Exception) {
     Toast.makeText(this, "Audio import failed: ${error.message}", Toast.LENGTH_LONG).show()
    }
   }
   intent.removeExtra(Intent.EXTRA_STREAM)
   intent.action = Intent.ACTION_MAIN
  }
  lockEnabled = AppLock.enabled(this)
  unlocked = !lockEnabled
  setContent {
   MaterialTheme(colorScheme = darkColorScheme(primary = teal, background = ink, surface = panel)) {
    if (unlocked) ProofVaultApp(startOnRecord = intent?.getBooleanExtra("open_record", false) == true, lockEnabled = lockEnabled, onEnableLock = { enabled ->
      if (!enabled) { AppLock.setEnabled(this, false); lockEnabled = false }
      else AppLock.authenticate(this, "Enable ProofVault lock", { AppLock.setEnabled(this, true); lockEnabled = true }, { })
    })
    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Button(onClick = { unlock() }) { Text("Unlock ProofVault") }
    }
   }
  }
 }
 override fun onResume() { super.onResume(); if (!unlocked && !authStarted) unlock() }
 override fun onStop() { super.onStop(); if (lockEnabled) unlocked = false; authStarted = false }
 private fun unlock() {
  if (authStarted) return
  authStarted = true
  AppLock.authenticate(this, "Unlock ProofVault", { unlocked = true; authStarted = false }, { authStarted = false })
 }
}

@Composable
fun ProofVaultApp(startOnRecord: Boolean = false, lockEnabled: Boolean = false, onEnableLock: (Boolean) -> Unit = {}) {
 val context = LocalContext.current
 val vault = remember { VaultRepository(context) }
 val secureStore = remember { SecureEvidenceStore(context) }
 var secureCount by remember { mutableIntStateOf(secureStore.list().size) }
 var chosenEncrypted by remember { mutableStateOf<File?>(null) }
 val capturePrefs = remember { CapturePreferences(context) }
 var selectedCapture by remember { mutableStateOf(capturePrefs.source()) }
 val connector = remember { RecordingFolderConnector(context) }
 var folderConnected by remember { mutableStateOf(connector.isConnected()) }
 var autoImport by remember { mutableStateOf(context.getSharedPreferences("proofvault_settings", android.content.Context.MODE_PRIVATE).getBoolean("auto_import_enabled", false)) }
 
 var tab by remember { mutableStateOf(if (startOnRecord) "Record" else "Home") }
 val settings = remember { context.getSharedPreferences("proofvault_settings", android.content.Context.MODE_PRIVATE) }
 var callAssist by remember { mutableStateOf(settings.getBoolean("call_assist_enabled", false)) }
 var recordings by remember { mutableStateOf(vault.list()) }
 var query by remember { mutableStateOf("") }
 val notes = remember { EvidenceNotes(context) }
 val ledger = remember { EvidenceLedger(context) }
 var isRecording by remember { mutableStateOf(RecordingService.recording) }
 var isPaused by remember { mutableStateOf(RecordingService.paused) }

 var playingFile by remember { mutableStateOf<String?>(null) }
 var player by remember { mutableStateOf<MediaPlayer?>(null) }
 var error by remember { mutableStateOf<String?>(null) }
 val refresh = { recordings = vault.list() }
 val startRecording = {
  try {
   ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java).setAction(RecordingService.START))
   isRecording = true; isPaused = false
  } catch (e: Exception) { error = e.message ?: "Could not start recording" }
 }
 val phonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
  val permitted = grants[Manifest.permission.READ_PHONE_STATE] == true
  callAssist = permitted
  settings.edit().putBoolean("call_assist_enabled", permitted).apply()
  if (!permitted) error = "Phone permission is required to enable call detection."
 }
 val playbackConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  if (result.resultCode == Activity.RESULT_OK && result.data != null) {
   try {
    val service = Intent(context, PlaybackCaptureService::class.java)
     .setAction(PlaybackCaptureService.START)
     .putExtra(PlaybackCaptureService.EXTRA_RESULT, result.resultCode)
     .putExtra(PlaybackCaptureService.EXTRA_DATA, result.data)
    ContextCompat.startForegroundService(context, service)
   } catch (e: Exception) { error = "Playback capture failed: ${e.message}" }
  } else error = "Playback recording requires Android's screen/audio capture permission."
 }
 val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
  if (granted) startRecording() else error = "Microphone permission is required."
 }
 val selectRecordingFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
  if (uri != null) {
   try {
    connector.connect(uri)
    folderConnected = true
    if (autoImport) AutoImportScheduler.enable(context)
    val result = connector.sync()
    refresh()
    error = "Connected. Imported ${result.imported} recordings." + (result.warning?.let { " $it" } ?: "")
   } catch (e: Exception) {
    error = "Folder connection failed: ${e.message}"
   }
  }
 }
 val exportEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri ->
  val encrypted = chosenEncrypted
  chosenEncrypted = null
  if (uri != null && encrypted != null) {
   var temp: File? = null
   try {
    val decrypted = secureStore.openTemporary(encrypted)
    temp = decrypted
    context.contentResolver.openOutputStream(uri)?.use { output -> decrypted.inputStream().use { it.copyTo(output) } }
     ?: error("Cannot open export destination")
   } catch (e: Exception) { error = "Encrypted export failed: ${e.message}" }
   finally { temp?.delete() }
  }
 }
 val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
  if (uri != null) {
   try { EvidenceExport(context).export(recordings, uri) }
   catch (e: Exception) { error = e.message ?: "Export failed" }
  }
 }
 val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if (uri != null) {
   try { vault.importAudio(uri); refresh(); tab = "Vault" }
   catch (e: Exception) { error = e.message ?: "Import failed" }
  }
 }
 DisposableEffect(context) {
  val receiver = object : BroadcastReceiver() {
   override fun onReceive(c: Context?, intent: Intent?) {
    isRecording = RecordingService.recording
    isPaused = RecordingService.paused
    recordings = vault.list()
    RecordingService.lastError?.let { error = it }
   }
  }
  ContextCompat.registerReceiver(context, receiver, IntentFilter(RecordingService.STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
  onDispose {
   context.unregisterReceiver(receiver)
   player?.release()
  }
 }
 LaunchedEffect(tab) {
  isRecording = RecordingService.recording
  isPaused = RecordingService.paused
  if (!isRecording) refresh()
 }
 fun play(file: File) {
  try {
   player?.release()
   player = null
   if (playingFile == file.name) {
    playingFile = null
   } else {
    val quality = RecordingQuality.inspect(file)
    if (!quality.playable) throw IllegalStateException("This recording cannot be decoded: ${quality.message}")
    val newPlayer = MediaPlayer()
    newPlayer.setAudioAttributes(
     android.media.AudioAttributes.Builder()
      .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
      .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()
    )
    newPlayer.setDataSource(file.absolutePath)
    newPlayer.setOnErrorListener { failedPlayer, what, extra ->
     error = "Playback error ($what/$extra). Check the saved audio file."
     playingFile = null
     player = null
     failedPlayer.reset()
     failedPlayer.release()
     true
    }
    newPlayer.prepare()
    newPlayer.setVolume(1f, 1f)
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
   modifier = Modifier.fillMaxSize().padding(padding).then(if (tab == "Settings") Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(16.dp),
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
       EvidenceItem(file, playingFile == file.name, notes, ledger, onArchive = {
         try { secureStore.seal(file); secureCount = secureStore.list().size; vault.delete(file); refresh() }
         catch (e: Exception) { error = "Archive failed: ${e.message}" }
        }, onPlay = { play(file) },
        onDelete = {
         player?.release(); player = null; playingFile = null
         notes.remove(file); vault.delete(file); refresh()
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
         context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.STOP))
         // Wait for the service to finish writing the MP4 header before refreshing.
         isRecording = false; isPaused = false
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
     Text(if (isRecording) (if (isPaused) "Recording paused" else "Recording active; notification visible") else "Tap to begin recording.", color = teal)
     if (isRecording) {
      OutlinedButton(onClick = {
       context.startService(Intent(context, RecordingService::class.java).setAction(if (isPaused) RecordingService.RESUME else RecordingService.PAUSE))
       isPaused = !isPaused
      }) { Text(if (isPaused) "Resume" else "Pause") }
     }
     Text("Recording continues when the screen is switched away. Start recording with the app open.", color = muted)
     val playbackDiagnostics = context.getSharedPreferences(PlaybackCaptureService.STATUS_PREFS, Context.MODE_PRIVATE)
     val captureResult = playbackDiagnostics.getString(PlaybackCaptureService.STATUS_KEY, null)
     if (captureResult != null) InfoCard("Last playback capture", captureResult)
     Text("App playback audio", style = MaterialTheme.typography.titleMedium)
     Text("Captures supported music/game/media audio with system consent. Phone calls, protected streams and apps that opt out cannot be captured.", color = muted)
     OutlinedButton(onClick = {
      if (PlaybackCaptureService.active) {
       context.startService(Intent(context, PlaybackCaptureService::class.java).setAction(PlaybackCaptureService.STOP))
      } else {
       val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
       playbackDiagnostics.edit().remove(PlaybackCaptureService.STATUS_KEY).apply()
       playbackConsent.launch(manager.createScreenCaptureIntent())
      }
     }, modifier = Modifier.fillMaxWidth()) {
      Text(if (PlaybackCaptureService.active) "Stop app audio capture" else "Record supported app audio")
     }
    }
    "Vault" -> {
     Text("Vault Library", style = MaterialTheme.typography.titleLarge)
     OutlinedTextField(query, { query = it }, label = { Text("Search recordings") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
     OutlinedButton(onClick = { importAudio.launch(arrayOf("audio/*")) }) { Text("Import Audio") }
     val visible = recordings.filter { it.name.contains(query, ignoreCase = true) || notes.get(it).contains(query, ignoreCase = true) }
     Text("${visible.size} items", color = muted)
     InfoCard("Encrypted archive", "$secureCount protected files. Archived audio requires export for playback.")
     secureStore.list().forEach { encrypted ->
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
       Icon(Icons.Default.Lock, contentDescription = null, tint = teal)
       Text(encrypted.name.take(18), modifier = Modifier.weight(1f))
       TextButton(onClick = { chosenEncrypted = encrypted; exportEncrypted.launch("ProofVault-Decrypted.m4a") }) { Text("Export") }
      }
     }
     OutlinedButton(onClick = { exportZip.launch("ProofVault-Evidence.zip") }, enabled = recordings.isNotEmpty()) { Text("Export evidence ZIP + SHA-256 manifest") }
     LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(visible, key = { it.absolutePath }) { file ->
       EvidenceItem(file, playingFile == file.name, notes, ledger, onArchive = {
         try { secureStore.seal(file); secureCount = secureStore.list().size; vault.delete(file); refresh() }
         catch (e: Exception) { error = "Archive failed: ${e.message}" }
        }, onPlay = { play(file) },
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
     Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.weight(1f)) {
       Text("Call detection assistance", style = MaterialTheme.typography.titleMedium)
       Text("Call alert only. Opens the microphone recorder when you tap the notification. No automatic recording or two-sided call capture.", color = muted)
      }
      Switch(checked = callAssist, onCheckedChange = { enabled ->
       if (!enabled) {
        callAssist = false
        settings.edit().putBoolean("call_assist_enabled", false).apply()
       } else {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
         callAssist = true
         settings.edit().putBoolean("call_assist_enabled", true).apply()
        } else phonePermission.launch(if (Build.VERSION.SDK_INT >= 33)
         arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS)
         else arrayOf(Manifest.permission.READ_PHONE_STATE))
       }
      })
     }
     InfoCard("Recording", "Foreground microphone recording with pause/resume. Calls are not intercepted.")
     Text("Experimental audio source", style = MaterialTheme.typography.titleMedium)
     Text("Choose a source for manual recordings. Android may return only your voice, silence, or deny capture during calls.", color = muted)
     CaptureSource.entries.forEach { source ->
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
       RadioButton(selected = selectedCapture == source, onClick = { selectedCapture = source; capturePrefs.select(source) })
       Text(source.label)
      }
     }
     InfoCard("Device compatibility", capturePrefs.diagnostic(context))
     val deviceProfile = remember { DeviceCompatibility.detect() }
     InfoCard("Detected phone family", deviceProfile.label + " • " + deviceProfile.route.name.replace('_', ' '))
     val dialer = remember { DialerInspector.inspect(context) }
     InfoCard("Default calling app", dialer.label + "\n" + dialer.advice)
     InfoCard("Recommended recording setup", deviceProfile.instructions)
     InfoCard("Device limitations", deviceProfile.caveat)
     Text("This profile does not verify two-sided audio. Test a real call and check both voices.", color = muted)
     OutlinedButton(onClick = { GenReportClipboard.copy(context, CompatibilityReport.create(context)); error = "Compatibility report copied." }, modifier = Modifier.fillMaxWidth()) { Text("Copy compatibility report") }
     Text("Recording folder connector", style = MaterialTheme.typography.titleMedium)
     Text(if (folderConnected) "Folder linked. Select Sync to import new recordings." else "Connect a recording folder exposed by your phone or recorder app. This does not capture calls.", color = muted)
     Button(onClick = { selectRecordingFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text(if (folderConnected) "Change recording folder" else "Connect recording folder") }
     if (folderConnected) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
       Column(Modifier.weight(1f)) {
        Text("Automatically import new recordings", style = MaterialTheme.typography.titleMedium)
        Text("Check the linked recording folder periodically in the background, approximately every 15 minutes or later if Android delays it.", color = muted)
       }
       Switch(checked = autoImport, onCheckedChange = { enabled ->
        autoImport = enabled
        settings.edit().putBoolean("auto_import_enabled", enabled).apply()
        if (enabled) AutoImportScheduler.enable(context) else AutoImportScheduler.disable(context)
       })
      }
      OutlinedButton(onClick = {
       try { val result = connector.sync(); refresh(); error = "Imported ${result.imported} new files; skipped ${result.alreadySeen} previously imported." + (result.warning?.let { " $it" } ?: "") }
       catch (e: Exception) { error = "Sync failed: ${e.message}" }
      }, modifier = Modifier.fillMaxWidth()) { Text("Sync folder now") }
      TextButton(onClick = { AutoImportScheduler.disable(context); autoImport = false; settings.edit().putBoolean("auto_import_enabled", false).apply(); connector.disconnect(); folderConnected = false }) { Text("Disconnect folder") }
     }
     InfoCard("Security", "App lock uses your phone biometrics or device PIN. Individual recording encryption is still pending.")
     Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.weight(1f)) { Text("Require device authentication"); Text("Unlock ProofVault with biometrics or screen lock", color = muted) }
      Switch(checked = lockEnabled, enabled = AppLock.available(context), onCheckedChange = onEnableLock)
     }
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
private fun EvidenceItem(file: File, playing: Boolean, notes: EvidenceNotes, ledger: EvidenceLedger, onArchive: () -> Unit, onPlay: () -> Unit, onDelete: () -> Unit) {
 var editNotes by remember { mutableStateOf(false) }
 var noteText by remember { mutableStateOf(notes.get(file)) }
 var confirmDelete by remember { mutableStateOf(false) }
 ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
  Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
   Icon(Icons.Default.GraphicEq, null, tint = teal)
   Spacer(Modifier.width(10.dp))
   Column(Modifier.weight(1f)) {
    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    val integrityStatus = remember(file.name, file.lastModified(), file.length()) { ledger.status(file) }
    val audioQuality = remember(file.name, file.lastModified(), file.length()) { RecordingQuality.inspect(file) }
    Text("Integrity: $integrityStatus", style = MaterialTheme.typography.labelSmall, color = if (integrityStatus == EvidenceLedger.Status.CHANGED) Color.Red else muted)
    Text(audioQuality.message, style = MaterialTheme.typography.labelSmall, color = if (audioQuality.playable) muted else Color.Red)
    if (notes.get(file).isNotBlank()) Text(notes.get(file), maxLines = 1, overflow = TextOverflow.Ellipsis, color = teal, style = MaterialTheme.typography.bodySmall)
    Text("${DateFormat.getDateTimeInstance().format(Date(file.lastModified()))} • ${file.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = muted)
   }
   IconButton(onClick = onPlay) {
    Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = if (playing) "Stop" else "Play", tint = teal)
   }
   IconButton(onClick = onArchive) { Icon(Icons.Default.Lock, "Encrypt and archive", tint = teal) }
   IconButton(onClick = { noteText = notes.get(file); editNotes = true }) { Icon(Icons.Default.EditNote, "Edit evidence notes", tint = teal) }
   IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = muted) }
  }
 }
 if (editNotes) AlertDialog(onDismissRequest = { editNotes = false }, title = { Text("Evidence notes & tags") }, text = { OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Notes / searchable keywords") }, minLines = 3) }, confirmButton = { TextButton(onClick = { notes.set(file, noteText); editNotes = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editNotes = false }) { Text("Cancel") } })
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
