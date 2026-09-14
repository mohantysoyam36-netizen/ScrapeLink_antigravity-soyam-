package com.ewaste.formalization.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ewaste.formalization.data.local.EWasteDatabase
import com.ewaste.formalization.data.local.entity.SyncStatus
import com.ewaste.formalization.data.remote.BatchSyncDto
import com.ewaste.formalization.data.remote.NetworkClient
import com.ewaste.formalization.data.remote.SyncBatchesRequest
import java.io.IOException

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "ewaste_periodic_sync_work"
        const val ONE_TIME_WORK_NAME = "ewaste_immediate_sync_work"
    }

    override suspend fun doWork(): Result {
        val database = EWasteDatabase.getInstance(applicationContext)
        val batchDao = database.batchDao()
        val collectorDao = database.collectorDao()

        Log.d(TAG, "Starting background sync job... (Attempt #$runAttemptCount)")

        val unsyncedBatches = batchDao.getUnsyncedBatches()
        if (unsyncedBatches.isEmpty()) {
            Log.d(TAG, "No unsynced batches found in Room. Sync complete.")
            return Result.success()
        }

        Log.d(TAG, "Found ${unsyncedBatches.size} batches pending sync.")

        val activeCollector = collectorDao.getActiveCollector()
        val collectorId = activeCollector?.collectorId ?: "KAB-DL-2024-001"

        // Map Room entities to remote DTOs
        val batchDtos = unsyncedBatches.map { batch ->
            BatchSyncDto(
                batchId = batch.batchId,
                collectorId = batch.collectorId,
                itemCategory = batch.itemCategory,
                cpcbCategoryCode = batch.cpcbCategoryCode,
                aiConfidence = batch.aiConfidence,
                manualOverride = batch.manualOverride,
                estimatedWeightKg = batch.estimatedWeightKg,
                itemCount = batch.itemCount,
                latitude = batch.latitude,
                longitude = batch.longitude,
                locationAddress = batch.locationAddress,
                timestamp = batch.timestamp,
                handoverStatus = batch.handoverStatus.name,
                recyclerId = batch.recyclerId,
                qrPasscode = batch.qrPasscode,
                recyclerVerificationHash = batch.recyclerVerificationHash
            )
        }

        val request = SyncBatchesRequest(
            collectorId = collectorId,
            batches = batchDtos
        )

        return try {
            val response = NetworkClient.apiService.syncBatches(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val acknowledgedIds = body.syncedBatchIds

                Log.d(TAG, "Server acknowledged ${acknowledgedIds.size} batches.")
                batchDao.markBatchesAsSynced(acknowledgedIds)
                Result.success()
            } else {
                Log.e(TAG, "Server responded with error code: ${response.code()}")
                // Mark as failed locally for UI awareness, retry via WorkManager backoff
                unsyncedBatches.forEach {
                    batchDao.updateSyncStatus(it.batchId, SyncStatus.SYNC_FAILED)
                }
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network connection error during sync: ${e.message}")
            unsyncedBatches.forEach {
                batchDao.updateSyncStatus(it.batchId, SyncStatus.SYNC_FAILED)
            }
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during batch sync: ${e.message}", e)
            Result.failure()
        }
    }
}
