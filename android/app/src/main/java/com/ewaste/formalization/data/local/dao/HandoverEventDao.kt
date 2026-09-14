package com.ewaste.formalization.data.local.dao

import androidx.room.*
import com.ewaste.formalization.data.local.entity.HandoverEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HandoverEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: HandoverEventEntity)

    @Query("SELECT * FROM handover_events WHERE batchId = :batchId ORDER BY eventTimestamp DESC")
    fun getEventsForBatchFlow(batchId: String): Flow<List<HandoverEventEntity>>

    @Query("SELECT * FROM handover_events WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedEvents(): List<HandoverEventEntity>

    @Query("UPDATE handover_events SET syncStatus = 'SYNCED' WHERE eventId IN (:eventIds)")
    suspend fun markEventsAsSynced(eventIds: List<String>)
}
