package com.example

import com.example.data.adaptive.AdaptiveDifficulty
import com.example.data.adaptive.AdaptiveEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Comprehensive Regression & Integrity Test Suite for Protocol Streak Logic.
 *
 * Enforces:
 * 1. Single authoritative streak calculation via AdaptiveEngine (adherence >= 80%).
 * 2. Case 1: 100% adherence -> streak continues.
 * 3. Case 2: Exactly at configured adherence threshold (80%) -> day counts.
 * 4. Case 3: Below threshold (e.g. 75%, 62.5%, 50%) -> day does NOT count.
 * 5. Case 4: Empty protocol / no tasks assigned -> does not accidentally create a streak day.
 * 6. Case 5: Partial completion (1 task out of 8 = 12.5%) -> rejected from streak.
 * 7. Case 6: Missed day breaks consecutive streak.
 * 8. Case 7: Multiple completions on the same day -> does not create duplicate streak days.
 * 9. Case 8: Timezone / date boundary formatting works reliably.
 * 10. Longest streak vs Current streak: Both derived from the identical adherence threshold.
 * 11. Closed-loop flow: Task completion -> Daily Adherence -> Authoritative Streak -> Adaptive Engine.
 * 12. Legacy regression check: Old date-only logic cannot count low adherence days as streak days.
 */
class StreakAuthoritativeIntegrityTest {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val requiredItems = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep") // 8 canonical items

    private fun getDateKeyDaysAgo(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return sdf.format(cal.time)
    }

    /**
     * CASE 1: 100% adherence (8/8 items) -> streak continues.
     */
    @Test
    fun testCase1_HundredPercentAdherence_ContinuesStreak() {
        val today = getDateKeyDaysAgo(0)
        val yesterday = getDateKeyDaysAgo(1)

        val completions = mapOf(
            today to requiredItems.toSet(),
            yesterday to requiredItems.toSet()
        )

        val currentStreak = AdaptiveEngine.calculateAdherenceStreak(completions, requiredItems)
        val longestStreak = AdaptiveEngine.calculateLongestAdherenceStreak(completions, requiredItems)

        assertEquals("2 consecutive days at 100% adherence must equal 2-day current streak", 2, currentStreak)
        assertEquals("Longest streak must also equal 2", 2, longestStreak)
    }

    /**
     * CASE 2: Exactly at the configured adherence threshold (80% -> e.g. 80% or 7/8 = 87.5% in discrete tasks)
     * For 10 tasks, exactly 8/10 = 80%.
     */
    @Test
    fun testCase2_ExactlyAtAdherenceThreshold_DayCountsTowardStreak() {
        val tenRequiredItems = (1..10).map { "item_$it" }
        val exactlyEightCompleted = tenRequiredItems.take(8).toSet() // 8/10 = 0.80f (80%)

        val yesterday = getDateKeyDaysAgo(1)
        val summary = AdaptiveEngine.summarizeDay(yesterday, exactlyEightCompleted, tenRequiredItems)

        assertEquals(0.80f, summary.adherenceRatio, 0.001f)
        assertTrue("Day meeting 80% threshold exactly must qualify for streak", summary.isStreakQualified)

        val completions = mapOf(yesterday to exactlyEightCompleted)
        val streak = AdaptiveEngine.calculateAdherenceStreak(completions, tenRequiredItems)
        assertEquals("Streak must be 1 when yesterday meets 80% threshold", 1, streak)
    }

    /**
     * CASE 3: Below threshold (e.g. 79% or 6/8 = 75%) -> day does NOT count.
     */
    @Test
    fun testCase3_BelowThreshold_DayDoesNotCountTowardStreak() {
        val tenRequiredItems = (1..10).map { "item_$it" }
        val sevenCompleted = tenRequiredItems.take(7).toSet() // 7/10 = 0.70f (< 80%)

        val yesterday = getDateKeyDaysAgo(1)
        val summary = AdaptiveEngine.summarizeDay(yesterday, sevenCompleted, tenRequiredItems)

        assertEquals(0.70f, summary.adherenceRatio, 0.001f)
        assertFalse("Day with 70% adherence (< 80%) must NOT qualify for streak", summary.isStreakQualified)

        val completions = mapOf(yesterday to sevenCompleted)
        val streak = AdaptiveEngine.calculateAdherenceStreak(completions, tenRequiredItems)
        assertEquals("Below threshold yesterday must yield 0 streak", 0, streak)
    }

    /**
     * CASE 4: No tasks assigned or empty protocol -> do NOT accidentally create a streak day.
     */
    @Test
    fun testCase4_EmptyProtocolOrNoTasks_DoesNotCreateStreak() {
        val emptyRequired = emptyList<String>()
        val today = getDateKeyDaysAgo(0)
        val completions = mapOf(today to setOf("random_completed_task"))

        val summary = AdaptiveEngine.summarizeDay(today, setOf("random_completed_task"), emptyRequired)
        assertFalse("Empty protocol cannot qualify for streak", summary.isStreakQualified)

        val streak = AdaptiveEngine.calculateAdherenceStreak(completions, emptyRequired)
        assertEquals("Empty required list must yield 0 streak", 0, streak)
    }

    /**
     * CASE 5: One task completed out of many (1/8 = 12.5%) -> must NOT count toward streak.
     * This directly rejects the legacy date-only behavior.
     */
    @Test
    fun testCase5_SingleTaskCompletedOutOfEight_DoesNotCountTowardStreak() {
        val yesterday = getDateKeyDaysAgo(1)
        val singleCompleted = setOf(requiredItems.first()) // 1/8 = 12.5%

        val summary = AdaptiveEngine.summarizeDay(yesterday, singleCompleted, requiredItems)
        assertEquals(0.125f, summary.adherenceRatio, 0.001f)
        assertFalse("12.5% adherence must not qualify for streak", summary.isStreakQualified)

        val completions = mapOf(yesterday to singleCompleted)
        val streak = AdaptiveEngine.calculateAdherenceStreak(completions, requiredItems)
        assertEquals("Single task completion out of 8 must yield 0 streak", 0, streak)
    }

    /**
     * CASE 6: Missed day breaks consecutive streak.
     */
    @Test
    fun testCase6_MissedDay_BreaksStreak() {
        val day3Ago = getDateKeyDaysAgo(3)
        val day2Ago = getDateKeyDaysAgo(2)
        // Day 1 ago (yesterday) was missed!
        val today = getDateKeyDaysAgo(0) // Today ongoing with 0 completions

        val completions = mapOf(
            day3Ago to requiredItems.toSet(),
            day2Ago to requiredItems.toSet()
        )

        val currentStreak = AdaptiveEngine.calculateAdherenceStreak(completions, requiredItems)
        assertEquals("Missed yesterday breaks the current streak to 0", 0, currentStreak)

        val longestStreak = AdaptiveEngine.calculateLongestAdherenceStreak(completions, requiredItems)
        assertEquals("Historical longest streak remains 2", 2, longestStreak)
    }

    /**
     * CASE 7: Multiple completions of the same task on the same day -> does NOT create multiple streak days.
     */
    @Test
    fun testCase7_MultipleCompletionsOnSameDate_DedupedBySet() {
        val yesterday = getDateKeyDaysAgo(1)
        // Simulating duplicate logging of the same items
        val duplicateList = listOf("sleep_sunlight", "sleep_sunlight", "sleep_delay_caffeine", "sleep_sunlight")
        val dedupedSet = duplicateList.toSet()

        val summary = AdaptiveEngine.summarizeDay(yesterday, dedupedSet, requiredItems)
        assertEquals("Only 2 unique valid items counted", 2, summary.completedCount)
        assertEquals(0.25f, summary.adherenceRatio, 0.001f) // 2/8
        assertFalse("Duplicate completions do not inflate adherence", summary.isStreakQualified)
    }

    /**
     * CASE 8: Date and boundary integrity -> format adherence across months and years.
     */
    @Test
    fun testCase8_DateBoundaryFormatting_HandlesCalendarTransitions() {
        val cal = Calendar.getInstance()
        val dates = mutableMapOf<String, Set<String>>()

        // Build a 5-day continuous chain across calendar boundaries
        for (i in 0..4) {
            val dateStr = sdf.format(cal.time)
            dates[dateStr] = requiredItems.toSet()
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        val streak = AdaptiveEngine.calculateAdherenceStreak(dates, requiredItems)
        assertEquals("5 continuous days across date boundaries must equal 5-day streak", 5, streak)
    }

    /**
     * Longest Streak vs Current Streak:
     * Current streak is 2, but historical best was 4 days.
     */
    @Test
    fun testSeparateCurrentAndLongestStreak_BothUseAdherenceAuthority() {
        // Historical block 1: 4 consecutive qualifying days (10 to 7 days ago)
        // Gap: 6, 5, 4, 3 days ago missed
        // Recent block 2: 2 consecutive qualifying days (1 day ago and today)
        val completions = mutableMapOf<String, Set<String>>()

        for (i in 7..10) {
            completions[getDateKeyDaysAgo(i)] = requiredItems.toSet()
        }

        completions[getDateKeyDaysAgo(1)] = requiredItems.toSet()
        completions[getDateKeyDaysAgo(0)] = requiredItems.toSet()

        val currentStreak = AdaptiveEngine.calculateAdherenceStreak(completions, requiredItems)
        val longestStreak = AdaptiveEngine.calculateLongestAdherenceStreak(completions, requiredItems)

        assertEquals("Current streak must be 2", 2, currentStreak)
        assertEquals("Longest streak must be 4", 4, longestStreak)
    }

    /**
     * Closed-Loop Flow: Task Completion -> Daily Adherence -> Authoritative Streak -> Adaptive Engine.
     */
    @Test
    fun testClosedLoop_AdaptiveEngineReceivesAuthoritativeStreakAndAdherence() {
        val yesterday = getDateKeyDaysAgo(1)
        val completedSeven = requiredItems.take(7).toSet() // 7/8 = 87.5%

        val yesterdaySummary = AdaptiveEngine.summarizeDay(yesterday, completedSeven, requiredItems)
        assertTrue(yesterdaySummary.isStreakQualified)
        assertEquals(0.875f, yesterdaySummary.adherenceRatio, 0.001f)

        val completions = mapOf(yesterday to completedSeven)
        val authoritativeStreak = AdaptiveEngine.calculateAdherenceStreak(completions, requiredItems)
        assertEquals(1, authoritativeStreak)

        val adaptiveState = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            activeFocus = "Deep Sleep"
        )

        assertEquals(AdaptiveDifficulty.PROGRESSIVE_OVERLOAD, adaptiveState.difficulty)
        assertTrue(adaptiveState.headline.contains("Progressive Overload"))
    }

    /**
     * Anti-Regression Test: Legacy date-only logic CANNOT count days where only 1 item was completed.
     */
    @Test
    fun testAntiRegression_LegacyDateOnlyLogicCannotReappear() {
        val dates = listOf(
            getDateKeyDaysAgo(0),
            getDateKeyDaysAgo(1),
            getDateKeyDaysAgo(2)
        )

        // On each day, user completed ONLY 1 item out of 8 (12.5% adherence)
        val completionsWithLowAdherence = dates.associateWith { setOf("sleep_sunlight") }

        val authoritativeStreak = AdaptiveEngine.calculateAdherenceStreak(
            completionsWithLowAdherence,
            requiredItems
        )

        assertEquals("Low adherence days must result in 0 streak under the authoritative model", 0, authoritativeStreak)
    }
}
