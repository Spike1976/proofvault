package com.aintcloud.proofvault

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodically imports files from an explicitly user-authorized recording folder.
 * The phone's own recorder is responsible for capturing the call.
 * WorkManager work is best-effort, not real-time: Doze and battery policies may delay it.
 */
class AutoImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
 override suspend fun doWork(): Result {
  val connector = RecordingFolderConnector(applicationContext)
  if (!connector.isConnected()) return Result.success()
  return try {
   val result = connector.sync()
   if (result.warning != null) Result.retry() else Result.success()
  } catch (_: SecurityException) {
   Result.failure()
  } catch (_: Exception) {
   Result.retry()
  }
 }
}

object AutoImportScheduler {
 const val WORK_NAME = "proofvault_recording_auto_import"
 fun enable(context: Context) {
  val request = PeriodicWorkRequestBuilder<AutoImportWorker>(15, TimeUnit.MINUTES).build()
  WorkManager.getInstance(context).enqueueUniquePeriodicWork(
   WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
  )
 }
 fun disable(context: Context) {
  WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
 }
}
