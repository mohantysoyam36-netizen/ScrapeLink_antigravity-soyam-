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

    @Query("""
        UPDATE collectors 
        SET successfulHandovers = successfulHandovers + 1,
            rating = MIN(5.0, rating + :ratingDelta),
            incentivePoints = incentivePoints + :points,
            incentiveTier = CASE 
                WHEN (incentivePoints + :points) >= 600 THEN 'GOLD'
                WHEN (incentivePoints + :points) >= 250 THEN 'SILVER'
                ELSE 'BRONZE'
            END
        WHERE collectorId = :collectorId
    """)
    suspend fun recordSuccessfulHandover(
        collectorId: String,
        ratingDelta: Double = 0.1,
        points: Int = 50
    )
}
