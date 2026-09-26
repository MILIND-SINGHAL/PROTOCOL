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

enum class TaskCategory(val label: String) {
    FOCUS("Focus Block"),
    PHYSICAL("Physical Mobility & Conditioning"),
    ROUTINE("Morning Circadian Anchor"),
    RECOVERY("Evening Wind-Down & Sleep")
}

data class TaskDurationBounds(
    val defaultMinutes: Int,
    val minDeloadMinutes: Int,
    val maxProgressiveMinutes: Int,
    val category: TaskCategory
)

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
    val tomorrowProjection: String,
    // Multi-day metrics
    val threeDayAdherence: Float? = null,
    val sevenDayAdherence: Float? = null,
    val protocolScore: Int = 80,
    val taskDurationOverrides: Map<String, Int> = emptyMap(),
    val taskDurationMultipliers: Map<String, Float> = emptyMap(),
    val isPeakCapacityReached: Boolean = false
) {
    fun getScaledMinutes(taskId: String, defaultMinutes: Int): Int {
        taskDurationOverrides[taskId]?.let { return it }
        val customMultiplier = taskDurationMultipliers[taskId] ?: difficulty.durationMultiplier
        return (defaultMinutes * customMultiplier).toInt().coerceAtLeast(3)
    }
}

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

    // Task-specific bounds to prevent runaway progression and ensure safe upper caps
    val TASK_DURATION_BOUNDS = mapOf(
        "clarity_deep_work" to TaskDurationBounds(90, 45, 120, TaskCategory.FOCUS),
        "clarity_dopamine_reset" to TaskDurationBounds(15, 10, 20, TaskCategory.FOCUS),
        "sleep_nsdr" to TaskDurationBounds(10, 7, 20, TaskCategory.FOCUS),
        "all_nsdr" to TaskDurationBounds(15, 10, 20, TaskCategory.FOCUS),
        "rec_zone2_flush" to TaskDurationBounds(20, 12, 35, TaskCategory.PHYSICAL),
        "all_zone2" to TaskDurationBounds(20, 12, 35, TaskCategory.PHYSICAL),
        "rec_tissue_release" to TaskDurationBounds(15, 10, 20, TaskCategory.PHYSICAL),
        "rec_sun_mobility" to TaskDurationBounds(10, 7, 15, TaskCategory.PHYSICAL),
        "clarity_air_walk" to TaskDurationBounds(10, 7, 20, TaskCategory.PHYSICAL),
        "sleep_sunlight" to TaskDurationBounds(10, 7, 15, TaskCategory.ROUTINE),
        "clarity_lux_splash" to TaskDurationBounds(10, 7, 15, TaskCategory.ROUTINE),
        "all_sunlight" to TaskDurationBounds(10, 7, 15, TaskCategory.ROUTINE),
        "rec_legs_wall" to TaskDurationBounds(10, 7, 15, TaskCategory.RECOVERY)
    )

    fun getTaskCategory(taskId: String): TaskCategory {
        val clean = taskId.lowercase()
        return when {
            clean.contains("deep_work") || clean.contains("nsdr") || clean.contains("dopamine") -> TaskCategory.FOCUS
            clean.contains("zone2") || clean.contains("tissue") || clean.contains("mobility") || clean.contains("walk") -> TaskCategory.PHYSICAL
            clean.contains("mag") || clean.contains("blue") || clean.contains("temp") || clean.contains("cool") ||
                clean.contains("sunset") || clean.contains("dump") || clean.contains("neuro") || clean.contains("legs") ||
                clean.contains("contrast") -> TaskCategory.RECOVERY
            else -> TaskCategory.ROUTINE
        }
    }

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
     * Authoritative calculation for Longest Streak based on consecutive qualifying adherence days.
     * Evaluates all historical recorded dates using the exact same adherence threshold.
     */
    fun calculateLongestAdherenceStreak(
        completionsByDate: Map<String, Set<String>>,
        requiredItems: List<String>,
        minAdherence: Float = MIN_STREAK_ADHERENCE_RATIO
    ): Int {
        if (completionsByDate.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val validDateKeys = completionsByDate.keys.filter { dateStr ->
            try {
                sdf.parse(dateStr) != null
            } catch (e: Exception) {
                false
            }
        }.sorted()

        if (validDateKeys.isEmpty()) return 0

        var maxStreak = 0
        var currentRunning = 0
        var previousCalendar: Calendar? = null

        for (dateStr in validDateKeys) {
            val date = sdf.parse(dateStr) ?: continue
            val thisCal = Calendar.getInstance().apply { time = date }

            val completed = completionsByDate[dateStr].orEmpty()
            val summary = summarizeDay(dateStr, completed, requiredItems)
            val qualifies = summary.adherenceRatio >= minAdherence

            if (qualifies) {
                if (previousCalendar == null) {
                    currentRunning = 1
                } else {
                    val prevDayPlusOne = (previousCalendar.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                    val isConsecutive = prevDayPlusOne.get(Calendar.YEAR) == thisCal.get(Calendar.YEAR) &&
                            prevDayPlusOne.get(Calendar.DAY_OF_YEAR) == thisCal.get(Calendar.DAY_OF_YEAR)

                    if (isConsecutive) {
                        currentRunning++
                    } else {
                        currentRunning = 1
                    }
                }
                previousCalendar = thisCal
                if (currentRunning > maxStreak) {
                    maxStreak = currentRunning
                }
            } else {
                currentRunning = 0
                previousCalendar = null
            }
        }

        return maxStreak
    }

    /**
     * Requirement 18 & Upgraded Multi-Day, Task-Specific Adaptation Engine:
     * Evaluates 1-day + 3-day + 7-day signals -> Detects category-specific deterioration ->
     * Applies safe upper bounds -> Generates explainable, deterministic rationale.
     */
    fun evaluateAdaptation(
        yesterdaySummary: DayExecutionSummary,
        recentDaysAdherence: List<Float> = emptyList(),
        activeFocus: String = "Deep Sleep",
        historicalSummaries: List<DayExecutionSummary> = emptyList()
    ): AdaptiveProtocolState {
        // Safe fallback for invalid summary
        if (yesterdaySummary.totalRequired <= 0) {
            val fallbackSummary = DayExecutionSummary(
                dateKey = yesterdaySummary.dateKey,
                completedCount = 0,
                totalRequired = STANDARD_REQUIRED_TASK_COUNT,
                adherenceRatio = 0f,
                isStreakQualified = false,
                completedItemIds = emptySet(),
                missedItemIds = emptyList()
            )
            return AdaptiveProtocolState(
                difficulty = AdaptiveDifficulty.BALANCED_CALIBRATION,
                yesterdaySummary = fallbackSummary,
                headline = "Auto-Calibrated: Standard Protocol Load",
                rationale = "No valid protocol completion data recorded. Defaulting to standard baseline calibration.",
                tomorrowProjection = "Complete your daily protocol to calibrate adaptive adjustments."
            )
        }

        val oneDayRatio = yesterdaySummary.adherenceRatio
        val missed = yesterdaySummary.missedItemIds
        val missedCount = missed.size

        // Extract historical adherence signals (excluding today)
        val priorRatios = if (historicalSummaries.isNotEmpty()) {
            historicalSummaries.map { it.adherenceRatio }
        } else {
            recentDaysAdherence
        }

        // 3-day signal: Average of yesterday + up to 2 preceding days
        val threeDayList = listOf(oneDayRatio) + priorRatios.take(2)
        val threeDayAvg = if (priorRatios.isNotEmpty()) threeDayList.average().toFloat() else null

        // 7-day signal: Average of yesterday + up to 6 preceding days
        val sevenDayList = listOf(oneDayRatio) + priorRatios.take(6)
        val sevenDayAvg = if (priorRatios.size >= 3) sevenDayList.average().toFloat() else null

        // Calculate deterministic Protocol Score (0-100)
        val protocolScore = when {
            sevenDayAvg != null && threeDayAvg != null ->
                ((sevenDayAvg * 0.40f + threeDayAvg * 0.35f + oneDayRatio * 0.25f) * 100f).toInt().coerceIn(0, 100)
            threeDayAvg != null ->
                ((threeDayAvg * 0.60f + oneDayRatio * 0.40f) * 100f).toInt().coerceIn(0, 100)
            else ->
                (oneDayRatio * 100f).toInt().coerceIn(0, 100)
        }

        // Task category analysis across recent window
        val allMissedItems = yesterdaySummary.missedItemIds + historicalSummaries.take(6).flatMap { it.missedItemIds }
        val categoryMisses = allMissedItems.groupBy { getTaskCategory(it) }
        val physicalMisses = categoryMisses[TaskCategory.PHYSICAL]?.size ?: 0
        val focusMisses = categoryMisses[TaskCategory.FOCUS]?.size ?: 0

        // Deterioration detection
        val isRecentDrop = sevenDayAvg != null && threeDayAvg != null && (sevenDayAvg - threeDayAvg >= 0.20f && threeDayAvg < 0.60f)
        val isThreeDayLow = threeDayAvg != null && threeDayAvg < 0.60f
        val isOneDayLow = oneDayRatio < 0.50f

        // Category-specific failure detection: one category failing while others succeed
        val isOnlyPhysicalFailing = physicalMisses >= 2 && focusMisses == 0 && oneDayRatio >= 0.50f
        val isOnlyFocusFailing = focusMisses >= 2 && physicalMisses == 0 && oneDayRatio >= 0.50f

        // Consecutive successful days count
        val consecutiveSuccessCount = (listOf(oneDayRatio) + priorRatios)
            .takeWhile { it >= MIN_STREAK_ADHERENCE_RATIO }
            .size

        // Progression condition: Requires sustained adherence, not a single lucky day
        val qualifiesForProgression = if (threeDayAvg != null && sevenDayAvg != null) {
            oneDayRatio >= MIN_STREAK_ADHERENCE_RATIO && threeDayAvg >= MIN_STREAK_ADHERENCE_RATIO && sevenDayAvg >= 0.75f
        } else if (threeDayAvg != null) {
            oneDayRatio >= MIN_STREAK_ADHERENCE_RATIO && threeDayAvg >= MIN_STREAK_ADHERENCE_RATIO
        } else {
            // Single-day history fallback
            oneDayRatio >= MIN_STREAK_ADHERENCE_RATIO
        }

        // Deload condition: Low 1-day or low 3-day average or sharp multi-day deterioration
        val qualifiesForDeload = isOneDayLow || isThreeDayLow || isRecentDrop

        return when {
            // -------------------------------------------------------------
            // CASE 1: DELOAD / RECOVERY
            // -------------------------------------------------------------
            qualifiesForDeload -> {
                val priorityId = missed.firstOrNull()
                val deloadOverrides = mutableMapOf<String, Int>()
                TASK_DURATION_BOUNDS.forEach { (taskId, bounds) ->
                    deloadOverrides[taskId] = bounds.minDeloadMinutes
                }

                val deloadRationale = when {
                    isRecentDrop ->
                        "Recent 3-day adherence dropped to ${(threeDayAvg!! * 100).toInt()}% from 7-day average of ${(sevenDayAvg!! * 100).toInt()}%. Protocol scaled down timer durations (-35%) to reduce habit friction and restore consistency without burnout."
                    isThreeDayLow ->
                        "Recent 3-day adherence average is ${(threeDayAvg!! * 100).toInt()}% (Yesterday: ${yesterdaySummary.adherencePercentage}%). Protocol scaled down timer durations (-35%) to reduce habit friction and restore consistency without burnout."
                    else ->
                        "Yesterday's adherence was ${yesterdaySummary.adherencePercentage}% ($missedCount items missed). Protocol scaled down timer durations (-35%) to reduce habit friction and restore consistency without burnout."
                }

                AdaptiveProtocolState(
                    difficulty = AdaptiveDifficulty.DELOAD_MICRO_ANCHOR,
                    yesterdaySummary = yesterdaySummary,
                    headline = "Auto-Calibrated: Deload & Micro-Anchor",
                    rationale = deloadRationale,
                    priorityTaskId = priorityId,
                    priorityTaskLabel = "Essential Micro-Anchor",
                    tomorrowProjection = "Complete >= 65% today to graduate from Deload into Standard Balanced Calibration tomorrow.",
                    threeDayAdherence = threeDayAvg,
                    sevenDayAdherence = sevenDayAvg,
                    protocolScore = protocolScore,
                    taskDurationOverrides = deloadOverrides,
                    taskDurationMultipliers = mapOf(
                        "clarity_deep_work" to 0.65f,
                        "rec_zone2_flush" to 0.65f,
                        "sleep_nsdr" to 0.70f,
                        "all_nsdr" to 0.70f
                    )
                )
            }

            // -------------------------------------------------------------
            // CASE 2: CATEGORY-SPECIFIC TARGETED ADAPTATION (MAINTAIN)
            // -------------------------------------------------------------
            isOnlyPhysicalFailing -> {
                val physicalMissedTask = allMissedItems.firstOrNull { getTaskCategory(it) == TaskCategory.PHYSICAL }
                    ?: missed.firstOrNull()
                val targetedOverrides = mutableMapOf<String, Int>()
                // Scale down ONLY physical items to eliminate specific exercise friction
                TASK_DURATION_BOUNDS.filter { it.value.category == TaskCategory.PHYSICAL }.forEach { (taskId, bounds) ->
                    targetedOverrides[taskId] = bounds.minDeloadMinutes
                }

                AdaptiveProtocolState(
                    difficulty = AdaptiveDifficulty.BALANCED_CALIBRATION,
                    yesterdaySummary = yesterdaySummary,
                    headline = "Auto-Calibrated: Targeted Physical Adaptation",
                    rationale = "Focus adherence remained strong, while physical tasks encountered friction ($physicalMisses misses). Physical protocol load adjusted (-35%) to reduce exercise friction while maintaining cognitive work blocks.",
                    priorityTaskId = physicalMissedTask,
                    priorityTaskLabel = "Adaptive Priority (Physical Mobility & Conditioning)",
                    tomorrowProjection = "Complete physical session at adjusted duration to restore full balanced calibration tomorrow.",
                    threeDayAdherence = threeDayAvg,
                    sevenDayAdherence = sevenDayAvg,
                    protocolScore = protocolScore,
                    taskDurationOverrides = targetedOverrides,
                    taskDurationMultipliers = mapOf(
                        "rec_zone2_flush" to 0.65f,
                        "rec_tissue_release" to 0.65f,
                        "all_zone2" to 0.65f
                    )
                )
            }

            isOnlyFocusFailing -> {
                val focusMissedTask = allMissedItems.firstOrNull { getTaskCategory(it) == TaskCategory.FOCUS }
                    ?: missed.firstOrNull()
                val targetedOverrides = mutableMapOf<String, Int>()
                TASK_DURATION_BOUNDS.filter { it.value.category == TaskCategory.FOCUS }.forEach { (taskId, bounds) ->
                    targetedOverrides[taskId] = bounds.minDeloadMinutes
                }

                AdaptiveProtocolState(
                    difficulty = AdaptiveDifficulty.BALANCED_CALIBRATION,
                    yesterdaySummary = yesterdaySummary,
                    headline = "Auto-Calibrated: Targeted Focus Adaptation",
                    rationale = "Physical and routine habits maintained, while focus blocks encountered friction ($focusMisses misses). Focus duration temporarily scaled to 45 min to lower cognitive startup friction.",
                    priorityTaskId = focusMissedTask,
                    priorityTaskLabel = "Adaptive Priority (Focus Block)",
                    tomorrowProjection = "Execute focused block today to restore full standard protocol tomorrow.",
                    threeDayAdherence = threeDayAvg,
                    sevenDayAdherence = sevenDayAvg,
                    protocolScore = protocolScore,
                    taskDurationOverrides = targetedOverrides,
                    taskDurationMultipliers = mapOf(
                        "clarity_deep_work" to 0.50f,
                        "clarity_dopamine_reset" to 0.65f
                    )
                )
            }

            // -------------------------------------------------------------
            // CASE 3: PROGRESSIVE LOAD (PROGRESS) WITH SAFE CEILINGS
            // -------------------------------------------------------------
            qualifiesForProgression -> {
                val isPeakCapacity = consecutiveSuccessCount >= 5
                val progressiveOverrides = mutableMapOf<String, Int>()
                TASK_DURATION_BOUNDS.forEach { (taskId, bounds) ->
                    progressiveOverrides[taskId] = bounds.maxProgressiveMinutes
                }

                val progressionRationale = when {
                    isPeakCapacity ->
                        "Yesterday achieved ${yesterdaySummary.adherencePercentage}% adherence across $consecutiveSuccessCount consecutive qualifying days. Protocol is currently operating at maximum safe duration capacity (+35%). Load maintained at peak safe ceiling."
                    threeDayAvg != null && sevenDayAvg != null ->
                        "Yesterday achieved ${yesterdaySummary.adherencePercentage}% adherence (3-Day: ${(threeDayAvg * 100).toInt()}%, 7-Day: ${(sevenDayAvg * 100).toInt()}%). Protocol auto-scaled timer durations (+35%) to support progressive focus and routine stamina."
                    threeDayAvg != null ->
                        "Yesterday achieved ${yesterdaySummary.adherencePercentage}% adherence with a ${(threeDayAvg * 100).toInt()}% 3-day average. Protocol auto-scaled timer durations (+35%) to support progressive focus and routine stamina."
                    else ->
                        "Yesterday achieved ${yesterdaySummary.adherencePercentage}% adherence (Streak Qualified). Protocol auto-scaled timer durations (+35%) to support progressive focus and routine stamina."
                }

                val projection = if (isPeakCapacity) {
                    "Peak safe capacity reached. Maintain >= 80% today to protect your protocol streak and anchor long-term adherence."
                } else {
                    "Maintain >= 80% today to sustain multi-day routine consistency and recovery habits."
                }

                AdaptiveProtocolState(
                    difficulty = AdaptiveDifficulty.PROGRESSIVE_OVERLOAD,
                    yesterdaySummary = yesterdaySummary,
                    headline = "Auto-Calibrated: Progressive Overload",
                    rationale = progressionRationale,
                    priorityTaskId = null,
                    priorityTaskLabel = null,
                    tomorrowProjection = projection,
                    threeDayAdherence = threeDayAvg,
                    sevenDayAdherence = sevenDayAvg,
                    protocolScore = protocolScore,
                    taskDurationOverrides = progressiveOverrides,
                    taskDurationMultipliers = mapOf(
                        "clarity_deep_work" to 1.33f, // 90m -> 120m cap
                        "rec_zone2_flush" to 1.35f,   // 20m -> 27m
                        "sleep_nsdr" to 1.35f,        // 10m -> 14m
                        "rec_tissue_release" to 1.33f // 15m -> 20m
                    ),
                    isPeakCapacityReached = isPeakCapacity
                )
            }

            // -------------------------------------------------------------
            // CASE 4: STANDARD / MAINTAIN (BALANCED_CALIBRATION)
            // -------------------------------------------------------------
            else -> {
                val priorityId = missed.firstOrNull()
                val gapLabel = when {
                    priorityId?.contains("sun", ignoreCase = true) == true -> "Morning Sunlight Anchor"
                    priorityId?.contains("caff", ignoreCase = true) == true -> "Caffeine Window Cutoff"
                    priorityId?.contains("nsdr", ignoreCase = true) == true -> "Midday Parasympathetic NSDR"
                    priorityId?.contains("mag", ignoreCase = true) == true -> "Evening Mineral Relaxation"
                    else -> "Evening Wind-Down"
                }

                val maintainRationale = when {
                    oneDayRatio >= MIN_STREAK_ADHERENCE_RATIO && threeDayAvg != null && threeDayAvg < MIN_STREAK_ADHERENCE_RATIO ->
                        "Yesterday reached ${yesterdaySummary.adherencePercentage}% adherence, but 3-day average is ${(threeDayAvg * 100).toInt()}%. Standard protocol load maintained until multi-day consistency qualifies for progressive load."
                    threeDayAvg != null ->
                        "Yesterday reached ${yesterdaySummary.adherencePercentage}% adherence (3-Day Average: ${(threeDayAvg * 100).toInt()}%). Standard protocol load maintained, with priority elevation on yesterday's missed anchor ($gapLabel)."
                    else ->
                        "Yesterday reached ${yesterdaySummary.adherencePercentage}% adherence. Standard protocol load maintained, with priority elevation on yesterday's missed anchor ($gapLabel)."
                }

                AdaptiveProtocolState(
                    difficulty = AdaptiveDifficulty.BALANCED_CALIBRATION,
                    yesterdaySummary = yesterdaySummary,
                    headline = "Auto-Calibrated: Targeted Calibration",
                    rationale = maintainRationale,
                    priorityTaskId = priorityId,
                    priorityTaskLabel = if (priorityId != null) "Adaptive Priority ($gapLabel)" else null,
                    tomorrowProjection = "Hit >= 80% adherence today to unlock Progressive Overload and advance your rhythm streak tomorrow.",
                    threeDayAdherence = threeDayAvg,
                    sevenDayAdherence = sevenDayAvg,
                    protocolScore = protocolScore
                )
            }
        }
    }
}
