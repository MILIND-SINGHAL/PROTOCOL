package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.notification.InAppNotificationMessage
import com.example.data.notification.NotificationAction
import com.example.data.notification.NotificationCampaign
import com.example.data.notification.ProtocolNotificationManager
import com.example.data.onesignal.ProtocolNotificationReceiver
import org.json.JSONObject
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
class NotificationActionTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository
    private lateinit var manager: ProtocolNotificationManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
        manager = ProtocolNotificationManager(context, repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testTaskIdValidation_RecognizedProtocolTaskAccepted() {
        val validIds = ProtocolNotificationReceiver.VALID_PROTOCOL_ITEM_IDS
        assertTrue("sleep_sunlight must be recognized", validIds.contains("sleep_sunlight"))
        assertTrue("rec_sun_mobility must be recognized", validIds.contains("rec_sun_mobility"))
        assertTrue("clarity_lux_splash must be recognized", validIds.contains("clarity_lux_splash"))
        assertTrue("all_magnesium must be recognized", validIds.contains("all_magnesium"))
    }

    @Test
    fun testTaskIdValidation_ArbitraryUnrecognizedTaskRejected() {
        val validIds = ProtocolNotificationReceiver.VALID_PROTOCOL_ITEM_IDS
        assertFalse("Arbitrary task must not be recognized", validIds.contains("arbitrary_unknown_task_123"))
        assertFalse("SQL injection string must not be recognized", validIds.contains("drop_table_users"))
        assertFalse("Empty string must not be recognized", validIds.contains(""))
    }

    @Test
    fun testNotificationPayloadJson_IncludesActionButtonsAndDeepLink() {
        val campaign = NotificationCampaign(
            id = "camp_test_actions",
            title = "Sunlight Reminder",
            message = "Get 10 minutes of direct retinal sunlight.",
            category = "circadian",
            triggerType = "Window Active",
            circadianPhase = "CORTISOL_AWAKENING",
            actions = listOf(
                NotificationAction("act_timer", "Start Timer", "TIMER", "sleep_sunlight"),
                NotificationAction("act_done", "Mark Done", "COMPLETE", "sleep_sunlight")
            )
        )

        val jsonString = manager.generateNotificationPayloadJson(campaign)
        val json = JSONObject(jsonString)

        assertEquals("Sunlight Reminder", json.getJSONObject("headings").getString("en"))
        assertEquals("Protocol Circadian Alerts", ProtocolNotificationManager.CHANNEL_NAME)
        assertEquals("protocol://circadian?phase=CORTISOL_AWAKENING", json.getJSONObject("data").getString("deep_link"))

        val buttons = json.getJSONArray("buttons")
        assertEquals(2, buttons.length())
        assertEquals("act_timer", buttons.getJSONObject(0).getString("id"))
        assertEquals("Start Timer", buttons.getJSONObject(0).getString("text"))
    }

    @Test
    fun testNotificationManager_InitialStateHasNoFakeBiologicalDataOrPlayerId() {
        val tags = manager.tags.value
        assertEquals("UNCALCULATED", tags["circadian_phase"])
        assertEquals("PENDING_SYNC", tags["caffeine_lockout_status"])
        assertEquals("0_DAYS", tags["streak_milestone"])
        assertEquals("FREE_TIER", tags["subscriber_tier"])

        // Must not contain fabricated biological states
        assertFalse(tags.containsValue("CORTISOL_PEAK"))
        assertFalse(tags.containsValue("PRO_ALL_ACCESS"))
        assertFalse(tags.containsKey("one_signal_player_id"))
        assertFalse(tags.containsValue("os_circadian_91283a"))
    }

    @Test
    fun testInAppNotificationMessage_ShowAndDismiss() {
        assertNull(manager.activeInAppMessage.value)

        val iam = InAppNotificationMessage(
            id = "iam_1",
            title = "Hydration Window",
            body = "Drink 500ml water with electrolytes.",
            badge = "ROUTINE",
            primaryActionLabel = "Acknowledge",
            actionRoute = "COMPLETE",
            targetTaskId = "sleep_hydration"
        )

        manager.showInAppMessage(iam)
        assertNotNull(manager.activeInAppMessage.value)
        assertEquals("Hydration Window", manager.activeInAppMessage.value?.title)

        manager.dismissInAppMessage()
        assertNull(manager.activeInAppMessage.value)
    }
}
