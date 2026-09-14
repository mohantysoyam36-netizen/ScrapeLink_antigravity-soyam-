package com.ewaste.formalization.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an informal scrap collector (Kabadiwala).
 * Captures formalization identity, operating territory, and KYC status
 * for inclusion in traceable EPR supply chain records.
 */
@Entity(tableName = "collectors")
data class CollectorEntity(
    @PrimaryKey
    val collectorId: String, // Unique identifier (e.g. phone-based UUID)
    val fullName: String,
    val phoneNumber: String,
    val operatingTerritory: String, // e.g. "Seelampur Market, East Delhi"
    val registrationTimestamp: Long = System.currentTimeMillis(),
    val isKycVerified: Boolean = false,
    val totalCollectedKg: Double = 0.0,
    val bankAccountOrUpiId: String? = null, // For direct formal digital payouts
    val rating: Double = 4.5, // Reputation rating (1.0 to 5.0)
    val successfulHandovers: Int = 0, // Number of batches successfully handed over to recyclers
    val incentivePoints: Int = 0, // Accrued incentive reward points
    val incentiveTier: String = "BRONZE" // BRONZE, SILVER, GOLD
)
