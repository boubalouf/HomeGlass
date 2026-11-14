package com.homeglass.app.domain.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresencePlannerTest {

    @Test
    fun `auto combines both detectors when available`() {
        val plan = PresencePlanner.plan(PresenceSource.Auto, hasProximity = true, hasCamera = true)

        assertTrue(plan.useProximity)
        assertTrue(plan.useCamera)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun `auto falls back to the camera without a proximity sensor`() {
        val plan = PresencePlanner.plan(PresenceSource.Auto, hasProximity = false, hasCamera = true)

        assertFalse(plan.useProximity)
        assertTrue(plan.useCamera)
    }

    @Test
    fun `explicit proximity mode never starts the camera`() {
        val plan = PresencePlanner.plan(PresenceSource.Proximity, hasProximity = true, hasCamera = true)

        assertTrue(plan.useProximity)
        assertFalse(plan.useCamera)
    }

    @Test
    fun `explicit camera mode never starts the sensor`() {
        val plan = PresencePlanner.plan(PresenceSource.Camera, hasProximity = true, hasCamera = true)

        assertFalse(plan.useProximity)
        assertTrue(plan.useCamera)
    }

    @Test
    fun `a missing hardware produces an empty plan`() {
        val plan = PresencePlanner.plan(PresenceSource.Camera, hasProximity = false, hasCamera = false)

        assertTrue(plan.isEmpty)
    }
}
