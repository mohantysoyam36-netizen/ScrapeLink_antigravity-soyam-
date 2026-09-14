package com.ewaste.formalization.data.remote

data class BatchSyncDto(
    val batchId: String,
    val collectorId: String,
    val itemCategory: String,
    val cpcbCategoryCode: String,
    val aiConfidence: Float,
    val manualOverride: Boolean,
    val estimatedWeightKg: Double,
    val itemCount: Int,
    val latitude: Double,
    val longitude: Double,
    val locationAddress: String?,
    val timestamp: Long,
    val handoverStatus: String,
    val recyclerId: String?,
    val qrPasscode: String,
    val recyclerVerificationHash: String?
)

data class SyncBatchesRequest(
    val collectorId: String,
    val batches: List<BatchSyncDto>,
    val clientSyncTimestamp: Long = System.currentTimeMillis()
)

data class SyncBatchesResponse(
    val success: Boolean,
    val message: String,
    val syncedBatchIds: List<String>,
    val serverTimestamp: Long
)

data class RecyclerSyncDto(
    val recyclerId: String,
    val companyName: String,
    val cpcbRegistrationNumber: String,
    val facilityAddress: String,
    val authorizedCategories: String,
    val contactPhone: String,
    val latitude: Double,
    val longitude: Double,
    val isEprCertified: Boolean
)
