package dev.xacnio.kciktv.shared.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatFlushPolicyTest {

    @Test
    fun constants_matchExpectedValues() {
        assertEquals(0L, ChatFlushPolicy.REFRESH_RATE_REALTIME_MS)
        assertEquals(16L, ChatFlushPolicy.REALTIME_FRAME_COALESCE_MS)
        assertEquals(100L, ChatFlushPolicy.DEFAULT_REFRESH_RATE_MS)
        assertEquals(1500L, ChatFlushPolicy.SLOW_TIMERS_REFRESH_RATE_MS)
        assertEquals(500L, ChatFlushPolicy.THERMAL_SEVERE_FLOOR_MS)
        assertEquals(1500L, ChatFlushPolicy.MAX_REFRESH_RATE_MS)
        assertEquals(listOf(0L, 50L, 100L, 200L, 500L, 1000L, 1500L), ChatFlushPolicy.SUPPORTED_REFRESH_RATES_MS)
    }

    @Test
    fun normalizeRefreshRateMs_clampsWithinSupportedBounds() {
        assertEquals(0L, ChatFlushPolicy.normalizeRefreshRateMs(-100L))
        assertEquals(0L, ChatFlushPolicy.normalizeRefreshRateMs(0L))
        assertEquals(100L, ChatFlushPolicy.normalizeRefreshRateMs(100L))
        assertEquals(1500L, ChatFlushPolicy.normalizeRefreshRateMs(1500L))
        assertEquals(1500L, ChatFlushPolicy.normalizeRefreshRateMs(5000L))
    }

    @Test
    fun effectiveIntervalMs_respectsBatterySaverAndThermalFloor() {
        // Low battery + slow timers overrides everything to 1500ms
        assertEquals(
            1500L,
            ChatFlushPolicy.effectiveIntervalMs(
                configuredRateMs = 0L,
                lowBatteryModeEnabled = true,
                batterySaverSlowTimers = true,
                thermalLevel = 0
            )
        )

        // Low battery without slowTimers does not override
        assertEquals(
            100L,
            ChatFlushPolicy.effectiveIntervalMs(
                configuredRateMs = 100L,
                lowBatteryModeEnabled = true,
                batterySaverSlowTimers = false,
                thermalLevel = 0
            )
        )

        // Thermal level < 2 applies no floor
        assertEquals(
            50L,
            ChatFlushPolicy.effectiveIntervalMs(
                configuredRateMs = 50L,
                lowBatteryModeEnabled = false,
                batterySaverSlowTimers = true,
                thermalLevel = 1
            )
        )

        // Thermal level >= 2 enforces 500ms floor
        assertEquals(
            500L,
            ChatFlushPolicy.effectiveIntervalMs(
                configuredRateMs = 100L,
                lowBatteryModeEnabled = false,
                batterySaverSlowTimers = false,
                thermalLevel = 2
            )
        )

        // Thermal floor does not lower higher configured interval
        assertEquals(
            1000L,
            ChatFlushPolicy.effectiveIntervalMs(
                configuredRateMs = 1000L,
                lowBatteryModeEnabled = false,
                batterySaverSlowTimers = false,
                thermalLevel = 3
            )
        )
    }

    @Test
    fun computeNextFlushDelayMs_calculatesCoalescedDelay() {
        // Realtime or first flush coalesces at 16ms
        assertEquals(16L, ChatFlushPolicy.computeNextFlushDelayMs(0L, 1000L, 950L))
        assertEquals(16L, ChatFlushPolicy.computeNextFlushDelayMs(100L, 1000L, 0L))

        // Elapsed >= interval -> 16ms
        assertEquals(16L, ChatFlushPolicy.computeNextFlushDelayMs(100L, 1100L, 1000L))
        assertEquals(16L, ChatFlushPolicy.computeNextFlushDelayMs(100L, 1250L, 1000L))

        // Elapsed < interval -> remaining time, floored at 16ms
        assertEquals(60L, ChatFlushPolicy.computeNextFlushDelayMs(100L, 1040L, 1000L))
        assertEquals(16L, ChatFlushPolicy.computeNextFlushDelayMs(100L, 1095L, 1000L))

        // Clock regression handled via coerceAtLeast(0L)
        assertEquals(100L, ChatFlushPolicy.computeNextFlushDelayMs(100L, 900L, 1000L))
    }

    @Test
    fun migrateLegacyDefaultRefreshRateMs_upgradesOnlyUnmigrated1500Default() {
        assertEquals(100L, ChatFlushPolicy.migrateLegacyDefaultRefreshRateMs(1500L, alreadyMigrated = false))
        assertNull(ChatFlushPolicy.migrateLegacyDefaultRefreshRateMs(1500L, alreadyMigrated = true))
        assertNull(ChatFlushPolicy.migrateLegacyDefaultRefreshRateMs(null, alreadyMigrated = false))
        assertNull(ChatFlushPolicy.migrateLegacyDefaultRefreshRateMs(200L, alreadyMigrated = false))
        assertNull(ChatFlushPolicy.migrateLegacyDefaultRefreshRateMs(500L, alreadyMigrated = false))
    }
}
