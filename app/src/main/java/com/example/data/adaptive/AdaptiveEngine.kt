package com.example.data.adaptive

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AdaptiveDifficulty(
    val title: String,
    val badge: String,
    val durationMultiplier: Float
) {
    DELOAD_MICRO_ANCHOR(
        title = "Deload & Micro-Anchor",
        badge = "LOW FRICTION",
        durationMultiplier = 0.65f // Reduced duration by ~35%
    ),
    BALANCED_CALIBRATION(
        title = "Balanced Calibration",
        badge = "STANDARD LOAD",
        durationMultiplier = 1.0f // Standard duration
    ),
    PROGRESSIVE_OVERLOAD(
        title = "Progressive Overload",
        badge = "ADVANCED STIMULUS",
        durationMultiplier = 1.35f // Extended focus/duration by +35%
    )
}

data class DayExecutionSummary(
    val dateKey: String,
    val completedCount: Int,
    val totalRequired: Int,
    val adherenceRatio: Float,
    val isStreakQualified: Boolean,
    val completedItemIds: Set<String>,
    val missedItemIds: List<String>
) {
    val adherencePercentage: Int
        get() = (adherenceRatio * 100).toInt().coerceIn(0, 100)
}

data class AdaptiveProtocolState(
    val difficulty: AdaptiveDifficulty = AdaptiveDifficulty.BALANCED_CALIBRATION,
    val yesterdaySummary: DayExecutionSummary,
    val headline: String,
    val rationale: String,
    val priorityTaskId: String? = null,
    val priorityTaskLabel: String? = null,
    val tomorrowProjection: String
)

object AdaptiveEngine {

    const val MIN_STREAK_ADHERENCE_RATIO = 0.80f // Requirement 17: adherence >= 80%
    const val STANDARD_REQUIRED_TASK_COUNT = 8

    // Canonical item IDs for each track
    val TRACK_REQUIRED_ITEMS = mapOf(
        "Physical Recovery" to listOf(
            "rec_sun_mobility", "rec_creatine_water", "rec_zone2_flush",
            "rec_tissue_release", "rec_contrast_flush",
            "rec_mag_glycinate", "rec_legs_wall", "rec_cool_room"
        ),
        "Mental Clarity" to listOf(
            "clarity_lux_splash", "clarity_alpha_coffee", "clarity_deep_work",
            "clarity_dopamine_reset", "clarity_air_walk",
            "clarity_digital_sunset", "clarity_brain_dump", "clarity_neuro_down"
        ),
        "All Stacks" to listOf(
            "all_sunlight", "all_hydration", "all_zone2",
            "all_caffeine_cutoff", "all_nsdr",
            "all_blue_block", "all_magnesium", "all_temp"
        ),
        "Deep Sleep" to listOf(
            "sleep_sunlight", "sleep_delay_caffeine", "sleep_hydration",
            "sleep_caffeine_cutoff", "sleep_nsdr",
            "sleep_blue_light", "sleep_magnesium", "sleep_temp"
        )
    )

    fun getRequiredItemsForTrack(trackName: String?): List<String> {
        val clean = trackName?.trim().orEmpty()
        return when {
            clean.contains("Physical", ignoreCase = true) || clean.contains("Muscle", ignoreCase = true) ->
                TRACK_REQUIRED_ITEMS["Physical Recovery"]!!
            clean.contains("Mental", ignoreCase = true) || clean.contains("Clarity", ignoreCase = true) ->
                TRACK_REQUIRED_ITEMS["Mental Clarity"]!!
            clean.contains("All Stacks", ignoreCase = true) ->
                TRACK_REQUIRED_ITEMS["All Stacks"]!!
            else ->
                TRACK_REQUIRED_ITEMS["Deep Sleep"]!!
        }
    }

    /**
     * Requirement 17: Evaluates whether a specific day qualifies for the streak based on adherence >= 80%.
     */
    fun summarizeDay(
        dateKey: String,
        completedItemIds: Set<String>,
        requiredItems: List<String>
    ): DayExecutionSummary {
        val total = requiredItems.size.coerceAtLeast(1)
        val validCompleted = completedItemIds.filter { it in requiredItems }.toSet()
        val completedCount = validCompleted.size
        val ratio = (completedCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val isQualified = ratio >= MIN_STREAK_ADHERENCE_RATIO
        val missed = requiredItems.filter { it !in validCompleted }

        return DayExecutionSummary(
            dateKey = dateKey,
            completedCount = completedCount,
            totalRequired = total,
            adherenceRatio = ratio,
            isStreakQualified = isQualified,
            completedItemIds = validCompleted,
            missedItemIds = missed
        )
    }

    /**
     * Requirement 17: Calculate streak where EACH qualifying day MUST meet adherence >= 80%.
     */
    fun calculateAdherenceStreak(
        completionsByDate: Map<String, Set<String>>,
        requiredItems: List<String>,
        minAdherence: Float = MIN_STREAK_ADHERENCE_RATIO
    ): Int {
        if (completionsByDate.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)

        var streak = 0
        val checkCal = Calendar.getInstance()

        // Check today's adherence
        val todayCompleted = completionsByDate[todayStr].orEmpty()
        val todaySummary = summarizeDay(todayStr, todayCompleted, requiredItems)
        val todayQualifies = todaySummary.adherenceRatio >= minAdherence

        if (todayQualifies) {
            streak++
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            // Today is still ongoing; check yesterday to see if current streak is intact
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = sdf.format(checkCal.time)
            val yesterdayCompleted = completionsByDate[yesterdayStr].orEmpty()
            val yesterdaySummary = summarizeDay(yesterdayStr, yesterdayCompleted, requiredItems)
            if (yesterdaySummary.adherenceRatio < minAdherence) {
                return 0
            }
        }

        // Iterate backwards through consecutive preceding days
        while (true) {
            val dateStr = sdf.format(checkCal.time)
            val dayCompleted = completionsByDate[dateStr].orEmpty()
            val daySummary = summarizeDay(dateStr, dayCompleted, requiredItems)

            if (daySummary.adherenceRatio >= minAdherence) {
                streak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }

    /**
     * Requirement 18: Closed-Loop Adaptive Protocol Engine.
     * Evaluates yesterday's execution -> Adjusts difficulty & durations -> Projects tomorrow's protocol.
     */
    fun evaluateAdaptation(
        yesterdaySummary: DayExecutionSummary,
        recentDaysAdherence: List<Float> = emptyList(),
        activeFocus: String = "Deep Sleep"
    ): AdaptiveProtocolState {
        val adherence = yesterdaySummary.adherenceRatio
        val missed = yesterdaySummary.missedItemIds
        val missedCount = missed.size

        return when {
            // Case 1: Low Adherence (< 50%) -> Deload & Habit Anchor
            adherence < 0.50f -> {
                val priorityId = missed.firstOrNull()
                AdaptiveProtocolState(
                    difficulty = AdaptiveDifficulty.DELOAD_MICRO_ANCHOR,
                    yesterdaySummary = yesterdaySummary,
                    headline = "Auto-Calibrated: Deload & Micro-Anchor",
                    rationale = "Yesterday's adherence was ${yesterdaySummary.adherencePercentage}% ($missedCount items missed). Protocol scaled down timer durations (-35%) to reduce habit friction and restore consistency without burnout.",
                    priorityTaskId = priorityId,
                    priorityTaskLabel = "Essential Micro-Anchor",
                    tomorrowProjection = "Complete >= 65% today to graduate from Deload into Standard Balanced Calibration tomorrow."
                )
            }

            // Case 2: Moderate Adherence (50% - 79%) -> Balanced Calibration + Targeted Priority
            adherence < MIN_STREAK_ADHERENCE_RATIO -> {
                val priorityId = missed.firstOrNull()
                val gapLabel = when {
                    priorityId?.contains("sun", ignoreCase = true) == true -> "Morning Sunlight Anchor"
                    priorityId?.contains("caff", ignoreCase = true) == true -> "Caffeine Window Cutoff"
                    priorityId?.contains("nsdr", ignoreCase = true) == true -> "Midday Parasympathetic NSDR"
                    priorityId?.contains("mag", ignoreCase = true) == true -> "Evening Mineral Relaxation"
                    else -> "Evening Wind-Down"
                }
                AdaptiveProtocolState(
                    difficulty = AdaptiveDifficulty.BALANCED_CALIBRATION,
                    yesterdaySummary = yesterdaySummary,
                    headline = "Auto-Calibrated: Targeted Calibration",
                    rationale = "Yesterday reached ${yesterdaySummary.adherencePercentage}% adherence. Standard clinical load maintained, with priority elevation on yesterday's missed anchor ($gapLabel).",
                    priorityTaskId = priorityId,
                    priorityTaskLabel = "Adaptive Priority ($gapLabel)",
                    tomorrowProjection = "Hit >= 80% adherence today to unlock Progressive Overload and advance your rhythm streak tomorrow."
                )
            }

            // Case 3: High Adherence (>= 80%) -> Progressive Overload
            else -> {
                AdaptiveProtocolState(
                    difficulty = AdaptiveDifficulty.PROGRESSIVE_OVERLOAD,
                    yesterdaySummary = yesterdaySummary,
                    headline = "Auto-Calibrated: Progressive Overload",
                    rationale = "Yesterday achieved ${yesterdaySummary.adherencePercentage}% adherence (Streak Qualified). Protocol auto-scaled timer durations and focus stimulus (+35%) for deepened circadian synchronization.",
                    priorityTaskId = null,
                    priorityTaskLabel = null,
                    tomorrowProjection = "Maintain >= 80% today to anchor multi-day neurochemical momentum and peak recovery stability."
                )
            }
        }
    }
}
