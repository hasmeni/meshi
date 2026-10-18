package com.meshi.code.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatMessageEntity::class, ContactNicknameEntity::class, GameRecordEntity::class, SnrSampleEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class MeshiDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun contactNicknameDao(): ContactNicknameDao
    abstract fun gameRecordDao(): GameRecordDao
    abstract fun snrSampleDao(): SnrSampleDao

    companion object {
        @Volatile private var INSTANCE: MeshiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN pathLen INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contact_nicknames` " +
                    "(`pubKeyHex` TEXT NOT NULL, `nickname` TEXT NOT NULL, PRIMARY KEY(`pubKeyHex`))"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `game_records` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`gameType` TEXT NOT NULL, " +
                    "`opponentId` TEXT NOT NULL, " +
                    "`opponentName` TEXT NOT NULL, " +
                    "`result` TEXT NOT NULL, " +
                    "`timestampMs` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN rttMs INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `snr_samples` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`contactId` TEXT NOT NULL, " +
                    "`snrDb` REAL NOT NULL, " +
                    "`timestampMs` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE contact_nicknames ADD COLUMN isVerified INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE contact_nicknames ADD COLUMN tag TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE contact_nicknames ADD COLUMN lat REAL NOT NULL DEFAULT 0.0"
                )
                database.execSQL(
                    "ALTER TABLE contact_nicknames ADD COLUMN lon REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        fun getInstance(context: Context): MeshiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeshiDatabase::class.java,
                    "meshi.db",
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build().also { INSTANCE = it }
            }
    }
}
