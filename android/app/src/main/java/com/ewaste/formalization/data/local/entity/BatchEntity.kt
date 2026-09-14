package com.ewaste.formalization.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lifecycle Handover States for a Digital Material Passport Batch.
 * Follows a strict monotonic progression:
 * COLLECTED (1) -> IN_TRANSIT (2) -> HANDED_OVER (3) -> VERIFIED_BY_RECYCLER (4)
 */
enum class HandoverStatus(val rank: Int) {
    COLLECTED(1),
    IN_TRANSIT(2),
    HANDED_OVER(3),
    VERIFIED_BY_RECYCLER(4);

    companion object {
        fun fromString(value: String?): HandoverStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: COLLECTED
    }
}

/**
 * Offline-First Synchronization Status.
 */
enum class SyncStatus {
    LOCAL_ONLY,    // Newly captured offline, not yet enqueued or ready
    PENDING_SYNC,  // Enqueued for upload via WorkManager
    SYNCED,        // Successfully acknowledged by backend
    SYNC_FAILED    // Retry scheduled via exponential backoff
}

/**
 * Core Digital Material Passport Entity.
 * Represents a digitized e-waste item/batch collected by an informal collector.
 * Designed to satisfy CPCB (Central Pollution Control Board) traceability under
 * India's E-Waste (Management) Rules, 2022.
 */
@Entity(
    tableName = "batches",
    foreignKeys = [
        ForeignKey(
            entity = CollectorEntity::class,
            parentColumns = ["collectorId"],
            childColumns = ["collectorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["collectorId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["handoverStatus"])
    ]
)
data class BatchEntity(
    @PrimaryKey
    val batchId: String,                       // UUID generated at collection time
    val collectorId: String,                   // Kabadiwala ID
    val itemCategory: String,                  // Display name, e.g. "Smartphone / Feature Phone"
    val cpcbCategoryCode: String,              // Official CPCB code: "ITEW15", "CEEW1", "PCB", etc.
    val aiConfidence: Float,                   // 0.0 to 1.0 from on-device MobileNetV2
    val manualOverride: Boolean = false,       // True if user adjusted category manually
    val estimatedWeightKg: Double,             // Weight measured or estimated (kg)
    val itemCount: Int = 1,                    // Number of physical units in batch
    val latitude: Double,                      // GPS latitude at collection
    val longitude: Double,                     // GPS longitude at collection
    val locationAddress: String? = null,       // Approximate address / landmark
    val timestamp: Long = System.currentTimeMillis(), // Physical collection timestamp
    val handoverStatus: HandoverStatus = HandoverStatus.COLLECTED,
    val recyclerId: String? = null,            // Assigned upon handover to formal recycler
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val localImagePath: String? = null,        // Local device path to captured photo
    val qrPasscode: String,                    // Traceability code / QR token for recycler scan
    val recyclerVerificationHash: String? = null, // Cryptographic verification from recycler
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
