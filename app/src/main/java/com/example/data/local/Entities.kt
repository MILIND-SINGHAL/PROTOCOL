package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Single Authority User Model Architecture (Requirement 21):
 *
 * Firebase UID (Identity Authority)
 *       ↓
 * UserProfile (Domain Model)
 *       ↓
 * Room Local Cache (UserProfileEntity)
 *
 * Subscription Authority: RevenueCat Entitlement (CustomerInfo.entitlements["pro"]?.isActive)
 * Role Authority: Server-Controlled (Client strictly clamped to "Member")
 *
 * Eliminates duplicate authorities (RegisteredUserEntity / registered_users).
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val firebaseUid: String? = null,
    val wakeTime: String = "06:30",
    val focus: String = "Deep Sleep",
    val wearable: String = "None",
    val themeMode: String = "dark",
    val isPro: Boolean = false,
    val subscriptionPlan: String = "free",
    val hasCompletedBaseline: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
    val isEmailVerified: Boolean = false,
    val accountRole: String = "Member",
    val sessionExpiry: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val streakDays: Int = 0,
    val caffeineDelayMinutes: Int = 90,
    val lightWindowMinutes: Int = 60,
    val windDownHours: Int = 14
)

@Entity(
    tableName = "protocol_completions",
    indices = [Index(value = ["dateKey", "itemId"], unique = true)]
)
data class ProtocolCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateKey: String,
    val itemId: String,
    val isCompleted: Boolean,
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val message: String,
    val tag: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "notification_settings")
data class NotificationSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val pushEnabled: Boolean = true,
    val caffeineAlert: Boolean = true,
    val luxAlert: Boolean = true,
    val windDownAlert: Boolean = true,
    val channelId: String = "protocol_circadian_channel",
    val notificationId: String = "",
    val lastCampaignSent: String? = null
)

