package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtocolDao {

    // User Profile
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfileFlow(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET isPro = :isPro, subscriptionPlan = :plan WHERE id = 1")
    suspend fun updateSubscriptionStatus(isPro: Boolean, plan: String)

    @Query("UPDATE user_profile SET themeMode = :mode WHERE id = 1")
    suspend fun updateThemeMode(mode: String)

    @Query("UPDATE user_profile SET wakeTime = :wakeTime, focus = :focus, wearable = :wearable, hasCompletedBaseline = 1 WHERE id = 1")
    suspend fun updateBaseline(wakeTime: String, focus: String, wearable: String)

    @Query("UPDATE user_profile SET focus = :focus WHERE id = 1")
    suspend fun updateFocus(focus: String)

    @Query("UPDATE user_profile SET wakeTime = :wakeTime WHERE id = 1")
    suspend fun updateWakeTime(wakeTime: String)

    @Query("UPDATE user_profile SET wearable = :wearable WHERE id = 1")
    suspend fun updateWearable(wearable: String)

    @Query("UPDATE user_profile SET caffeineDelayMinutes = :caffeineDelay, lightWindowMinutes = :lightWindow, windDownHours = :windDown WHERE id = 1")
    suspend fun updateCircadianOffsets(caffeineDelay: Int, lightWindow: Int, windDown: Int)

    @Query("UPDATE user_profile SET email = :email, isEmailVerified = :verified WHERE id = 1")
    suspend fun updateEmailAuth(email: String?, verified: Boolean)

    @Query("UPDATE user_profile SET isEmailVerified = :verified WHERE id = 1")
    suspend fun updateEmailVerified(verified: Boolean)

    @Query("DELETE FROM user_profile")
    suspend fun deleteUserProfile()

    // Daily completions
    @Query("SELECT * FROM protocol_completions WHERE dateKey = :dateKey")
    fun getCompletionsForDateFlow(dateKey: String): Flow<List<ProtocolCompletionEntity>>

    @Query("SELECT * FROM protocol_completions WHERE dateKey = :dateKey")
    suspend fun getCompletionsForDate(dateKey: String): List<ProtocolCompletionEntity>

    @Query("SELECT DISTINCT dateKey FROM protocol_completions WHERE isCompleted = 1")
    fun getAllCompletedDateKeysFlow(): Flow<List<String>>

    @Query("SELECT * FROM protocol_completions WHERE isCompleted = 1")
    fun getAllCompletedCompletionsFlow(): Flow<List<ProtocolCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCompletion(completion: ProtocolCompletionEntity)

    @Query("DELETE FROM protocol_completions WHERE dateKey = :dateKey AND itemId = :itemId")
    suspend fun removeCompletion(dateKey: String, itemId: String)

    @Query("DELETE FROM protocol_completions")
    suspend fun clearAllCompletions()

    // Notification Logs
    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    fun getNotificationLogsFlow(): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    suspend fun getNotificationLogs(): List<NotificationLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(log: NotificationLogEntity)

    @Query("UPDATE notification_logs SET isRead = 1")
    suspend fun markAllNotificationsAsRead()

    @Query("DELETE FROM notification_logs")
    suspend fun clearNotifications()

    // OneSignal Settings
    @Query("SELECT * FROM onesignal_settings WHERE id = 1")
    fun getOneSignalSettingsFlow(): Flow<OneSignalSettingsEntity?>

    @Query("SELECT * FROM onesignal_settings WHERE id = 1")
    suspend fun getOneSignalSettings(): OneSignalSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOneSignalSettings(settings: OneSignalSettingsEntity)
}
