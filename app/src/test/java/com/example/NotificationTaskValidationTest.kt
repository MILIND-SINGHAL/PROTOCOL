package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.local.UserProfileEntity
import com.example.data.notification.NotificationTaskValidator
import com.example.data.notification.NotificationValidationResult
import com.example.data.notification.ProtocolNotificationReceiver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Test suite verifying notification task validation and completion integrity.
 *
 * Validates:
 * TEST 1: Valid task belonging to today's protocol -> completion succeeds.
 * TEST 2: Globally valid task ID but not present in today's protocol -> completion rejected.
 * TEST 3: Invalid/arbitrary task ID -> completion rejected.
 * TEST 4: SQL-injection-like task ID -> rejected safely.
 * TEST 5: Task belongs to another user's protocol -> completion rejected.
 * TEST 6: Yesterday's notification attempts to complete today's task -> stale action rejected.
 * TEST 7: Task already completed -> no duplicate completion.
 * TEST 8: SNOOZE action -> notification is rescheduled, completion count remains unchanged.
 * TEST 9: DISMISS action -> no completion record.
 * TEST 10: COMPLETE action -> exactly one completion is recorded.
 * TEST 11: Process recreation before notification action -> uses persistent Room data.
 * TEST 12: No authenticated user -> local guest rules followed and cannot impersonate authenticated account.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationTaskValidationTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository
    private lateinit var context: Context

    private val todayDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
        ProtocolNotificationReceiver.testDatabase = database
    }

    @After
    fun tearDown() {
        ProtocolNotificationReceiver.testDatabase = null
        database.close()
    }

    /**
     * TEST 1: Valid task belonging to today's protocol
     * -> completion succeeds.
     */
    @Test
    fun test1_ValidTaskInTodayProtocol_CompletionSucceeds() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Physical Recovery",
                firebaseUid = "user_123"
            )
        )

        // "rec_sun_mobility" is an actionable task in Physical Recovery track
        val result = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "rec_sun_mobility",
            taskTitle = "Morning Sunlight Mobility",
            targetDateKey = todayDateKey,
            targetUserId = "user_123",
            currentDateKey = todayDateKey
        )

        assertTrue("Completion must succeed for valid task in today's protocol", result is NotificationValidationResult.Success)
        val completions = dao.getCompletionsForDate(todayDateKey)
        assertEquals(1, completions.size)
        assertEquals("rec_sun_mobility", completions.first().itemId)
        assertTrue(completions.first().isCompleted)
    }

    /**
     * TEST 2: Globally valid task ID but not present in today's protocol
     * -> completion rejected.
     */
    @Test
    fun test2_GloballyValidTaskNotPresentInTodayProtocol_CompletionRejected() = runBlocking {
        val dao = database.protocolDao()
        // Active focus track is Physical Recovery
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Physical Recovery",
                firebaseUid = "user_123"
            )
        )

        // "sleep_sunlight" is globally valid in VALID_PROTOCOL_ITEM_IDS, but belongs to Deep Sleep track
        val result = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "sleep_sunlight",
            taskTitle = "Morning Sunlight",
            targetDateKey = todayDateKey,
            targetUserId = "user_123",
            currentDateKey = todayDateKey
        )

        assertTrue("Task not present in today's active protocol track must be rejected", result is NotificationValidationResult.Rejected)
        val rejected = result as NotificationValidationResult.Rejected
        assertEquals("NOT_IN_TODAY_PROTOCOL", rejected.reasonCode)

        val completions = dao.getCompletionsForDate(todayDateKey)
        assertEquals("No completion should be recorded for task outside active track", 0, completions.size)
    }

    /**
     * TEST 3: Invalid/arbitrary task ID
     * -> completion rejected.
     */
    @Test
    fun test3_InvalidArbitraryTaskId_CompletionRejected() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, focus = "Deep Sleep"))

        val result = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "arbitrary_fake_task_123",
            taskTitle = "Fake Task",
            targetDateKey = todayDateKey,
            targetUserId = null,
            currentDateKey = todayDateKey
        )

        assertTrue("Arbitrary task ID must be rejected", result is NotificationValidationResult.Rejected)
        val rejected = result as NotificationValidationResult.Rejected
        assertEquals("INVALID_TASK_ID", rejected.reasonCode)

        val completions = dao.getCompletionsForDate(todayDateKey)
        assertEquals(0, completions.size)
    }

    /**
     * TEST 4: SQL-injection-like task ID
     * -> rejected safely.
     */
    @Test
    fun test4_SqlInjectionLikeTaskId_RejectedSafely() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, focus = "Deep Sleep"))

        val injectionPayloads = listOf(
            "'; DROP TABLE protocol_completions; --",
            "' OR 1=1 --",
            "sleep_sunlight' OR '1'='1",
            "\" OR \"\"=\""
        )

        for (payload in injectionPayloads) {
            val result = NotificationTaskValidator.validateAndComplete(
                dao = dao,
                taskId = payload,
                taskTitle = "Malicious Payload",
                targetDateKey = todayDateKey,
                targetUserId = null,
                currentDateKey = todayDateKey
            )

            assertTrue("SQL injection payload must be rejected: $payload", result is NotificationValidationResult.Rejected)
        }

        // Verify table integrity: table still exists and query executes without error
        val completions = dao.getCompletionsForDate(todayDateKey)
        assertEquals(0, completions.size)
    }

    /**
     * TEST 5: Task belongs to another user's protocol
     * -> completion rejected.
     */
    @Test
    fun test5_TaskBelongsToAnotherUser_CompletionRejected() = runBlocking {
        val dao = database.protocolDao()
        // Active session is for user_alice
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Deep Sleep",
                firebaseUid = "user_alice"
            )
        )

        // Notification carries identity of user_bob
        val result = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "sleep_sunlight",
            taskTitle = "10 Min Morning Sunlight",
            targetDateKey = todayDateKey,
            targetUserId = "user_bob",
            currentDateKey = todayDateKey
        )

        assertTrue("Task targeting another user must be rejected", result is NotificationValidationResult.Rejected)
        val rejected = result as NotificationValidationResult.Rejected
        assertEquals("USER_MISMATCH", rejected.reasonCode)

        val completions = dao.getCompletionsForDate(todayDateKey)
        assertEquals(0, completions.size)
    }

    /**
     * TEST 6: Yesterday's notification attempts to complete today's task
     * -> stale action rejected.
     */
    @Test
    fun test6_YesterdayNotificationAttemptsToCompleteTodayTask_StaleActionRejected() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, focus = "Deep Sleep"))

        val yesterdayDateKey = "2026-09-23"

        // Notification intent has targetDateKey = yesterday
        val result = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "sleep_sunlight",
            taskTitle = "10 Min Morning Sunlight",
            targetDateKey = yesterdayDateKey,
            targetUserId = null,
            currentDateKey = todayDateKey // Today is 2026-09-24
        )

        assertTrue("Stale notification for yesterday must be rejected", result is NotificationValidationResult.Rejected)
        val rejected = result as NotificationValidationResult.Rejected
        assertEquals("STALE_DATE", rejected.reasonCode)

        val todayCompletions = dao.getCompletionsForDate(todayDateKey)
        assertEquals("No completions must be recorded for today", 0, todayCompletions.size)
        val yesterdayCompletions = dao.getCompletionsForDate(yesterdayDateKey)
        assertEquals("No completions must be recorded for yesterday", 0, yesterdayCompletions.size)
    }

    /**
     * TEST 7: Task already completed
     * -> no duplicate completion.
     */
    @Test
    fun test7_TaskAlreadyCompleted_NoDuplicateCompletion() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, focus = "Deep Sleep"))

        // First completion
        val firstResult = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "sleep_sunlight",
            taskTitle = "10 Min Morning Sunlight",
            targetDateKey = todayDateKey,
            targetUserId = null,
            currentDateKey = todayDateKey
        )
        assertTrue(firstResult is NotificationValidationResult.Success)

        val completionsAfterFirst = dao.getCompletionsForDate(todayDateKey)
        assertEquals(1, completionsAfterFirst.size)

        // Second completion attempt via notification
        val secondResult = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "sleep_sunlight",
            taskTitle = "10 Min Morning Sunlight",
            targetDateKey = todayDateKey,
            targetUserId = null,
            currentDateKey = todayDateKey
        )

        assertTrue("Repeated completion must return AlreadyCompleted", secondResult is NotificationValidationResult.AlreadyCompleted)
        val completionsAfterSecond = dao.getCompletionsForDate(todayDateKey)
        assertEquals("Completion count must remain exactly 1 (no duplicates)", 1, completionsAfterSecond.size)
    }

    /**
     * TEST 8: SNOOZE action
     * -> notification is rescheduled, completion count remains unchanged.
     */
    @Test
    fun test8_SnoozeAction_NotificationRescheduled_CompletionCountUnchanged() = runBlocking {
        val receiver = ProtocolNotificationReceiver()
        val intent = Intent(context, ProtocolNotificationReceiver::class.java).apply {
            action = ProtocolNotificationReceiver.ACTION_SNOOZE_TASK
            putExtra(ProtocolNotificationReceiver.EXTRA_TASK_ID, "sleep_sunlight")
            putExtra(ProtocolNotificationReceiver.EXTRA_TASK_TITLE, "Morning Sunlight")
            putExtra(ProtocolNotificationReceiver.EXTRA_NOTIFICATION_ID, 101)
            putExtra(ProtocolNotificationReceiver.EXTRA_DATE_KEY, todayDateKey)
        }

        receiver.handleIntent(context, intent)

        // Verify that NO completion was recorded
        val completions = database.protocolDao().getCompletionsForDate(todayDateKey)
        assertEquals("SNOOZE must NOT record any task completions", 0, completions.size)

        // Verify notification log captured snooze
        val logs = database.protocolDao().getNotificationLogs()
        assertTrue("Log should register snooze action", logs.any { it.tag == "action_snoozed" })
    }

    /**
     * TEST 9: DISMISS action
     * -> no completion record.
     */
    @Test
    fun test9_DismissAction_NoCompletionRecord() = runBlocking {
        val receiver = ProtocolNotificationReceiver()
        val intent = Intent(context, ProtocolNotificationReceiver::class.java).apply {
            action = ProtocolNotificationReceiver.ACTION_DISMISS_TASK
            putExtra(ProtocolNotificationReceiver.EXTRA_TASK_ID, "sleep_sunlight")
            putExtra(ProtocolNotificationReceiver.EXTRA_TASK_TITLE, "Morning Sunlight")
            putExtra(ProtocolNotificationReceiver.EXTRA_NOTIFICATION_ID, 102)
            putExtra(ProtocolNotificationReceiver.EXTRA_DATE_KEY, todayDateKey)
        }

        receiver.handleIntent(context, intent)

        // Verify that NO completion was recorded
        val completions = database.protocolDao().getCompletionsForDate(todayDateKey)
        assertEquals("DISMISS must NOT record any task completions", 0, completions.size)

        // Verify notification log captured dismissal
        val logs = database.protocolDao().getNotificationLogs()
        assertTrue("Log should register dismissal action", logs.any { it.tag == "action_dismissed" })
    }

    /**
     * TEST 10: COMPLETE action
     * -> exactly one completion is recorded.
     */
    @Test
    fun test10_CompleteAction_ExactlyOneCompletionRecorded() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, focus = "Deep Sleep", firebaseUid = "user_test"))

        val receiver = ProtocolNotificationReceiver()
        val intent = Intent(context, ProtocolNotificationReceiver::class.java).apply {
            action = ProtocolNotificationReceiver.ACTION_COMPLETE_TASK
            putExtra(ProtocolNotificationReceiver.EXTRA_TASK_ID, "sleep_sunlight")
            putExtra(ProtocolNotificationReceiver.EXTRA_TASK_TITLE, "Morning Sunlight")
            putExtra(ProtocolNotificationReceiver.EXTRA_NOTIFICATION_ID, 103)
            putExtra(ProtocolNotificationReceiver.EXTRA_DATE_KEY, todayDateKey)
            putExtra(ProtocolNotificationReceiver.EXTRA_USER_ID, "user_test")
        }

        // Send COMPLETE action
        receiver.handleIntent(context, intent)

        val completions = dao.getCompletionsForDate(todayDateKey)
        assertEquals("Exactly 1 completion must be recorded", 1, completions.size)
        assertEquals("sleep_sunlight", completions.first().itemId)
        assertTrue(completions.first().isCompleted)

        // Send COMPLETE action a second time
        receiver.handleIntent(context, intent)

        val completionsAfterDuplicate = dao.getCompletionsForDate(todayDateKey)
        assertEquals("Still exactly 1 completion after duplicate intent", 1, completionsAfterDuplicate.size)
    }

    /**
     * TEST 11: Process recreation before notification action
     * -> validation still uses persistent protocol data, not stale in-memory UI state.
     */
    @Test
    fun test11_ProcessRecreationBeforeNotificationAction_UsesPersistentProtocolData() = runBlocking {
        val dao = database.protocolDao()
        // Simulate initial process storing user profile with Mental Clarity
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Mental Clarity",
                firebaseUid = "user_persisted"
            )
        )

        // Simulate process recreation: fresh validator instance querying persistent Room DB
        val result = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "clarity_lux_splash",
            taskTitle = "Lux Splash",
            targetDateKey = todayDateKey,
            targetUserId = "user_persisted",
            currentDateKey = todayDateKey
        )

        assertTrue("Must succeed using persistent Room data after process restart", result is NotificationValidationResult.Success)
        val completions = dao.getCompletionsForDate(todayDateKey)
        assertEquals(1, completions.size)
        assertEquals("clarity_lux_splash", completions.first().itemId)

        // Verify that task from another track is rejected against the persistent data
        val otherTrackResult = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "rec_sun_mobility", // Physical Recovery task
            taskTitle = "Mobility",
            targetDateKey = todayDateKey,
            targetUserId = "user_persisted",
            currentDateKey = todayDateKey
        )
        assertTrue(otherTrackResult is NotificationValidationResult.Rejected)
    }

    /**
     * TEST 12: No authenticated user
     * -> notification completion follows local/guest rules and must not impersonate another account.
     */
    @Test
    fun test12_NoAuthenticatedUser_LocalGuestRulesFollowedAndNoImpersonation() = runBlocking {
        val dao = database.protocolDao()
        // No authenticated user: firebaseUid is null (Guest mode)
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Deep Sleep",
                firebaseUid = null
            )
        )

        // 1. Guest notification with targetUserId = "guest" or "local" succeeds
        val guestResult = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "sleep_sunlight",
            taskTitle = "Morning Sunlight",
            targetDateKey = todayDateKey,
            targetUserId = "guest",
            currentDateKey = todayDateKey
        )
        assertTrue("Guest notification action must succeed for local protocol", guestResult is NotificationValidationResult.Success)
        assertEquals(1, dao.getCompletionsForDate(todayDateKey).size)

        // 2. Notification carrying an authenticated UID attempts to run against guest session
        val impersonatingResult = NotificationTaskValidator.validateAndComplete(
            dao = dao,
            taskId = "sleep_delay_caffeine",
            taskTitle = "Delay Caffeine",
            targetDateKey = todayDateKey,
            targetUserId = "authenticated_user_999", // Attempting to complete on unauthenticated device
            currentDateKey = todayDateKey
        )
        assertTrue("Notification targeted for authenticated user must be rejected in guest session", impersonatingResult is NotificationValidationResult.Rejected)
        val rejected = impersonatingResult as NotificationValidationResult.Rejected
        assertEquals("USER_MISMATCH", rejected.reasonCode)
        assertEquals("No second completion recorded", 1, dao.getCompletionsForDate(todayDateKey).size)
    }

    /**
     * TEST 13: Shared Authoritative Completion Path
     * -> UI and notification actions share the exact same completion pipeline via repository.completeItemAuthoritatively().
     */
    @Test
    fun test13_UIAndNotificationShareAuthoritativeCompletionPath() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Deep Sleep",
                firebaseUid = "user_shared"
            )
        )

        // 1. UI completion via repository.toggleItem()
        repository.toggleItem("sleep_sunlight")
        val completions = dao.getCompletionsForDate(todayDateKey)
        assertEquals(1, completions.size)
        assertTrue(completions.first().isCompleted)

        // 2. Notification action attempts to complete the same item
        val notifResult = repository.completeItemAuthoritatively(
            taskId = "sleep_sunlight",
            targetDateKey = todayDateKey,
            targetUserId = "user_shared"
        )
        assertTrue("Notification completion recognizes already completed task", notifResult is NotificationValidationResult.AlreadyCompleted)
        assertEquals("Completions count remains exactly 1", 1, dao.getCompletionsForDate(todayDateKey).size)
    }

    /**
     * TEST 14: Account Switch Data Isolation
     * -> Old user's notification intent cannot mutate new user's active session or completions.
     */
    @Test
    fun test14_AccountSwitch_OldNotificationCannotMutateNewUserData() = runBlocking {
        val dao = database.protocolDao()

        // User A was logged in when notification was scheduled
        repository.setActiveUserSession(
            firebaseUid = "user_A_uid",
            email = "userA@protocol.app",
            displayName = "User A",
            isEmailVerified = true
        )

        val oldNotificationIntent = Intent(ProtocolNotificationReceiver.ACTION_COMPLETE_TASK).apply {
            putExtra(ProtocolNotificationReceiver.EXTRA_TASK_ID, "sleep_sunlight")
            putExtra(ProtocolNotificationReceiver.EXTRA_TASK_TITLE, "Morning Sunlight")
            putExtra(ProtocolNotificationReceiver.EXTRA_DATE_KEY, todayDateKey)
            putExtra(ProtocolNotificationReceiver.EXTRA_USER_ID, "user_A_uid")
        }

        // User A logs out and User B logs in
        repository.clearActiveUserSession()
        repository.clearSessionCompletions()

        repository.setActiveUserSession(
            firebaseUid = "user_B_uid",
            email = "userB@protocol.app",
            displayName = "User B",
            isEmailVerified = true
        )

        // Old User A notification is received
        val receiver = ProtocolNotificationReceiver()
        receiver.handleIntent(context, oldNotificationIntent)

        val userBCompletions = dao.getCompletionsForDate(todayDateKey)
        assertEquals("User B data must NOT be modified by User A notification", 0, userBCompletions.size)
    }

    /**
     * TEST 15: Notification completion directly feeds the authoritative Daily Adherence & Streak
     * -> Exactly matches UI completion calculations with no divergence.
     */
    @Test
    fun test15_NotificationCompletion_UpdatesDailyAdherenceAndStreakAuthoritatively() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Physical Recovery",
                firebaseUid = "user_adherence"
            )
        )

        val requiredTasks = com.example.data.adaptive.AdaptiveEngine.getRequiredItemsForTrack("Physical Recovery")
        assertEquals(8, requiredTasks.size)

        // Complete 7 out of 8 tasks via notification action
        val tasksToComplete = requiredTasks.take(7)
        tasksToComplete.forEach { taskId ->
            val result = repository.completeItemAuthoritatively(
                taskId = taskId,
                targetDateKey = todayDateKey,
                targetUserId = "user_adherence"
            )
            assertTrue(result is NotificationValidationResult.Success)
        }

        val completedSet = dao.getCompletionsForDate(todayDateKey).filter { it.isCompleted }.map { it.itemId }.toSet()
        assertEquals(7, completedSet.size)

        // Summarize day adherence through AdaptiveEngine
        val daySummary = com.example.data.adaptive.AdaptiveEngine.summarizeDay(
            dateKey = todayDateKey,
            completedItemIds = completedSet,
            requiredItems = requiredTasks
        )

        assertEquals(0.875f, daySummary.adherenceRatio)
        assertTrue("7/8 is 87.5% which qualifies for streak (>= 80%)", daySummary.isStreakQualified)
    }
}
