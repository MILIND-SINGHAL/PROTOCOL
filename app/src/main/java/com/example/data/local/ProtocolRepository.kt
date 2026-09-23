package com.example.data.local

import com.example.data.adaptive.AdaptiveEngine
import com.example.data.adaptive.AdaptiveProtocolState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ProtocolRepository(private val dao: ProtocolDao) {

    val userProfileFlow: Flow<UserProfileEntity?> = dao.getUserProfileFlow()

    fun getTodayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getYesterdayKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(cal.time)
    }

    fun getRecentDaysKeys(count: Int = 7): List<String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val list = mutableListOf<String>()
        for (i in (count - 1) downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            list.add(sdf.format(cal.time))
        }
        return list
    }

    fun getCompletionsForTodayFlow(): Flow<Set<String>> {
        val today = getTodayKey()
        return dao.getCompletionsForDateFlow(today).map { list ->
            list.filter { it.isCompleted }.map { it.itemId }.toSet()
        }
    }

    val notificationLogsFlow: Flow<List<NotificationLogEntity>> = dao.getNotificationLogsFlow()
    val oneSignalSettingsFlow: Flow<OneSignalSettingsEntity?> = dao.getOneSignalSettingsFlow()
    val allCompletedDatesFlow: Flow<List<String>> = dao.getAllCompletedDateKeysFlow()
    val allCompletedCompletionsFlow: Flow<List<ProtocolCompletionEntity>> = dao.getAllCompletedCompletionsFlow()

    // Requirement 17: Streak day = daily adherence >= 80%
    val realStreakFlow: Flow<Int> = combine(
        allCompletedCompletionsFlow,
        userProfileFlow
    ) { completions, profile ->
        val requiredItems = AdaptiveEngine.getRequiredItemsForTrack(profile?.focus)
        val completionsByDate = completions
            .filter { it.isCompleted }
            .groupBy { it.dateKey }
            .mapValues { entry -> entry.value.map { it.itemId }.toSet() }

        AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
    }

    // Requirement 18: Closed-Loop Adaptive Protocol Engine Flow
    val adaptiveProtocolStateFlow: Flow<AdaptiveProtocolState> = combine(
        allCompletedCompletionsFlow,
        userProfileFlow
    ) { completions, profile ->
        val yesterday = getYesterdayKey()
        val requiredItems = AdaptiveEngine.getRequiredItemsForTrack(profile?.focus)
        val yesterdayCompleted = completions
            .filter { it.dateKey == yesterday && it.isCompleted }
            .map { it.itemId }
            .toSet()

        val yesterdaySummary = AdaptiveEngine.summarizeDay(
            dateKey = yesterday,
            completedItemIds = yesterdayCompleted,
            requiredItems = requiredItems
        )

        AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            activeFocus = profile?.focus ?: "Deep Sleep"
        )
    }

    fun calculateStreakFromDates(completedDates: List<String>): Int {
        if (completedDates.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val set = completedDates.toSet()

        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)

        var streak = 0
        val checkCal = Calendar.getInstance()

        if (set.contains(todayStr)) {
            streak++
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = sdf.format(checkCal.time)
            if (!set.contains(yesterdayStr)) {
                return 0
            }
        }

        while (true) {
            val dateStr = sdf.format(checkCal.time)
            if (set.contains(dateStr)) {
                streak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        val existing = dao.getUserProfile()
        if (existing == null) {
            dao.insertOrUpdateUserProfile(
                UserProfileEntity(
                    id = 1,
                    wakeTime = "06:30",
                    focus = "Circadian Alignment",
                    wearable = "None",
                    themeMode = "dark",
                    isPro = false,
                    subscriptionPlan = "free",
                    hasCompletedBaseline = false,
                    email = null,
                    isEmailVerified = false,
                    accountRole = "Member",
                    streakDays = 0
                )
            )
        }

        if (dao.getOneSignalSettings() == null) {
            dao.saveOneSignalSettings(OneSignalSettingsEntity())
        }
    }

    suspend fun getUserProfile(): UserProfileEntity? = withContext(Dispatchers.IO) {
        dao.getUserProfile()
    }

    suspend fun saveBaseline(wakeTime: String, focus: String, wearable: String) = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile() ?: UserProfileEntity()
        dao.insertOrUpdateUserProfile(
            current.copy(
                wakeTime = wakeTime,
                focus = focus,
                wearable = wearable,
                hasCompletedBaseline = true
            )
        )
    }

    suspend fun updateFocus(focus: String) = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile()
        if (current != null) {
            dao.updateFocus(focus)
        } else {
            dao.insertOrUpdateUserProfile(UserProfileEntity(focus = focus))
        }
    }

    suspend fun updateWearable(wearable: String) = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile()
        if (current != null) {
            dao.updateWearable(wearable)
        } else {
            dao.insertOrUpdateUserProfile(UserProfileEntity(wearable = wearable))
        }
    }

    suspend fun updateWakeTime(wakeTime: String) = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile()
        if (current != null) {
            dao.updateWakeTime(wakeTime)
        } else {
            dao.insertOrUpdateUserProfile(UserProfileEntity(wakeTime = wakeTime))
        }
    }

    suspend fun updateCircadianOffsets(caffeineDelay: Int, lightWindow: Int, windDown: Int) = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile()
        if (current != null) {
            dao.updateCircadianOffsets(caffeineDelay, lightWindow, windDown)
        } else {
            dao.insertOrUpdateUserProfile(
                UserProfileEntity(
                    caffeineDelayMinutes = caffeineDelay,
                    lightWindowMinutes = lightWindow,
                    windDownHours = windDown
                )
            )
        }
    }

    suspend fun updateTheme(mode: String) = withContext(Dispatchers.IO) {
        dao.updateThemeMode(mode)
    }

    suspend fun setSubscription(isPro: Boolean, plan: String) = withContext(Dispatchers.IO) {
        dao.updateSubscriptionStatus(isPro, plan)
    }

    suspend fun toggleItem(itemId: String) = withContext(Dispatchers.IO) {
        val today = getTodayKey()
        val existing = dao.getCompletionsForDate(today).find { it.itemId == itemId }
        val nextCompleted = !(existing?.isCompleted ?: false)
        dao.insertOrUpdateCompletion(
            ProtocolCompletionEntity(
                id = existing?.id ?: 0L,
                dateKey = today,
                itemId = itemId,
                isCompleted = nextCompleted,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun resetBaseline() = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile() ?: UserProfileEntity()
        dao.insertOrUpdateUserProfile(
            current.copy(
                hasCompletedBaseline = false,
                isPro = false,
                wakeTime = "06:30",
                focus = "Deep Sleep",
                wearable = "None"
            )
        )
        dao.clearAllCompletions()
    }

    suspend fun updateEmailVerificationStatus(isVerified: Boolean) = withContext(Dispatchers.IO) {
        dao.updateEmailVerified(isVerified)
    }

    suspend fun saveOneSignalSettings(settings: OneSignalSettingsEntity) = withContext(Dispatchers.IO) {
        dao.saveOneSignalSettings(settings)
    }

    suspend fun addNotification(title: String, message: String, tag: String) = withContext(Dispatchers.IO) {
        dao.insertNotificationLog(
            NotificationLogEntity(
                title = title,
                message = message,
                tag = tag,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
        )
    }

    suspend fun markAllNotificationsRead() = withContext(Dispatchers.IO) {
        dao.markAllNotificationsAsRead()
    }

    suspend fun exportDataAsJson(): String = withContext(Dispatchers.IO) {
        val profile = dao.getUserProfile()
        val today = getTodayKey()
        val completions = dao.getCompletionsForDate(today)
        val notifications = dao.getNotificationLogs()
        """
        {
          "export_timestamp": ${System.currentTimeMillis()},
          "app": "Protocol Circadian OS",
          "user_profile": {
            "wake_time": "${profile?.wakeTime ?: "06:30"}",
            "focus": "${profile?.focus ?: "Deep Sleep"}",
            "wearable": "${profile?.wearable ?: "None"}",
            "is_pro": ${profile?.isPro ?: false},
            "plan": "${profile?.subscriptionPlan ?: "annual"}",
            "email": "${profile?.email ?: "anonymous"}"
          },
          "today_completions": [
            ${completions.joinToString(",") { """{"item": "${it.itemId}", "completed": ${it.isCompleted}}""" }}
          ],
          "notification_logs_count": ${notifications.size}
        }
        """.trimIndent()
    }

    suspend fun wipeAllUserData() = withContext(Dispatchers.IO) {
        dao.clearAllCompletions()
        dao.clearNotifications()
        val current = dao.getUserProfile() ?: UserProfileEntity()
        dao.insertOrUpdateUserProfile(
            current.copy(
                firebaseUid = null,
                hasCompletedBaseline = false,
                isPro = false,
                subscriptionPlan = "free",
                email = null,
                displayName = null,
                isEmailVerified = false,
                accountRole = "Member",
                wakeTime = "06:30",
                focus = "Circadian Alignment",
                wearable = "None",
                streakDays = 0
            )
        )
    }

    suspend fun setActiveUserSession(
        firebaseUid: String?,
        email: String?,
        displayName: String?,
        isEmailVerified: Boolean,
        role: String = "Member"
    ) = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile() ?: UserProfileEntity()
        val safeRole = com.example.data.security.SecurityIntegrityManager.sanitizeAccountRole(role)
        dao.insertOrUpdateUserProfile(
            current.copy(
                firebaseUid = firebaseUid,
                email = if (email.isNullOrBlank()) null else email.trim(),
                displayName = if (displayName.isNullOrBlank()) null else displayName.trim(),
                isEmailVerified = isEmailVerified,
                accountRole = safeRole
            )
        )
    }

    suspend fun clearActiveUserSession() = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile() ?: UserProfileEntity()
        dao.insertOrUpdateUserProfile(
            current.copy(
                firebaseUid = null,
                email = null,
                displayName = null,
                isEmailVerified = false,
                accountRole = "Member",
                isPro = false,
                subscriptionPlan = "free"
            )
        )
    }

    suspend fun updateSubscriptionStatus(isPro: Boolean, plan: String) = withContext(Dispatchers.IO) {
        dao.updateSubscriptionStatus(isPro, plan)
    }
}
