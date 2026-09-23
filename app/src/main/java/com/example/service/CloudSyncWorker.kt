package com.example.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.SusuDatabase
import com.example.data.repository.SusuRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

class CloudSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result = coroutineScope {
    if (FirebaseAuth.getInstance().currentUser == null) return@coroutineScope Result.success()
    val database = SusuDatabase.getDatabase(applicationContext, this)
    val repository = SusuRepository(database)
    try {
      repository.syncAllOfflineDataToCloud()
      for (group in repository.getAllGroupsOnce()) repository.syncPendingClaimsFromCloud(group.id)
      if (database.susuDao().getQueuedReceipts().isNotEmpty()) Result.retry() else Result.success()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      Result.retry()
    }
  }

  companion object {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "susu-periodic-sync", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
      )
    }

    fun enqueue(context: Context) {
      WorkManager.getInstance(context).enqueueUniqueWork(
        "susu-pending-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequestBuilder<CloudSyncWorker>().setConstraints(constraints).build()
      )
    }
  }
}
