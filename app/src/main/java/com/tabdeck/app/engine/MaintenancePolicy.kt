package com.tabdeck.app.engine

/** Pure maintenance timing rules shared by WorkManager, settings, and tests. */
object MaintenancePolicy {
    const val DAY_MS: Long = 86_400_000L
    const val DEFAULT_RETENTION_DAYS: Int = 30

    fun retentionDays(value: Int): Int = value.coerceAtLeast(1)

    fun pruneBeforeEpochMs(nowEpochMs: Long, retentionDays: Int): Long {
        val safeNow = nowEpochMs.coerceAtLeast(0L)
        val retentionMs = retentionDays(retentionDays).toLong() * DAY_MS
        return (safeNow - retentionMs).coerceAtLeast(0L)
    }
}
