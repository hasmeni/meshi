package com.meshi.code.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "contact_nicknames")
data class ContactNicknameEntity(
    @PrimaryKey val pubKeyHex: String,
    val nickname: String,
    val isVerified: Boolean = false,
    val tag: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

@Dao
interface ContactNicknameDao {
    @Query("SELECT * FROM contact_nicknames")
    fun observeAll(): Flow<List<ContactNicknameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setNickname(entity: ContactNicknameEntity)

    @Query("DELETE FROM contact_nicknames WHERE pubKeyHex = :pubKeyHex")
    suspend fun deleteNickname(pubKeyHex: String)

    @Query("SELECT * FROM contact_nicknames WHERE pubKeyHex = :pubKeyHex")
    suspend fun get(pubKeyHex: String): ContactNicknameEntity?

    @Query("UPDATE contact_nicknames SET isVerified = :verified WHERE pubKeyHex = :pubKeyHex")
    suspend fun setVerified(pubKeyHex: String, verified: Boolean)

    @Query("UPDATE contact_nicknames SET tag = :tag WHERE pubKeyHex = :pubKeyHex")
    suspend fun setTag(pubKeyHex: String, tag: String)

    @Query("DELETE FROM contact_nicknames")
    suspend fun deleteAll()
}
