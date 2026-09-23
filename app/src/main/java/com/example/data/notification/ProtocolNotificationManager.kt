package com.example.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.ProtocolRepository
import com.example.data.onesignal.ProtocolNotificationReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class NotificationAction(
    val id: String,
    val label: String,
    val actionType: String, // COMPLETE, SNOOZE, TIMER, PACER
    val targetTaskId: String
)

data class NotificationCampaign(
    val id: String,
    val title: String,
    val message: String,
    val category: String,
    val triggerType: String,
    val circadianPhase: String = "ALL",
    val actions: List<NotificationAction> = emptyList()
)

data class InAppNotificationMessage(
    val id: String,
    val title: String,
    val body: String,
    val badge: String,
    val primaryActionLabel: String,
    val actionRoute: String,
    val targetTaskId: String? = null,
    val secondaryActionLabel: String = "Dismiss"
)

data class JourneyStep(
    val stepNumber: Int,
    val title: String,
    val triggerCondition: String,
    val notificationTitle: String,
    val tagMutation: String
)

data class CircadianJourney(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val steps: List<JourneyStep>
)

class ProtocolNotificationManager(
    private val context: Context,
    private val repository: ProtocolRepository
) {
    companion object {
        const val CHANNEL_ID = "protocol_circadian_channel"
        const val CHANNEL_NAME = "Protocol Circadian Alerts"
    }

    // Clean initial state: no fake biological tags or fake player ID
    private val _tags = MutableStateFlow<Map<String, String>>(
        mapOf(
            "circadian_phase" to "UNCALCULATED",
            "caffeine_lockout_status" to "PENDING_SYNC",
            "active_protocol_track" to "Circadian Alignment",
            "streak_milestone" to "0_DAYS",
            "wearable_telemetry_source" to "None",
            "subscriber_tier" to "FREE_TIER"
        )
    )
    val tags: StateFlow<Map<String, String>> = _tags.asStateFlow()

    private val _activeInAppMessage = MutableStateFlow<InAppNotificationMessage?>(null)
    val activeInAppMessage: StateFlow<InAppNotificationMessage?> = _activeInAppMessage.asStateFlow()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tactical physiological alerts with interactive notification action buttons"
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun setTags(newTags: Map<String, String>) {
        _tags.value = _tags.value + newTags
    }

    fun syncCircadianTags(
        wakeTime: String,
        focus: String,
        completedCount: Int,
        streakDays: Int,
        wearable: String
    ) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val phase = when (hour) {
            in 5..8 -> "CORTISOL_AWAKENING_SPIKE"
            in 9..12 -> "DOPAMINE_HIGH_ALERTNESS"
            in 13..17 -> "ADENOSINE_PLATEAU_ZONE"
            in 18..21 -> "MELATONIN_ONSET_DUSK"
            else -> "DELTA_WAVE_RESTORATION"
        }

        val caffeineStatus = when {
            hour < 8 -> "ACTIVE_LOCKOUT"
            hour in 8..13 -> "OPTIMAL_CAFFEINE_WINDOW"
            else -> "STRICT_EVENING_CUTOFF"
        }

        setTags(
            mapOf(
                "wake_time" to wakeTime,
                "circadian_phase" to phase,
                "caffeine_lockout_status" to caffeineStatus,
                "active_protocol_track" to focus,
                "today_completed_count" to completedCount.toString(),
                "streak_milestone" to "${streakDays}_DAYS",
                "wearable_source" to wearable,
                "last_biological_sync" to System.currentTimeMillis().toString()
            )
        )
    }

    fun showInAppMessage(iam: InAppNotificationMessage) {
        _activeInAppMessage.value = iam
    }

    fun dismissInAppMessage() {
        _activeInAppMessage.value = null
    }

    /**
     * Requirement 24: Reset notification tags and in-app message state.
     * Invoked during atomic account deletion.
     */
    fun resetIdentityAndTags() {
        _tags.value = mapOf(
            "circadian_phase" to "UNCALCULATED",
            "caffeine_lockout_status" to "PENDING_SYNC",
            "active_protocol_track" to "Circadian Alignment",
            "streak_milestone" to "0_DAYS",
            "wearable_telemetry_source" to "None",
            "subscriber_tier" to "FREE_TIER"
        )
        _activeInAppMessage.value = null
    }

    val availableCampaigns = listOf(
        NotificationCampaign(
            id = "camp_lux",
            title = "☀️ Morning Sunlight Window",
            message = "Get 10 minutes of direct retinal lux exposure to lock your 14h melatonin clock.",
            category = "circadian",
            triggerType = "0–60m Post-Wake",
            circadianPhase = "CORTISOL_AWAKENING_SPIKE",
            actions = listOf(
                NotificationAction(
                    id = "act_sun_timer",
                    label = "⏱️ Start 10m Timer",
                    actionType = "TIMER",
                    targetTaskId = "rec_sun_mobility"
                ),
                NotificationAction(
                    id = "act_sun_done",
                    label = "✅ Mark Done",
                    actionType = "COMPLETE",
                    targetTaskId = "rec_sun_mobility"
                )
            )
        ),
        NotificationCampaign(
            id = "camp_caffeine",
            title = "☕ 90m Caffeine Lockout Lifted",
            message = "Adenosine receptors cleared. Your first cup of coffee or matcha is now biologically optimal.",
            category = "circadian",
            triggerType = "90m Post-Wake",
            circadianPhase = "DOPAMINE_HIGH_ALERTNESS",
            actions = listOf(
                NotificationAction(
                    id = "act_caff_log",
                    label = "☕ Log 1st Cup",
                    actionType = "COMPLETE",
                    targetTaskId = "clarity_alpha_coffee"
                ),
                NotificationAction(
                    id = "act_caff_snooze",
                    label = "⏰ Snooze 30m",
                    actionType = "SNOOZE",
                    targetTaskId = "clarity_alpha_coffee"
                )
            )
        ),
        NotificationCampaign(
            id = "camp_midday_reset",
            title = "🧘 10 Min NSDR / Dopamine Reset",
            message = "Midday energy trough detected. Reload acetylcholine and restore cognitive stamina.",
            category = "performance",
            triggerType = "Midday Trough",
            circadianPhase = "ADENOSINE_PLATEAU_ZONE",
            actions = listOf(
                NotificationAction(
                    id = "act_nsdr_start",
                    label = "⏱️ Start 10m NSDR",
                    actionType = "TIMER",
                    targetTaskId = "sleep_nsdr"
                ),
                NotificationAction(
                    id = "act_nsdr_done",
                    label = "✅ Done",
                    actionType = "COMPLETE",
                    targetTaskId = "sleep_nsdr"
                )
            )
        ),
        NotificationCampaign(
            id = "camp_evening",
            title = "🌙 Evening Recovery & Wind-Down",
            message = "Dim overhead lights to warm tones. Take Magnesium and initiate parasympathetic breathing.",
            category = "recovery",
            triggerType = "60m Pre-Bed",
            circadianPhase = "MELATONIN_ONSET_DUSK",
            actions = listOf(
                NotificationAction(
                    id = "act_eve_pacer",
                    label = "🌬️ Box Breathing",
                    actionType = "PACER",
                    targetTaskId = "pacer"
                ),
                NotificationAction(
                    id = "act_eve_mag",
                    label = "💊 Took Magnesium",
                    actionType = "COMPLETE",
                    targetTaskId = "rec_mag_glycinate"
                )
            )
        ),
        NotificationCampaign(
            id = "camp_streak",
            title = "🔥 3-Day Rhythm Anchored",
            message = "Your circadian consistency index increased by 28%. Tap to share your execution badge.",
            category = "engagement",
            triggerType = "Lifecycle Milestone",
            circadianPhase = "RETENTION_JOURNEY",
            actions = listOf(
                NotificationAction(
                    id = "act_streak_share",
                    label = "🚀 Share Streak Card",
                    actionType = "TIMER",
                    targetTaskId = "streak_share"
                )
            )
        )
    )

    val circadianJourneys = listOf(
        CircadianJourney(
            id = "journey_dawn",
            name = "Dawn Cortisol Awakening",
            subtitle = "Circadian Anchor Automation",
            description = "Sequenced lifecycle journey triggering light exposure, cortisol spike verification, and 90-minute adenosine clearance lockout.",
            steps = listOf(
                JourneyStep(1, "Wake Detection", "Trigger: User wake time reached", "Good morning. Initiating Cortisol Awakening sequence.", "tag.circadian_phase = 'CORTISOL_AWAKENING'"),
                JourneyStep(2, "Sunlight Lux Window", "Delay: +10 min", "☀️ 10 Min Retinal Light Window (Action: Start Timer)", "tag.lux_window = 'ACTIVE'"),
                JourneyStep(3, "Adenosine Clearance", "Delay: +90 min", "☕ Caffeine Lockout Lifted (Action: Log First Cup)", "tag.caffeine_lockout_status = 'LIFTED'")
            )
        ),
        CircadianJourney(
            id = "journey_midday",
            name = "Midday Metabolic & Dopamine Reset",
            subtitle = "Prevent 2:00 PM Crash",
            description = "Monitors adenosine accumulation and enforces strict 2:00 PM caffeine cutoff while recommending Non-Sleep Deep Rest (NSDR).",
            steps = listOf(
                JourneyStep(1, "Pre-Cutoff Warning", "Trigger: 1:30 PM", "30 minutes until strict 2:00 PM caffeine cutoff.", "tag.caffeine_warning = 'T_MINUS_30'"),
                JourneyStep(2, "Hard Lockout Enforced", "Trigger: 2:00 PM", "☕ Caffeine Window Closed. Protect tonight's deep slow-wave delta sleep.", "tag.caffeine_lockout_status = 'ENFORCED'"),
                JourneyStep(3, "Autonomic NSDR Prompt", "Trigger: 2:30 PM dip", "🧘 10 Min NSDR Reset (Action: Start NSDR)", "tag.midday_reset = 'COMPLETED'")
            )
        ),
        CircadianJourney(
            id = "journey_dusk",
            name = "Circadian Dusk & Delta Sleep Prep",
            subtitle = "Melatonin Protection System",
            description = "Triggers ambient light reduction, targeted magnesium supplementation, and thermal bedroom cooling for optimal slow-wave sleep.",
            steps = listOf(
                JourneyStep(1, "Digital Sunset Cues", "Trigger: 2h pre-bed", "Overhead LED lights off. Zero blue light to allow natural melatonin synthesis.", "tag.melatonin_surge = 'INITIALIZED'"),
                JourneyStep(2, "Neuromuscular Dosing", "Trigger: 60m pre-bed", "🌙 Targeted Magnesium Dose (Action: Took Magnesium / Box Breathing)", "tag.magnesium_taken = 'TRUE'"),
                JourneyStep(3, "Sleep Chamber Climate", "Trigger: 30m pre-bed", "❄️ Room Climate 18°C. Core body temperature drop initiated.", "tag.bedroom_temp_set = '18C'")
            )
        ),
        CircadianJourney(
            id = "journey_shield",
            name = "Streak Shield & VIP Engagement",
            subtitle = "Automated Retention Engine",
            description = "Recognizes daily compliance milestones and shields habits before they slip.",
            steps = listOf(
                JourneyStep(1, "Morning Stack Complete", "Trigger: All morning items done", "⚡ Morning Ignition 100% Locked. Cognitive momentum is primed.", "tag.morning_compliance = '100%'"),
                JourneyStep(2, "Milestone Reward", "Trigger: 3rd consecutive day", "🔥 3-Day Rhythm Anchored! VIP executive badge unlocked.", "tag.streak_tier = 'CHAMPION'"),
                JourneyStep(3, "Streak Rescue Alert", "Trigger: Evening inactivity", "Your 3-day rhythm is at risk. 2 minutes to complete your evening stack.", "tag.streak_shield = 'ENGAGED'")
            )
        )
    )

    fun generateNotificationPayloadJson(campaign: NotificationCampaign): String {
        val root = JSONObject().apply {
            put("headings", JSONObject().put("en", campaign.title))
            put("contents", JSONObject().put("en", campaign.message))
            put("android_channel_id", CHANNEL_ID)
            put("priority", 10)
            put("data", JSONObject().apply {
                put("category", campaign.category)
                put("circadian_phase", campaign.circadianPhase)
                put("campaign_id", campaign.id)
                put("deep_link", "protocol://circadian?phase=${campaign.circadianPhase}")
            })
            if (campaign.actions.isNotEmpty()) {
                val buttonsArray = JSONArray()
                campaign.actions.forEach { act ->
                    buttonsArray.put(
                        JSONObject().apply {
                            put("id", act.id)
                            put("text", act.label)
                        }
                    )
                }
                put("buttons", buttonsArray)
            }
        }
        return root.toString(2)
    }

    suspend fun triggerCampaign(campaign: NotificationCampaign) {
        // 1. Log in database
        repository.addNotification(
            title = campaign.title,
            message = campaign.message,
            tag = campaign.category
        )

        // 2. Post real local Android notification with interactive buttons
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val notificationId = campaign.id.hashCode()

                // Content Intent (Tap notification body -> opens app)
                val contentIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("campaign_id", campaign.id)
                }
                val pendingContentIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(campaign.title)
                    .setContentText(campaign.message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(campaign.message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingContentIntent)

                // Add interactive action buttons
                campaign.actions.forEachIndexed { index, action ->
                    val broadcastAction = when (action.actionType) {
                        "COMPLETE" -> ProtocolNotificationReceiver.ACTION_COMPLETE_TASK
                        "SNOOZE" -> ProtocolNotificationReceiver.ACTION_SNOOZE_TASK
                        "TIMER" -> ProtocolNotificationReceiver.ACTION_START_TIMER
                        "PACER" -> ProtocolNotificationReceiver.ACTION_LAUNCH_PACER
                        else -> ProtocolNotificationReceiver.ACTION_COMPLETE_TASK
                    }

                    val actionIntent = Intent(context, ProtocolNotificationReceiver::class.java).apply {
                        this.action = broadcastAction
                        putExtra(ProtocolNotificationReceiver.EXTRA_TASK_ID, action.targetTaskId)
                        putExtra(ProtocolNotificationReceiver.EXTRA_TASK_TITLE, campaign.title)
                        putExtra(ProtocolNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    }

                    val pendingActionIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId + index + 100,
                        actionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    builder.addAction(
                        android.R.drawable.ic_menu_agenda,
                        action.label,
                        pendingActionIntent
                    )
                }

                notificationManager.notify(notificationId, builder.build())
            }
        } catch (_: SecurityException) {
            // Graceful fallback if notification permission is not granted
        }

        // 3. Trigger In-App Notification Message
        val iamActionRoute = campaign.actions.firstOrNull()?.actionType ?: "NONE"
        showInAppMessage(
            InAppNotificationMessage(
                id = "iam_${campaign.id}",
                title = campaign.title,
                body = campaign.message,
                badge = "CIRCADIAN ALERT",
                primaryActionLabel = campaign.actions.firstOrNull()?.label ?: "Acknowledge",
                actionRoute = iamActionRoute,
                targetTaskId = campaign.actions.firstOrNull()?.targetTaskId
            )
        )
    }

    suspend fun sendCustomCircadianPush(
        title: String,
        body: String,
        category: String,
        action1Label: String?,
        action2Label: String?
    ) {
        val actions = mutableListOf<NotificationAction>()
        if (!action1Label.isNullOrBlank()) {
            actions.add(NotificationAction("custom_1", action1Label, "COMPLETE", "custom_task"))
        }
        if (!action2Label.isNullOrBlank()) {
            actions.add(NotificationAction("custom_2", action2Label, "PACER", "pacer"))
        }

        val customCampaign = NotificationCampaign(
            id = "custom_${System.currentTimeMillis()}",
            title = title,
            message = body,
            category = category,
            triggerType = "Custom Test Broadcast",
            circadianPhase = "CUSTOM_TRIGGER",
            actions = actions
        )

        triggerCampaign(customCampaign)
    }
}
