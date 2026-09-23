package com.example

import com.example.data.adaptive.AdaptiveEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StreakCalculatorTest {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val requiredItems = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")

    private fun getDateOffset(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return dateFormat.format(cal.time)
    }

    @Test
    fun testStreakCalculation_EmptyCompletionsReturnsZero() {
        val completionsByDate = emptyMap<String, Set<String>>()
        val streak = AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
        assertEquals(0, streak)
    }

    @Test
    fun testStreakCalculation_LowDailyAdherenceYieldsZeroStreak() {
        // 3 consecutive days where user completed only 1 of 8 items (12.5% adherence < 80% threshold)
        val day0 = getDateOffset(0)
        val day1 = getDateOffset(1)
        val day2 = getDateOffset(2)

        val completionsByDate = mapOf(
            day0 to setOf("sleep_sunlight"),
            day1 to setOf("sleep_sunlight"),
            day2 to setOf("sleep_sunlight")
        )

        val streak = AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
        assertEquals("Adherence below 80% MUST NOT count towards consecutive streak", 0, streak)
    }

    @Test
    fun testStreakCalculation_AdherenceAtOrAbove80PercentCalculatesConsecutiveStreak() {
        // 3 consecutive days where user completed 7 of 8 items (87.5% >= 80% threshold)
        val day0 = getDateOffset(0)
        val day1 = getDateOffset(1)
        val day2 = getDateOffset(2)

        val sevenItems = requiredItems.take(7).toSet()
        val completionsByDate = mapOf(
            day0 to sevenItems,
            day1 to sevenItems,
            day2 to sevenItems
        )

        val streak = AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
        assertEquals("3 consecutive days meeting >= 80% adherence yields 3-day streak", 3, streak)
    }

    @Test
    fun testStreakCalculation_InterveningLowAdherenceDayBreaksStreak() {
        val day0 = getDateOffset(0) // Today: 7 items (passed)
        val day1 = getDateOffset(1) // Yesterday: 1 item (failed)
        val day2 = getDateOffset(2) // 2 days ago: 7 items (passed)

        val sevenItems = requiredItems.take(7).toSet()
        val oneItem = setOf(requiredItems.first())

        val completionsByDate = mapOf(
            day0 to sevenItems,
            day1 to oneItem,
            day2 to sevenItems
        )

        val streak = AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
        assertEquals("Intervening day below 80% breaks streak sequence (only today counts)", 1, streak)
    }

    @Test
    fun testStreakCalculation_DeduplicatesMultipleCompletionsOnSameDay() {
        val day0 = getDateOffset(0)

        // Multiple entries of the same item result in a set of size 1 (12.5% adherence -> 0 streak)
        val singleItemSet = setOf("sleep_sunlight")
        val completionsByDate = mapOf(day0 to singleItemSet)

        val streak = AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
        assertEquals("Single completed item results in 0 streak (< 80% threshold)", 0, streak)
    }
}
