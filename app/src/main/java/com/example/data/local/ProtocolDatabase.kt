package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        ProtocolCompletionEntity::class,
        NotificationLogEntity::class,
        NotificationSettingsEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ProtocolDatabase : RoomDatabase() {

    abstract fun protocolDao(): ProtocolDao

    companion object {
        @Volatile
        private var INSTANCE: ProtocolDatabase? = null

        /**
         * Requirement 21: Eliminates redundant user authority tables.
         * The application architecture uses a single authority model:
         * Firebase UID (Auth Authority) -> UserProfileEntity (Room Local Cache) -> RevenueCat (Subscription) -> Server (Role)
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure duplicate registered_users authority table is dropped
                db.execSQL("DROP TABLE IF EXISTS `registered_users`")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `firebaseUid` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `displayName` TEXT DEFAULT NULL")
                db.execSQL("DROP TABLE IF EXISTS `registered_users`")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `caffeineDelayMinutes` INTEGER NOT NULL DEFAULT 90")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `lightWindowMinutes` INTEGER NOT NULL DEFAULT 60")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `windDownHours` INTEGER NOT NULL DEFAULT 14")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename onesignal_settings table to notification_settings if it exists
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notification_settings` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `pushEnabled` INTEGER NOT NULL,
                        `caffeineAlert` INTEGER NOT NULL,
                        `luxAlert` INTEGER NOT NULL,
                        `windDownAlert` INTEGER NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `notificationId` TEXT NOT NULL,
                        `lastCampaignSent` TEXT DEFAULT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO `notification_settings` SELECT 
                        `id`, `pushEnabled`, `caffeineAlert`, `luxAlert`, 
                        `windDownAlert`, `channelId`, `notificationId`, `lastCampaignSent` 
                    FROM `onesignal_settings`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `onesignal_settings`")
            }
        }

        val MIGRATION_5_4 = object : Migration(5, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `onesignal_settings` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `pushEnabled` INTEGER NOT NULL,
                        `caffeineAlert` INTEGER NOT NULL,
                        `luxAlert` INTEGER NOT NULL,
                        `windDownAlert` INTEGER NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `notificationId` TEXT NOT NULL,
                        `lastCampaignSent` TEXT DEFAULT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO `onesignal_settings` SELECT 
                        `id`, `pushEnabled`, `caffeineAlert`, `luxAlert`, 
                        `windDownAlert`, `channelId`, `notificationId`, `lastCampaignSent` 
                    FROM `notification_settings`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `notification_settings`")
            }
        }

        /**
         * Requirement 25: Deliberate reverse downgrade migrations to prevent data destruction.
         * Schema 4 -> Schema 3: Safely preserves user profile data while reverting offset columns.
         */
        val MIGRATION_4_3 = object : Migration(4, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_profile_temp` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `firebaseUid` TEXT DEFAULT NULL,
                        `wakeTime` TEXT NOT NULL,
                        `focus` TEXT NOT NULL,
                        `wearable` TEXT NOT NULL,
                        `themeMode` TEXT NOT NULL,
                        `isPro` INTEGER NOT NULL,
                        `subscriptionPlan` TEXT NOT NULL,
                        `hasCompletedBaseline` INTEGER NOT NULL,
                        `email` TEXT DEFAULT NULL,
                        `displayName` TEXT DEFAULT NULL,
                        `isEmailVerified` INTEGER NOT NULL,
                        `accountRole` TEXT NOT NULL,
                        `sessionExpiry` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `streakDays` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `user_profile_temp` SELECT 
                        `id`, `firebaseUid`, `wakeTime`, `focus`, `wearable`, `themeMode`, `isPro`, 
                        `subscriptionPlan`, `hasCompletedBaseline`, `email`, `displayName`, 
                        `isEmailVerified`, `accountRole`, `sessionExpiry`, `createdAt`, `streakDays` 
                    FROM `user_profile`
                """.trimIndent())
                db.execSQL("DROP TABLE `user_profile`")
                db.execSQL("ALTER TABLE `user_profile_temp` RENAME TO `user_profile`")
            }
        }

        /**
         * Schema 3 -> Schema 2: Reverts firebaseUid and displayName while preserving core profile.
         */
        val MIGRATION_3_2 = object : Migration(3, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_profile_temp` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `wakeTime` TEXT NOT NULL,
                        `focus` TEXT NOT NULL,
                        `wearable` TEXT NOT NULL,
                        `themeMode` TEXT NOT NULL,
                        `isPro` INTEGER NOT NULL,
                        `subscriptionPlan` TEXT NOT NULL,
                        `hasCompletedBaseline` INTEGER NOT NULL,
                        `email` TEXT DEFAULT NULL,
                        `isEmailVerified` INTEGER NOT NULL,
                        `accountRole` TEXT NOT NULL,
                        `sessionExpiry` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `streakDays` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `user_profile_temp` SELECT 
                        `id`, `wakeTime`, `focus`, `wearable`, `themeMode`, `isPro`, 
                        `subscriptionPlan`, `hasCompletedBaseline`, `email`, 
                        `isEmailVerified`, `accountRole`, `sessionExpiry`, `createdAt`, `streakDays` 
                    FROM `user_profile`
                """.trimIndent())
                db.execSQL("DROP TABLE `user_profile`")
                db.execSQL("ALTER TABLE `user_profile_temp` RENAME TO `user_profile`")
            }
        }

        /**
         * Schema 2 -> Schema 1: Reverts schema 2 changes cleanly.
         */
        val MIGRATION_2_1 = object : Migration(2, 1) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `registered_users`")
            }
        }

        fun getDatabase(context: Context): ProtocolDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    ProtocolDatabase::class.java,
                    "protocol_wellness.db"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_4, MIGRATION_4_3, MIGRATION_3_2, MIGRATION_2_1
                )

                // Requirement 25: Never destroy user database on downgrade in production.
                // Destructive downgrade fallback is strictly restricted to development/debug builds.
                if (com.example.BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigrationOnDowngrade(false)
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
