package com.ewaste.formalization.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Authorized Recycler Entity.
 * Registered with CPCB (Central Pollution Control Board) under E-Waste Rules 2022.
 */
@Entity(tableName = "recyclers")
data class RecyclerEntity(
    @PrimaryKey
    val recyclerId: String,                   // Formal ID or GSTIN/CPCB registration
    val companyName: String,                  // e.g. "Attero Recycling Pvt Ltd"
    val cpcbRegistrationNumber: String,       // e.g. "CPCB/EW-REC/2023/DEL-014"
    val facilityAddress: String,              // Authorized processing site
    val authorizedCategories: String,         // Comma-separated: "ITEW15,CEEW1,PCB,BATT_LII"
    val contactPhone: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isEprCertified: Boolean = true
)
