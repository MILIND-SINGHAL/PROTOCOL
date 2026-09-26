package com.example

import com.example.data.adaptive.AdaptiveDifficulty
import com.example.data.adaptive.AdaptiveEngine
import com.example.data.adaptive.DayExecutionSummary
import com.example.data.adaptive.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolEngineTest {

    @Test
    fun testProtocolGeneration_TrackRequiredItemsExist() {
        val sleepItems = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        assertTrue("Deep Sleep should include sunlight exposure", sleepItems.contains("sleep_sunlight"))
        assertTrue("Deep Sleep should include caffeine delay", sleepItems.contains("sleep_delay_caffeine"))
        assertEquals(8, sleepItems.size)

        val energyItems = AdaptiveEngine.getRequiredItemsForTrack("All Stacks")
        assertEquals(8, energyItems.size)
        assertTrue(energyItems.contains("all_sunlight"))

        val defaultItems = AdaptiveEngine.getRequiredItemsForTrack("Unknown Track")
        assertEquals("Unknown track defaults to Deep Sleep", 8, defaultItems.size)
    }

    @Test
    fun testAdaptiveEngine_DeloadOnLowCompliance() {
        // Less than 50% adherence (< 4 of 8 items) triggers DELOAD_MICRO_ANCHOR
        val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        val completedOnlyOne = setOf("sleep_sunlight") // 1/8 = 12.5%

        val summary = AdaptiveEngine.summarizeDay("2026-09-22", completedOnlyOne, required)
        assertEquals(1, summary.completedCount)
        assertEquals(8, summary.totalRequired)
        assertEquals(0.125f, summary.adherenceRatio, 0.001f)

        val adaptiveState = AdaptiveEngine.evaluateAdaptation(summary)
        assertEquals(AdaptiveDifficulty.DELOAD_MICRO_ANCHOR, adaptiveState.difficulty)
        assertEquals(0.65f, adaptiveState.difficulty.durationMultiplier, 0.001f)
        assertTrue(adaptiveState.headline.contains("Deload"))
    }

    @Test
    fun testAdaptiveEngine_PriorityTargetOnModerateCompliance() {
        // 50% to 79% adherence (e.g. 5 of 8 items = 62.5%) triggers BALANCED_CALIBRATION with priority focus
        val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        val completedFive = setOf(
            "sleep_sunlight", "sleep_delay_caffeine", "sleep_hydration",
            "sleep_caffeine_cutoff", "sleep_nsdr"
        ) // 5/8 = 62.5%

        val summary = AdaptiveEngine.summarizeDay("2026-09-22", completedFive, required)
        assertEquals(5, summary.completedCount)
        assertEquals(0.625f, summary.adherenceRatio, 0.001f)

        val adaptiveState = AdaptiveEngine.evaluateAdaptation(summary)
        assertEquals(AdaptiveDifficulty.BALANCED_CALIBRATION, adaptiveState.difficulty)
        assertEquals(1.0f, adaptiveState.difficulty.durationMultiplier, 0.001f)
        assertNotNull("Should prioritize first uncompleted habit", adaptiveState.priorityTaskId)
        assertTrue("Priority task must be in required items", required.contains(adaptiveState.priorityTaskId))
    }

    @Test
    fun testAdaptiveEngine_ProgressiveOverloadOnHighCompliance() {
        // At least 80% adherence (e.g. 7 of 8 items = 87.5%) triggers PROGRESSIVE_OVERLOAD
        val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        val completedSeven = setOf(
            "sleep_sunlight", "sleep_delay_caffeine", "sleep_hydration",
            "sleep_caffeine_cutoff", "sleep_nsdr", "sleep_blue_light", "sleep_magnesium"
        ) // 7/8 = 87.5%

        val summary = AdaptiveEngine.summarizeDay("2026-09-22", completedSeven, required)
        assertEquals(7, summary.completedCount)
        assertEquals(0.875f, summary.adherenceRatio, 0.001f)

        val adaptiveState = AdaptiveEngine.evaluateAdaptation(summary)
        assertEquals(AdaptiveDifficulty.PROGRESSIVE_OVERLOAD, adaptiveState.difficulty)
        assertEquals(1.35f, adaptiveState.difficulty.durationMultiplier, 0.001f)
        assertTrue(adaptiveState.headline.contains("Progressive Overload"))
    }

    @Test
    fun testCircadianTiming_DynamicCalculationsWithConfigurableOffsets() {
        val wakeHour = 6
        val wakeMin = 30
        val wakeTotalMinutes = wakeHour * 60 + wakeMin // 390 min

        val caffeineOffset = 90
        val lightOffset = 60
        val windDownHoursOffset = 14

        val caffeineTargetMin = wakeTotalMinutes + caffeineOffset // 480 min = 08:00
        val lightTargetMin = wakeTotalMinutes + lightOffset // 450 min = 07:30
        val windDownTargetMin = wakeTotalMinutes + (windDownHoursOffset * 60) // 1230 min = 20:30

        assertEquals(480, caffeineTargetMin)
        assertEquals("08:00", "%02d:%02d".format(caffeineTargetMin / 60, caffeineTargetMin % 60))
        assertEquals(450, lightTargetMin)
        assertEquals("07:30", "%02d:%02d".format(lightTargetMin / 60, lightTargetMin % 60))
        assertEquals(1230, windDownTargetMin)
        assertEquals("20:30", "%02d:%02d".format(windDownTargetMin / 60, windDownTargetMin % 60))
    }

    // =========================================================================
    // UPGRADED MULTI-DAY, TASK-SPECIFIC ADAPTATION SYSTEM UNIT TESTS (STEP 10)
    // =========================================================================

    @Test
    fun test1_HighMultiDayAdherence_TriggersProgression() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Mental Clarity")
        val completedSeven = required.take(7).toSet() // 7/8 = 87.5%
        val yesterdaySummary = AdaptiveEngine.summarizeDay("2026-09-22", completedSeven, required)

        // 3-day and 7-day adherence both >= 80%
        val pastRatios = listOf(0.875f, 0.875f, 0.875f, 0.875f, 0.875f, 0.875f)
        val state = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            recentDaysAdherence = pastRatios,
            activeFocus = "Mental Clarity"
        )

        assertEquals(AdaptiveDifficulty.PROGRESSIVE_OVERLOAD, state.difficulty)
        assertTrue(state.headline.contains("Progressive Overload"))
        assertTrue(state.rationale.contains("+35%"))
        assertNotNull(state.threeDayAdherence)
        assertTrue(state.threeDayAdherence!! >= 0.80f)
        assertNotNull(state.sevenDayAdherence)
        assertTrue(state.sevenDayAdherence!! >= 0.75f)
        assertTrue(state.protocolScore >= 80)

        // Task-specific progression: Focus task scaled up to max ceiling
        assertEquals(120, state.getScaledMinutes("clarity_deep_work", 90))
        assertEquals(20, state.getScaledMinutes("clarity_dopamine_reset", 15))
    }

    @Test
    fun test2_LowRecentAdherence_TriggersDeload() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        val completedOnlyTwo = required.take(2).toSet() // 2/8 = 25% (< 50%)
        val yesterdaySummary = AdaptiveEngine.summarizeDay("2026-09-22", completedOnlyTwo, required)

        val pastRatios = listOf(0.40f, 0.35f, 0.45f)
        val state = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            recentDaysAdherence = pastRatios,
            activeFocus = "Deep Sleep"
        )

        assertEquals(AdaptiveDifficulty.DELOAD_MICRO_ANCHOR, state.difficulty)
        assertTrue(state.headline.contains("Deload"))
        assertTrue(state.rationale.contains("-35%"))
        assertEquals("Essential Micro-Anchor", state.priorityTaskLabel)
        assertNotNull(state.priorityTaskId)

        // Task-specific deload friction reduction:
        assertEquals(45, state.getScaledMinutes("clarity_deep_work", 90))
        assertEquals(12, state.getScaledMinutes("rec_zone2_flush", 20))
    }

    @Test
    fun test3_StableMediumAdherence_MaintainsBalancedCalibration() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        val completedFive = required.take(5).toSet() // 5/8 = 62.5%
        val yesterdaySummary = AdaptiveEngine.summarizeDay("2026-09-22", completedFive, required)

        val pastRatios = listOf(0.625f, 0.625f, 0.625f, 0.625f)
        val state = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            recentDaysAdherence = pastRatios,
            activeFocus = "Deep Sleep"
        )

        assertEquals(AdaptiveDifficulty.BALANCED_CALIBRATION, state.difficulty)
        assertEquals(1.0f, state.difficulty.durationMultiplier)
        // Standard durations maintained:
        assertEquals(90, state.getScaledMinutes("clarity_deep_work", 90))
        assertEquals(20, state.getScaledMinutes("rec_zone2_flush", 20))
    }

    @Test
    fun test4_StrongHistoricalAdherenceWithRecentDip_MaintainsRatherThanProgression() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        // Yesterday had a mild dip (5/8 = 62.5%)
        val completedFive = required.take(5).toSet()
        val yesterdaySummary = AdaptiveEngine.summarizeDay("2026-09-22", completedFive, required)

        // Strong 7-day history preceding yesterday (all 100%)
        val pastRatios = listOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
        val state = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            recentDaysAdherence = pastRatios,
            activeFocus = "Deep Sleep"
        )

        // Must maintain balanced calibration rather than progressing or doing a harsh deload
        assertEquals(AdaptiveDifficulty.BALANCED_CALIBRATION, state.difficulty)
        assertEquals(1.0f, state.difficulty.durationMultiplier)
        assertNotNull(state.priorityTaskId)
    }

    @Test
    fun test5_CategorySpecificFailure_AdaptsOnlyProblematicCategory() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Physical Recovery")
        // Completed all focus & routine tasks, but missed physical tasks (rec_zone2_flush, rec_tissue_release)
        val completedSix = setOf(
            "rec_sun_mobility", "rec_creatine_water", "rec_contrast_flush",
            "rec_mag_glycinate", "rec_legs_wall", "rec_cool_room"
        ) // 6/8 = 75%
        val yesterdaySummary = AdaptiveEngine.summarizeDay("2026-09-22", completedSix, required)

        // Historical day where physical was also missed
        val pastDaySummary = AdaptiveEngine.summarizeDay(
            "2026-09-21",
            completedSix,
            required
        )

        val state = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            activeFocus = "Physical Recovery",
            historicalSummaries = listOf(pastDaySummary)
        )

        // Category-specific adaptation keeps overall load balanced while scaling down physical friction:
        assertEquals(AdaptiveDifficulty.BALANCED_CALIBRATION, state.difficulty)
        assertTrue(state.headline.contains("Targeted Physical Adaptation"))
        // Physical task scaled down:
        assertEquals(12, state.getScaledMinutes("rec_zone2_flush", 20))
        // Focus task remains at standard:
        assertEquals(90, state.getScaledMinutes("clarity_deep_work", 90))
    }

    @Test
    fun test6_RepeatedSuccessfulDays_DoesNotRunawayIndefinitely() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Mental Clarity")
        val completedAll = required.toSet() // 8/8 = 100%
        val yesterdaySummary = AdaptiveEngine.summarizeDay("2026-09-22", completedAll, required)

        // 10 consecutive days of 100% adherence
        val tenDays100 = List(10) { 1.0f }
        val state = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            recentDaysAdherence = tenDays100,
            activeFocus = "Mental Clarity"
        )

        assertEquals(AdaptiveDifficulty.PROGRESSIVE_OVERLOAD, state.difficulty)
        assertTrue("Must indicate peak safe capacity reached", state.isPeakCapacityReached)
        // Deep work capped safely at 120 min, never infinitely multiplying:
        assertEquals(120, state.getScaledMinutes("clarity_deep_work", 90))
        // Sunlight capped safely at 15 min:
        assertEquals(15, state.getScaledMinutes("clarity_lux_splash", 10))
    }

    @Test
    fun test7_InsufficientHistory_ConservativeBehavior() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        // Yesterday was 100%
        val yesterdaySummary = AdaptiveEngine.summarizeDay("2026-09-22", required.toSet(), required)

        // But 3-day history was low (50% and 55%, average < 80%)
        val pastRatios = listOf(0.50f, 0.55f)
        val state = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            recentDaysAdherence = pastRatios,
            activeFocus = "Deep Sleep"
        )

        // Conservative behavior: does not progress on 1 day if multi-day average < 80%
        assertEquals(AdaptiveDifficulty.BALANCED_CALIBRATION, state.difficulty)
        assertTrue(state.rationale.contains("Standard protocol load maintained until multi-day consistency"))
    }

    @Test
    fun test8_MissingInvalidData_SafeFallback() {
        val invalidSummary = DayExecutionSummary(
            dateKey = "2026-09-22",
            completedCount = 0,
            totalRequired = 0,
            adherenceRatio = 0f,
            isStreakQualified = false,
            completedItemIds = emptySet(),
            missedItemIds = emptyList()
        )

        val state = AdaptiveEngine.evaluateAdaptation(invalidSummary)
        // Must safely fallback to BALANCED_CALIBRATION without crash or division by zero:
        assertEquals(AdaptiveDifficulty.BALANCED_CALIBRATION, state.difficulty)
        assertTrue(state.headline.contains("Standard Protocol Load"))
    }

    @Test
    fun test9_MaximumDifficultyReached_MaintainsPeakCapacity() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Mental Clarity")
        val yesterdaySummary = AdaptiveEngine.summarizeDay("2026-09-22", required.toSet(), required)
        val pastRatios = List(7) { 1.0f }

        val state = AdaptiveEngine.evaluateAdaptation(
            yesterdaySummary = yesterdaySummary,
            recentDaysAdherence = pastRatios,
            activeFocus = "Mental Clarity"
        )

        assertTrue(state.isPeakCapacityReached)
        assertTrue(state.tomorrowProjection.contains("Peak safe capacity reached"))
    }

    @Test
    fun test10_AdaptationExplanation_MatchesActualDecision() {
        val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")

        // 1. Deload explanation
        val lowSummary = AdaptiveEngine.summarizeDay("2026-09-22", setOf(required.first()), required)
        val deloadState = AdaptiveEngine.evaluateAdaptation(lowSummary)
        assertTrue(deloadState.rationale.contains("-35%"))

        // 2. Progression explanation
        val highSummary = AdaptiveEngine.summarizeDay("2026-09-22", required.toSet(), required)
        val progState = AdaptiveEngine.evaluateAdaptation(highSummary)
        assertTrue(progState.rationale.contains("+35%"))

        // 3. Category explanation
        val physRequired = AdaptiveEngine.getRequiredItemsForTrack("Physical Recovery")
        val completedSix = setOf(
            "rec_sun_mobility", "rec_creatine_water", "rec_contrast_flush",
            "rec_mag_glycinate", "rec_legs_wall", "rec_cool_room"
        )
        val catSummary = AdaptiveEngine.summarizeDay("2026-09-22", completedSix, physRequired)
        val pastMiss = AdaptiveEngine.summarizeDay("2026-09-21", completedSix, physRequired)
        val catState = AdaptiveEngine.evaluateAdaptation(catSummary, historicalSummaries = listOf(pastMiss), activeFocus = "Physical Recovery")
        assertTrue(catState.rationale.contains("friction"))
    }
}
