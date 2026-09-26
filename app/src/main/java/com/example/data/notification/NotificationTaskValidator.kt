package com.example.data.notification

import com.example.data.adaptive.AdaptiveEngine
import com.example.data.local.NotificationLogEntity
import com.example.data.local.ProtocolCompletionEntity
import com.example.data.local.ProtocolDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class NotificationValidationResult {
    data class Success(val taskId: String, val dateKey: String) : NotificationValidationResult()
    data class AlreadyCompleted(val taskId: String, val dateKey: String) : NotificationValidationResult()
    data class Rejected(val reasonCode: String, val message: String) : NotificationValidationResult()
}

/**
 * Strict multi-layer validation engine for notification task completions.
 *
 * Core Security & Integrity Hierarchy:
 * Notification Action
 *   ↓
 * 1. Validate Task ID (Allowlist: VALID_PROTOCOL_ITEM_IDS)
 *   ↓
 * 2. Validate Target Date (Reject stale/future date notifications)
 *   ↓
 * 3. Validate Current User / Session (Prevent cross-user impersonation or leakage)
 *   ↓
 * 4. Validate Today's Protocol (Must belong to user's active focus track for relevant date)
 *   ↓
 * 5. Validate Actionable & Completion State (Verify actionable item & prevent duplicate completions)
 *   ↓
 * Record Single Completion
 */
object NotificationTaskValidator {

    suspend fun validateAndComplete(
        dao: ProtocolDao,
        taskId: String?,
        taskTitle: String? = null,
        targetDateKey: String? = null,
        targetUserId: String? = null,
        currentDateKey: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    ): NotificationValidationResult {

        // Layer 1: Validate Task ID against global allowlist
        if (taskId.isNullOrBlank() || taskId !in ProtocolNotificationReceiver.VALID_PROTOCOL_ITEM_IDS) {
            val message = "Ignored completion request for unverified or invalid task ID '${taskId ?: "null"}'."
            dao.insertNotificationLog(
                NotificationLogEntity(
                    title = "Rejected Notification Action",
                    message = message,
                    tag = "action_rejected",
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
            )
            return NotificationValidationResult.Rejected("INVALID_TASK_ID", message)
        }

        // Layer 2: Validate Target Date (Reject stale notifications from previous/future days)
        if (!targetDateKey.isNullOrBlank() && targetDateKey != currentDateKey) {
            val message = "Stale notification for date '$targetDateKey' rejected. Current active date is '$currentDateKey'."
            dao.insertNotificationLog(
                NotificationLogEntity(
                    title = "Stale Notification Action",
                    message = message,
                    tag = "action_stale",
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
            )
            return NotificationValidationResult.Rejected("STALE_DATE", message)
        }

        // Layer 3: Validate Current User / Session
        val userProfile = dao.getUserProfile()
        val currentFirebaseUid = userProfile?.firebaseUid

        if (!targetUserId.isNullOrBlank()) {
            if (currentFirebaseUid != null) {
                // Device currently has an authenticated Firebase user
                if (targetUserId != currentFirebaseUid) {
                    val message = "Notification targeted user '$targetUserId' does not match active session '$currentFirebaseUid'."
                    dao.insertNotificationLog(
                        NotificationLogEntity(
                            title = "Rejected User Mismatch",
                            message = message,
                            tag = "action_user_mismatch",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                    return NotificationValidationResult.Rejected("USER_MISMATCH", message)
                }
            } else {
                // Device currently has no authenticated user (local/guest session)
                if (targetUserId != "local" && targetUserId != "guest") {
                    val message = "Notification targeted for authenticated user '$targetUserId' cannot be completed in unauthenticated/guest session."
                    dao.insertNotificationLog(
                        NotificationLogEntity(
                            title = "Rejected User Mismatch",
                            message = message,
                            tag = "action_user_mismatch",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                    return NotificationValidationResult.Rejected("USER_MISMATCH", message)
                }
            }
        }

        // Layer 4: Validate Today's Active Protocol Track
        val activeTrack = userProfile?.focus ?: "Deep Sleep"
        val activeProtocolItems = AdaptiveEngine.getRequiredItemsForTrack(activeTrack)

        if (taskId !in activeProtocolItems) {
            val message = "Task '$taskId' is recognized globally but does not belong to active protocol track '$activeTrack'."
            dao.insertNotificationLog(
                NotificationLogEntity(
                    title = "Rejected Notification Action",
                    message = message,
                    tag = "action_rejected",
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
            )
            return NotificationValidationResult.Rejected("NOT_IN_TODAY_PROTOCOL", message)
        }

        // Layer 5: Validate Actionable & Completion State
        val completions = dao.getCompletionsForDate(currentDateKey)
        val existingCompletion = completions.find { it.itemId == taskId }

        if (existingCompletion != null && existingCompletion.isCompleted) {
            // Task has already been completed today. Do NOT create duplicate completion records.
            dao.insertNotificationLog(
                NotificationLogEntity(
                    title = "Task Already Completed",
                    message = "Task '$taskId' was already completed for $currentDateKey. Duplicate completion suppressed.",
                    tag = "action_skipped",
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
            )
            return NotificationValidationResult.AlreadyCompleted(taskId, currentDateKey)
        }

        // All 5 validation layers passed: Record exactly one completion in Room
        dao.insertOrUpdateCompletion(
            ProtocolCompletionEntity(
                id = existingCompletion?.id ?: 0L,
                dateKey = currentDateKey,
                itemId = taskId,
                isCompleted = true,
                completedAt = System.currentTimeMillis()
            )
        )

        val displayTitle = taskTitle ?: taskId
        dao.insertNotificationLog(
            NotificationLogEntity(
                title = "Notification Action: $displayTitle",
                message = "Marked '$displayTitle' as complete directly from Android notification shade.",
                tag = "action_completed",
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
        )

        return NotificationValidationResult.Success(taskId, currentDateKey)
    }
}
