package com.meshi.code.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_records")
data class GameRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameType: String,       // "REVERSI" or "CHESS"
    val opponentId: String,
    val opponentName: String,
    val result: String,         // "WIN", "LOSS", "DRAW"
    val timestampMs: Long,
)

@Dao
interface GameRecordDao {
    @Insert
    suspend fun insert(record: GameRecordEntity)

    @Query("SELECT * FROM game_records ORDER BY timestampMs DESC LIMIT 100")
    fun observeAll(): Flow<List<GameRecordEntity>>

    @Query("SELECT * FROM game_records WHERE opponentId = :opponentId ORDER BY timestampMs DESC")
    fun observeForOpponent(opponentId: String): Flow<List<GameRecordEntity>>

    @Query("SELECT COUNT(*) FROM game_records WHERE opponentId = :opponentId AND result = 'WIN' AND gameType = :gameType")
    suspend fun countWins(opponentId: String, gameType: String): Int

    @Query("SELECT COUNT(*) FROM game_records WHERE opponentId = :opponentId AND result = 'LOSS' AND gameType = :gameType")
    suspend fun countLosses(opponentId: String, gameType: String): Int

    @Query("SELECT * FROM game_records ORDER BY timestampMs DESC")
    fun observeAllUnlimited(): Flow<List<GameRecordEntity>>

    @Query("DELETE FROM game_records")
    suspend fun deleteAll()
}
