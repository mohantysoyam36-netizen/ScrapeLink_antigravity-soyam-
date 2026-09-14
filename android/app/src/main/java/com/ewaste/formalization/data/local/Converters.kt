package com.ewaste.formalization.data.local

import androidx.room.TypeConverter
import com.ewaste.formalization.data.local.entity.HandoverStatus
import com.ewaste.formalization.data.local.entity.SyncStatus

class Converters {
    @TypeConverter
    fun fromHandoverStatus(status: HandoverStatus?): String? = status?.name

    @TypeConverter
    fun toHandoverStatus(value: String?): HandoverStatus =
        HandoverStatus.fromString(value)

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String? = status?.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus =
        try {
            value?.let { SyncStatus.valueOf(it) } ?: SyncStatus.LOCAL_ONLY
        } catch (e: Exception) {
            SyncStatus.LOCAL_ONLY
        }
}
