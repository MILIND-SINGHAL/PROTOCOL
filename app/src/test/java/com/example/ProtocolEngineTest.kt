package com.example

import com.example.data.adaptive.AdaptiveDifficulty
import com.example.data.adaptive.AdaptiveEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        // Configurable offsets:
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
}
