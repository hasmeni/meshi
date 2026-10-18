package com.meshi.code.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "snr_samples")
data class SnrSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: String,
    val snrDb: Float,
    val timestampMs: Long,
)

@Dao
interface SnrSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: SnrSampleEntity)

    @Query(
        "SELECT * FROM snr_samples WHERE contactId = :contactId " +
        "ORDER BY timestampMs DESC LIMIT :limit"
    )
    fun observeForContact(contactId: String, limit: Int = 30): Flow<List<SnrSampleEntity>>

    /** Keep only the newest N samples per contact to avoid unbounded growth. */
    @Query(
        "DELETE FROM snr_samples WHERE contactId = :contactId AND id NOT IN " +
        "(SELECT id FROM snr_samples WHERE contactId = :contactId ORDER BY timestampMs DESC LIMIT 100)"
    )
    suspend fun trimForContact(contactId: String)

    @Query("DELETE FROM snr_samples")
    suspend fun deleteAll()
}
