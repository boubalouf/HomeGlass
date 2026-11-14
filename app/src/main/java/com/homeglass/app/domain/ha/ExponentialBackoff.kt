package com.homeglass.app.domain.ha

import kotlin.random.Random

/**
 * Backoff exponentiel des reconnexions.
 *
 * 1 s, 2 s, 4 s… plafonné à [maxMillis], avec un jitter proportionnel pour éviter que
 * plusieurs clients ne retentent au même instant. [reset] est appelé après une connexion
 * réussie. Pas de `System` ici : [random] est injectable, les tests sont donc déterministes.
 */
class ExponentialBackoff(
    private val initialMillis: Long = 1_000L,
    private val maxMillis: Long = 30_000L,
    private val multiplier: Double = 2.0,
    private val jitterRatio: Double = 0.2,
    private val random: Random = Random.Default,
) {
    private var attempts: Int = 0

    /** Nombre de tentatives depuis la dernière connexion réussie. */
    val attempt: Int get() = attempts

    /** Délai à attendre avant la prochaine tentative. */
    fun nextDelayMillis(): Long {
        val exponent = attempts.coerceAtMost(MAX_EXPONENT)
        val base = (initialMillis * multiplier.pow(exponent)).toLong().coerceAtMost(maxMillis)
        attempts++
        val jitter = (base * jitterRatio).toLong()
        val offset = if (jitter <= 0L) 0L else random.nextLong(-jitter, jitter + 1)
        return (base + offset).coerceIn(initialMillis / 2, maxMillis)
    }

    fun reset() {
        attempts = 0
    }

    private fun Double.pow(exponent: Int): Double {
        var result = 1.0
        repeat(exponent) { result *= this }
        return result
    }

    private companion object {
        /** 2^30 dépasse déjà [maxMillis] : inutile de dépasser. */
        const val MAX_EXPONENT = 30
    }
}
