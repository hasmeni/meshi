package com.meshi.code.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages ORDER BY timestampMs ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(msg: ChatMessageEntity)

    @Query(
        "UPDATE chat_messages SET deliveryState = :state " +
        "WHERE ackTag = :ackTag AND ackTag != 0"
    )
    suspend fun updateDeliveryState(ackTag: Long, state: String)

    @Query(
        "UPDATE chat_messages SET deliveryState = :state, rttMs = :rttMs " +
        "WHERE ackTag = :ackTag AND ackTag != 0"
    )
    suspend fun updateDeliveryStateAndRtt(ackTag: Long, state: String, rttMs: Int)

    @Query("SELECT * FROM chat_messages WHERE text LIKE '%' || :query || '%' ORDER BY timestampMs DESC LIMIT 200")
    suspend fun searchMessages(query: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
