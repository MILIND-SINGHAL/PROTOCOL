package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProtocolRepositoryTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun getTodayCompletedSet(): Set<String> {
        val today = repository.getTodayKey()
        return database.protocolDao().getCompletionsForDate(today)
            .filter { it.isCompleted }
            .map { it.itemId }
            .toSet()
    }

    @Test
    fun testBaselineInitializationAndProfileDefaults() = runBlocking {
        repository.ensureInitialized()
        val profile = repository.getUserProfile()
        assertNotNull("User profile should be initialized", profile)
        assertEquals("06:30", profile?.wakeTime)
        assertEquals("Circadian Alignment", profile?.focus)
        assertEquals("dark", profile?.themeMode)
        assertFalse(profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)
    }

    @Test
    fun testToggleItemCompletionAndTodayRetrieval() = runBlocking {
        repository.ensureInitialized()
        val itemId = "sleep_sunlight"

        // Initially not completed
        val initialToday = getTodayCompletedSet()
        assertFalse(initialToday.contains(itemId))

        // Toggle to true
        repository.toggleItem(itemId)
        val afterFirstToggle = getTodayCompletedSet()
        assertTrue("Item should be marked completed", afterFirstToggle.contains(itemId))

        // Toggle back to false
        repository.toggleItem(itemId)
        val afterSecondToggle = getTodayCompletedSet()
        assertFalse("Item should be uncompleted", afterSecondToggle.contains(itemId))
    }

    @Test
    fun testDataExportAsJson() = runBlocking {
        repository.ensureInitialized()
        repository.toggleItem("sleep_nsdr")
        val json = repository.exportDataAsJson()

        assertNotNull(json)
        assertTrue(json.contains("Protocol Circadian OS"))
        assertTrue(json.contains("user_profile"))
        assertTrue(json.contains("today_completions"))
        assertTrue(json.contains("sleep_nsdr"))
    }

    @Test
    fun testWipeAllUserData_ClearsCompletionsNotificationsAndResetsProfile() = runBlocking {
        repository.ensureInitialized()
        repository.toggleItem("sleep_temp")
        repository.addNotification("Test Alert", "Alert Body", "test")
        repository.setActiveUserSession("uid_12345", "user@test.com", "Test User", true, "Member")

        // Perform wipe
        repository.wipeAllUserData()

        val profile = repository.getUserProfile()
        val completions = getTodayCompletedSet()

        assertNull("Firebase UID should be null after wipe", profile?.firebaseUid)
        assertNull("Email should be null after wipe", profile?.email)
        assertFalse("isPro should be false after wipe", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)
        assertEquals(0, profile?.streakDays)
        assertTrue("Completions must be empty after wipe", completions.isEmpty())
    }

    @Test
    fun testSessionLifecycle_SetAndClearSession() = runBlocking {
        repository.ensureInitialized()

        repository.setActiveUserSession(
            firebaseUid = "auth_uid_abc",
            email = "pilot@protocol.app",
            displayName = "Protocol Pilot",
            isEmailVerified = true,
            role = "Admin" // Should be sanitized to Member
        )

        var profile = repository.getUserProfile()
        assertEquals("auth_uid_abc", profile?.firebaseUid)
        assertEquals("pilot@protocol.app", profile?.email)
        assertEquals("Protocol Pilot", profile?.displayName)
        assertTrue(profile?.isEmailVerified ?: false)
        assertEquals("Member", profile?.accountRole) // Role clamping

        repository.clearActiveUserSession()
        profile = repository.getUserProfile()
        assertNull(profile?.firebaseUid)
        assertNull(profile?.email)
        assertFalse(profile?.isPro ?: true)
    }

    @Test
    fun testUpdateCircadianOffsets() = runBlocking {
        repository.ensureInitialized()
        repository.updateCircadianOffsets(caffeineDelay = 105, lightWindow = 45, windDown = 15)

        val profile = repository.getUserProfile()
        assertEquals(105, profile?.caffeineDelayMinutes)
        assertEquals(45, profile?.lightWindowMinutes)
        assertEquals(15, profile?.windDownHours)
    }
}
