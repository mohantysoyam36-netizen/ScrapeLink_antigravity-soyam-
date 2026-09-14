package com.ewaste.formalization.data.local.dao

import androidx.room.*
import com.ewaste.formalization.data.local.entity.RecyclerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecyclerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycler(recycler: RecyclerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecyclers(recyclers: List<RecyclerEntity>)

    @Query("SELECT * FROM recyclers ORDER BY companyName ASC")
    fun getAllRecyclersFlow(): Flow<List<RecyclerEntity>>

    @Query("SELECT * FROM recyclers ORDER BY companyName ASC")
    suspend fun getAllRecyclers(): List<RecyclerEntity>

    @Query("SELECT * FROM recyclers WHERE recyclerId = :recyclerId")
    suspend fun getRecyclerById(recyclerId: String): RecyclerEntity?
}
