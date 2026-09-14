package com.ewaste.formalization.data.local.dao

import androidx.room.*
import com.ewaste.formalization.data.local.entity.CollectorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollector(collector: CollectorEntity)

    @Query("SELECT * FROM collectors WHERE collectorId = :collectorId")
    suspend fun getCollectorById(collectorId: String): CollectorEntity?

    @Query("SELECT * FROM collectors LIMIT 1")
    fun getActiveCollectorFlow(): Flow<CollectorEntity?>

    @Query("SELECT * FROM collectors LIMIT 1")
    suspend fun getActiveCollector(): CollectorEntity?

    @Query("UPDATE collectors SET totalCollectedKg = totalCollectedKg + :additionalKg WHERE collectorId = :collectorId")
    suspend fun incrementCollectedWeight(collectorId: String, additionalKg: Double)
}
