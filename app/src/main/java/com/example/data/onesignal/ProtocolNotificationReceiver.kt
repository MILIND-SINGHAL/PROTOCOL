package com.example.data.onesignal

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

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

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
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: "protocol_task"
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Protocol Task"

        // Dismiss the current notification from shade if present
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }

        when (intent.action) {
            ACTION_COMPLETE_TASK -> {
                // Requirement 🔴 16: Validate that taskId belongs to today's recognized protocol items
                if (taskId !in VALID_PROTOCOL_ITEM_IDS) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = ProtocolDatabase.getDatabase(context)
                        db.protocolDao().insertNotificationLog(
                            NotificationLogEntity(
                                title = "Rejected Notification Action",
                                message = "Ignored completion request for unverified task ID '$taskId'.",
                                tag = "action_rejected",
                                timestamp = System.currentTimeMillis(),
                                isRead = false
                            )
                        )
                    }
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "⚠️ Unrecognized task ID. Completion ignored.", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                CoroutineScope(Dispatchers.IO).launch {
                    val db = ProtocolDatabase.getDatabase(context)
                    val dao = db.protocolDao()

                    // Mark completed in Room database for today's date
                    dao.insertOrUpdateCompletion(
                        ProtocolCompletionEntity(
                            dateKey = today,
                            itemId = taskId,
                            isCompleted = true,
                            completedAt = System.currentTimeMillis()
                        )
                    )

                    // Log execution event
                    dao.insertNotificationLog(
                        NotificationLogEntity(
                            title = "Notification Action: $taskTitle",
                            message = "Marked '$taskTitle' as complete directly from Android notification shade.",
                            tag = "action_completed",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                }

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "✅ $taskTitle completed via notification action!", Toast.LENGTH_SHORT).show()
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
                }

                val pendingSnoozeIntent = PendingIntent.getBroadcast(
                    context,
                    if (notificationId != -1) notificationId + 500 else 8888,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (alarmManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTimeMs, pendingSnoozeIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, snoozeTimeMs, pendingSnoozeIntent)
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val db = ProtocolDatabase.getDatabase(context)
                    db.protocolDao().insertNotificationLog(
                        NotificationLogEntity(
                            title = "Notification Snooze Scheduled",
                            message = "Scheduled 15-minute snooze alarm via AlarmManager for '$taskTitle'.",
                            tag = "action_snoozed",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                }

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "⏰ Snoozed '$taskTitle' for 15 minutes.", Toast.LENGTH_SHORT).show()
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

            ACTION_START_TIMER, ACTION_LAUNCH_PACER -> {
                // Launch MainActivity with target route
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
