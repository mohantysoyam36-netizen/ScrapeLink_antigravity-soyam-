package com.ewaste.formalization.data.local.dao

import androidx.room.*
import com.ewaste.formalization.data.local.entity.BatchEntity
import com.ewaste.formalization.data.local.entity.HandoverStatus
import com.ewaste.formalization.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatches(batches: List<BatchEntity>)

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Query("SELECT * FROM batches WHERE batchId = :batchId")
    suspend fun getBatchById(batchId: String): BatchEntity?

    @Query("SELECT * FROM batches ORDER BY timestamp DESC")
    fun getAllBatchesFlow(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches ORDER BY timestamp DESC")
    suspend fun getAllBatches(): List<BatchEntity>

    @Query("SELECT * FROM batches WHERE syncStatus != 'SYNCED' ORDER BY timestamp ASC")
    suspend fun getUnsyncedBatches(): List<BatchEntity>

    @Query("SELECT COUNT(*) FROM batches WHERE syncStatus != 'SYNCED'")
    fun getUnsyncedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM batches WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedCount(): Int

    @Query("UPDATE batches SET syncStatus = 'SYNCED', updatedAt = :updatedAt WHERE batchId IN (:batchIds)")
    suspend fun markBatchesAsSynced(batchIds: List<String>, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE batches SET syncStatus = :syncStatus, updatedAt = :updatedAt WHERE batchId = :batchId")
    suspend fun updateSyncStatus(batchId: String, syncStatus: SyncStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE batches SET handoverStatus = :status, recyclerId = :recyclerId, syncStatus = 'PENDING_SYNC', updatedAt = :updatedAt WHERE batchId = :batchId")
    suspend fun markHandedOver(batchId: String, status: HandoverStatus, recyclerId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT SUM(estimatedWeightKg) FROM batches")
    fun getTotalWeightCollectedFlow(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM batches")
    fun getTotalBatchesCountFlow(): Flow<Int>

    @Delete
    suspend fun deleteBatch(batch: BatchEntity)
}
