package com.tabdeck.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenancePolicyTest {
    @Test
    fun retentionIsAlwaysPositive() {
        assertEquals(1, MaintenancePolicy.retentionDays(0))
        assertEquals(1, MaintenancePolicy.retentionDays(-10))
        assertEquals(45, MaintenancePolicy.retentionDays(45))
    }

    @Test
    fun cutoffIsBoundedAndDeterministic() {
        val now = 100L * MaintenancePolicy.DAY_MS
        assertEquals(70L * MaintenancePolicy.DAY_MS, MaintenancePolicy.pruneBeforeEpochMs(now, 30))
        assertEquals(0L, MaintenancePolicy.pruneBeforeEpochMs(0L, Int.MAX_VALUE))
    }
}
