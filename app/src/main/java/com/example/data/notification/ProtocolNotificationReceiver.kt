package com.example.data.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.NotificationLogEntity
import com.example.data.local.ProtocolCompletionEntity
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.notification.NotificationTaskValidator
import com.example.data.notification.NotificationValidationResult
import com.example.data.notification.ProtocolNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProtocolNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COMPLETE_TASK = "com.example.ACTION_COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK = "com.example.ACTION_SNOOZE_TASK"
        const val ACTION_TRIGGER_SNOOZED_ALERT = "com.example.ACTION_TRIGGER_SNOOZED_ALERT"
        const val ACTION_START_TIMER = "com.example.ACTION_START_TIMER"
        const val ACTION_LAUNCH_PACER = "com.example.ACTION_LAUNCH_PACER"
        const val ACTION_DISMISS_TASK = "com.example.ACTION_DISMISS_TASK"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_DATE_KEY = "extra_date_key"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACTION_TYPE = "extra_action_type"

        // Set of valid, recognized protocol task IDs in the application
        val VALID_PROTOCOL_ITEM_IDS = setOf(
            // Physical Recovery Track
            "rec_sun_mobility", "rec_creatine_water", "rec_zone2_flush", "rec_tissue_release",
            "rec_contrast_flush", "rec_mag_glycinate", "rec_legs_wall", "rec_cool_room",
            // Mental Clarity Track
            "clarity_lux_splash", "clarity_alpha_coffee", "clarity_deep_work", "clarity_dopamine_reset",
            "clarity_air_walk", "clarity_digital_sunset", "clarity_brain_dump", "clarity_neuro_down",
            // All Stacks Combined Track
            "all_sunlight", "all_hydration", "all_zone2", "all_caffeine_cutoff", "all_nsdr",
            "all_blue_block", "all_magnesium", "all_temp",
            // Deep Sleep Baseline Track
            "sleep_sunlight", "sleep_delay_caffeine", "sleep_hydration", "sleep_caffeine_cutoff",
            "sleep_nsdr", "sleep_blue_light", "sleep_magnesium", "sleep_temp"
        )
        @Volatile
        var testDatabase: ProtocolDatabase? = null
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val pendingResult = try { goAsync() } catch (_: Exception) { null }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIntent(context, intent)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    suspend fun handleIntent(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: ""
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Protocol Task"
        val dateKey = intent.getStringExtra(EXTRA_DATE_KEY)
        val userId = intent.getStringExtra(EXTRA_USER_ID)

        // Dismiss the current notification from shade if present
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }

        val db = testDatabase ?: ProtocolDatabase.getDatabase(context)
        val dao = db.protocolDao()

        when (intent.action) {
            ACTION_COMPLETE_TASK -> {
                val repo = ProtocolRepository(dao)
                val result = repo.completeItemAuthoritatively(
                    taskId = taskId,
                    taskTitle = taskTitle,
                    targetDateKey = dateKey,
                    targetUserId = userId
                )

                try {
                    Handler(Looper.getMainLooper()).post {
                        when (result) {
                            is NotificationValidationResult.Success -> {
                                Toast.makeText(context, "✅ $taskTitle completed via notification action!", Toast.LENGTH_SHORT).show()
                            }
                            is NotificationValidationResult.AlreadyCompleted -> {
                                Toast.makeText(context, "ℹ️ $taskTitle is already completed for today.", Toast.LENGTH_SHORT).show()
                            }
                            is NotificationValidationResult.Rejected -> {
                                Toast.makeText(context, "⚠️ Completion rejected: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore Toast dispatch failures in headless test runners
                }
            }

            ACTION_SNOOZE_TASK -> {
                // Requirement 🔴 15: Schedule actual alarm 15 minutes later using AlarmManager
                val snoozeTimeMs = System.currentTimeMillis() + (15 * 60 * 1000L) // 15 minutes
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

                val snoozeIntent = Intent(context, ProtocolNotificationReceiver::class.java).apply {
                    action = ACTION_TRIGGER_SNOOZED_ALERT
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_TASK_TITLE, taskTitle)
                    putExtra(EXTRA_NOTIFICATION_ID, if (notificationId != -1) notificationId + 500 else 8888)
                    putExtra(EXTRA_DATE_KEY, dateKey ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                    putExtra(EXTRA_USER_ID, userId ?: "")
                }

                val pendingSnoozeIntent = PendingIntent.getBroadcast(
                    context,
                    if (notificationId != -1) notificationId + 500 else 8888,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (alarmManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // API 31+: Must check canScheduleExactAlarms() before using exact alarms
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTimeMs, pendingSnoozeIntent)
                        } else {
                            // Graceful fallback: use inexact alarm window (±2 min)
                            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, snoozeTimeMs, 2 * 60 * 1000L, pendingSnoozeIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTimeMs, pendingSnoozeIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, snoozeTimeMs, pendingSnoozeIntent)
                    }
                }

                dao.insertNotificationLog(
                    NotificationLogEntity(
                        title = "Notification Snooze Scheduled",
                        message = "Scheduled 15-minute snooze alarm via AlarmManager for '$taskTitle'.",
                        tag = "action_snoozed",
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                )

                try {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "⏰ Snoozed '$taskTitle' for 15 minutes.", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    // Ignore Toast dispatch failures in headless test runners
                }
            }

            ACTION_TRIGGER_SNOOZED_ALERT -> {
                // Fire the actual snoozed notification when AlarmManager triggers
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null) {
                    val channelId = ProtocolNotificationManager.CHANNEL_ID
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            ProtocolNotificationManager.CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH
                        )
                        notificationManager.createNotificationChannel(channel)
                    }

                    val contentIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingContentIntent = PendingIntent.getActivity(
                        context,
                        notificationId,
                        contentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val snoozedBuilder = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle("⏰ Snooze Ended: $taskTitle")
                        .setContentText("Your 15-minute snooze period has ended. Time to complete your circadian protocol task.")
                        .setStyle(NotificationCompat.BigTextStyle().bigText("Your 15-minute snooze period has ended. Time to complete your circadian protocol task."))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingContentIntent)

                    val completeIntent = Intent(context, ProtocolNotificationReceiver::class.java).apply {
                        action = ACTION_COMPLETE_TASK
                        putExtra(EXTRA_TASK_ID, taskId)
                        putExtra(EXTRA_TASK_TITLE, taskTitle)
                        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                        putExtra(EXTRA_DATE_KEY, dateKey ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                        putExtra(EXTRA_USER_ID, userId ?: "")
                    }
                    val pendingCompleteIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId + 10,
                        completeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    snoozedBuilder.addAction(
                        android.R.drawable.ic_menu_agenda,
                        "✅ Complete Task",
                        pendingCompleteIntent
                    )

                    notificationManager.notify(notificationId, snoozedBuilder.build())
                }
            }

            ACTION_DISMISS_TASK -> {
                // Dismiss action must NOT mark the task complete
                dao.insertNotificationLog(
                    NotificationLogEntity(
                        title = "Notification Dismissed",
                        message = "Dismissed alert for '$taskTitle'. No completion recorded.",
                        tag = "action_dismissed",
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                )
            }

            ACTION_START_TIMER, ACTION_LAUNCH_PACER -> {
                // Launch MainActivity with target route (does not complete task)
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("route", if (intent.action == ACTION_LAUNCH_PACER) "pacer" else "timer")
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_TASK_TITLE, taskTitle)
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
