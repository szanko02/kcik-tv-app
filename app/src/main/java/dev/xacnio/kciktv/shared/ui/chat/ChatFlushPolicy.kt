/**
 * File: ChatFlushPolicy.kt
 *
 * Description: Pure Kotlin policy helpers for chat message batch flush intervals and delays.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.shared.ui.chat

object ChatFlushPolicy {
    const val REFRESH_RATE_REALTIME_MS = 0L
    const val REALTIME_FRAME_COALESCE_MS = 16L
    const val DEFAULT_REFRESH_RATE_MS = 100L
    const val SLOW_TIMERS_REFRESH_RATE_MS = 1500L
    const val THERMAL_SEVERE_FLOOR_MS = 500L
    const val MAX_REFRESH_RATE_MS = 1500L

    val SUPPORTED_REFRESH_RATES_MS = listOf(0L, 50L, 100L, 200L, 500L, 1000L, 1500L)

    fun normalizeRefreshRateMs(rawMs: Long): Long =
        rawMs.coerceIn(REFRESH_RATE_REALTIME_MS, MAX_REFRESH_RATE_MS)

    fun effectiveIntervalMs(
        configuredRateMs: Long,
        lowBatteryModeEnabled: Boolean,
        batterySaverSlowTimers: Boolean,
        thermalLevel: Int
    ): Long {
        if (lowBatteryModeEnabled && batterySaverSlowTimers) return SLOW_TIMERS_REFRESH_RATE_MS
        val clamped = normalizeRefreshRateMs(configuredRateMs)
        val thermalFloor = if (thermalLevel >= 2) THERMAL_SEVERE_FLOOR_MS else 0L
        return maxOf(clamped, thermalFloor)
    }

    fun computeNextFlushDelayMs(
        effectiveIntervalMs: Long,
        nowMs: Long,
        lastFlushMs: Long
    ): Long {
        if (effectiveIntervalMs <= 0L || lastFlushMs <= 0L) return REALTIME_FRAME_COALESCE_MS
        val elapsed = (nowMs - lastFlushMs).coerceAtLeast(0L)
        if (elapsed >= effectiveIntervalMs) return REALTIME_FRAME_COALESCE_MS
        return (effectiveIntervalMs - elapsed).coerceAtLeast(REALTIME_FRAME_COALESCE_MS)
    }

    /**
     * One-time migration helper for users upgrading from <= 2.5.8 where `1500L` was the default
     * written by "Reset Chat to Defaults". Returns [DEFAULT_REFRESH_RATE_MS] when a stored `1500L`
     * has not yet been migrated; returns `null` when no migration write is needed.
     */
    fun migrateLegacyDefaultRefreshRateMs(storedMs: Long?, alreadyMigrated: Boolean): Long? {
        if (alreadyMigrated) return null
        if (storedMs == SLOW_TIMERS_REFRESH_RATE_MS) return DEFAULT_REFRESH_RATE_MS
        return null
    }
}
