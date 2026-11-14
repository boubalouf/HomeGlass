package com.homeglass.app.domain.ha

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExponentialBackoffTest {

    private fun backoff() = ExponentialBackoff(
        initialMillis = 1_000L,
        maxMillis = 30_000L,
        multiplier = 2.0,
        jitterRatio = 0.0,
        random = Random(7),
    )

    @Test
    fun `delays double after each attempt`() {
        val backoff = backoff()
        assertEquals(1_000L, backoff.nextDelayMillis())
        assertEquals(2_000L, backoff.nextDelayMillis())
        assertEquals(4_000L, backoff.nextDelayMillis())
        assertEquals(3, backoff.attempt)
    }

    @Test
    fun `delay is capped at the maximum`() {
        val backoff = backoff()
        var last = 0L
        repeat(20) { last = backoff.nextDelayMillis() }
        assertEquals(30_000L, last)
    }

    @Test
    fun `reset restarts the sequence`() {
        val backoff = backoff()
        repeat(4) { backoff.nextDelayMillis() }
        backoff.reset()
        assertEquals(0, backoff.attempt)
        assertEquals(1_000L, backoff.nextDelayMillis())
    }

    @Test
    fun `jitter stays within bounds`() {
        val backoff = ExponentialBackoff(
            initialMillis = 1_000L,
            maxMillis = 30_000L,
            jitterRatio = 0.2,
            random = Random(42),
        )
        repeat(30) {
            val delay = backoff.nextDelayMillis()
            assertTrue("délai hors bornes : $delay", delay in 500L..30_000L)
        }
    }
}
