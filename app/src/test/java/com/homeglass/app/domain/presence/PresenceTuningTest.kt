package com.homeglass.app.domain.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceTuningTest {

    @Test
    fun `defaults are inside the allowed range`() {
        assertEquals(PresenceTuning.DEFAULT_THRESHOLD_CM, PresenceTuning.clampThreshold(PresenceTuning.DEFAULT_THRESHOLD_CM))
        assertEquals(PresenceTuning.DEFAULT_COOLDOWN_MS, PresenceTuning.clampCooldown(PresenceTuning.DEFAULT_COOLDOWN_MS))
    }

    @Test
    fun `threshold is clamped to the slider range`() {
        assertEquals(PresenceTuning.MIN_THRESHOLD_CM, PresenceTuning.clampThreshold(-5f), 0.001f)
        assertEquals(PresenceTuning.MAX_THRESHOLD_CM, PresenceTuning.clampThreshold(10_000f), 0.001f)
        assertEquals(120f, PresenceTuning.clampThreshold(120f), 0.001f)
    }

    @Test
    fun `a broken stored threshold falls back to the default`() {
        assertEquals(PresenceTuning.DEFAULT_THRESHOLD_CM, PresenceTuning.clampThreshold(Float.NaN), 0.001f)
    }

    @Test
    fun `cooldown is clamped to the slider range`() {
        assertEquals(PresenceTuning.MIN_COOLDOWN_MS, PresenceTuning.clampCooldown(0L))
        assertEquals(PresenceTuning.MAX_COOLDOWN_MS, PresenceTuning.clampCooldown(600_000L))
        assertEquals(7_000L, PresenceTuning.clampCooldown(7_000L))
    }
}
