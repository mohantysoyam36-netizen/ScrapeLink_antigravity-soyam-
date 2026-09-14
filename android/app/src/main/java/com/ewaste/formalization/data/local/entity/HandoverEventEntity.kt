package com.ewaste.formalization.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Handover Event audit log.
 * Records state transitions, location stamps, and physical custody transfers.
 */
@Entity(
    tableName = "handover_events",
    foreignKeys = [
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["batchId"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["batchId"])]
)
data class HandoverEventEntity(
    @PrimaryKey
    val eventId: String,                      // UUID
    val batchId: String,
    val collectorId: String,
    val recyclerId: String?,
    val previousStatus: HandoverStatus,
    val newStatus: HandoverStatus,
    val eventTimestamp: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val verificationNotes: String? = null,
    val verifiedWeightKg: Double? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
