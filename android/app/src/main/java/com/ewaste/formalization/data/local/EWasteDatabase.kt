package com.ewaste.formalization.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ewaste.formalization.data.local.dao.BatchDao
import com.ewaste.formalization.data.local.dao.CollectorDao
import com.ewaste.formalization.data.local.dao.HandoverEventDao
import com.ewaste.formalization.data.local.dao.RecyclerDao
import com.ewaste.formalization.data.local.entity.BatchEntity
import com.ewaste.formalization.data.local.entity.CollectorEntity
import com.ewaste.formalization.data.local.entity.HandoverEventEntity
import com.ewaste.formalization.data.local.entity.RecyclerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CollectorEntity::class,
        BatchEntity::class,
        RecyclerEntity::class,
        HandoverEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EWasteDatabase : RoomDatabase() {

    abstract fun batchDao(): BatchDao
    abstract fun collectorDao(): CollectorDao
    abstract fun recyclerDao(): RecyclerDao
    abstract fun handoverEventDao(): HandoverEventDao

    companion object {
        @Volatile
        private var INSTANCE: EWasteDatabase? = null

        fun getInstance(context: Context): EWasteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EWasteDatabase::class.java,
                    "ewaste_material_passport.db"
                )
                .addCallback(DatabaseCallback(context))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val database = getInstance(context)
                    seedInitialData(database)
                }
            }
        }

        private suspend fun seedInitialData(database: EWasteDatabase) {
            // Seed a default registered Kabadiwala identity
            val defaultCollector = CollectorEntity(
                collectorId = "KAB-DL-2024-001",
                fullName = "Ramesh Kumar (रमेश कुमार)",
                phoneNumber = "+91 98765 43210",
                operatingTerritory = "Seelampur Ward 4, East Delhi",
                isKycVerified = true,
                totalCollectedKg = 0.0,
                bankAccountOrUpiId = "ramesh.scrap@upi"
            )
            database.collectorDao().insertCollector(defaultCollector)

            // Seed Authorized CPCB Recyclers
            val recyclers = listOf(
                RecyclerEntity(
                    recyclerId = "REC-CPCB-001",
                    companyName = "Eco-Greens Formal Recycling Hub",
                    cpcbRegistrationNumber = "CPCB/EW-REC/2023/DL-0081",
                    facilityAddress = "Plot 12, Okhla Industrial Area Phase III, New Delhi - 110020",
                    authorizedCategories = "ITEW15,ITEW3,CEEW1,PCB,BATT_LII",
                    contactPhone = "+91 11 2681 4400",
                    latitude = 28.5355,
                    longitude = 77.2662,
                    isEprCertified = true
                ),
                RecyclerEntity(
                    recyclerId = "REC-CPCB-002",
                    companyName = "Bharat EPR Resource Recovery Ltd",
                    cpcbRegistrationNumber = "CPCB/EW-REC/2022/UP-0194",
                    facilityAddress = "Site IV, Sahibabad Industrial Area, Ghaziabad, UP - 201010",
                    authorizedCategories = "ITEW15,CEEW1,PCB,CBL_COP",
                    contactPhone = "+91 120 415 8890",
                    latitude = 28.6711,
                    longitude = 77.3421,
                    isEprCertified = true
                ),
                RecyclerEntity(
                    recyclerId = "REC-CPCB-003",
                    companyName = "Swachh Circular Metals & Electronics",
                    cpcbRegistrationNumber = "CPCB/EW-REC/2023/HR-0045",
                    facilityAddress = "Sector 37, Pace City II, Gurugram, Haryana - 122001",
                    authorizedCategories = "ITEW15,ITEW3,PCB,BATT_LII,CBL_COP",
                    contactPhone = "+91 124 498 7000",
                    latitude = 28.4322,
                    longitude = 77.0118,
                    isEprCertified = true
                )
            )
            database.recyclerDao().insertRecyclers(recyclers)
        }
    }
}
