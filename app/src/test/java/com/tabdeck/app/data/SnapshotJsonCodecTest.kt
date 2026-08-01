package com.tabdeck.app.data

import com.tabdeck.app.model.AppSettings
import com.tabdeck.app.model.AppSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotJsonCodecTest {
    @Test
    fun decodeClassifiedCoversEveryOutcome() {
        assertTrue(
            SnapshotJsonCodec.decodeClassified(
                """{"format":"tabdeck-backup","version":3,"tabs":[]}""",
            ) is SnapshotJsonCodec.DecodeResult.Success,
        )
        assertTrue(
            SnapshotJsonCodec.decodeClassified(
                """{"format":"tabdeck-backup","version":99,"tabs":[]}""",
            ) is SnapshotJsonCodec.DecodeResult.Rejected,
        )
        assertTrue(
            SnapshotJsonCodec.decodeClassified(
                """{"format":"other-app","version":3,"tabs":[]}""",
            ) is SnapshotJsonCodec.DecodeResult.Rejected,
        )
        assertTrue(
            SnapshotJsonCodec.decodeClassified(
                """{"format":"tabdeck-backup","version":3}""",
            ) is SnapshotJsonCodec.DecodeResult.Rejected,
        )
        assertTrue(
            SnapshotJsonCodec.decodeClassified(
                """{"format":"tabdeck-backup","version":3,"tabs":[}""",
            ) is SnapshotJsonCodec.DecodeResult.Rejected,
        )
        assertTrue(
            SnapshotJsonCodec.decodeClassified("""{"hello":"world"}""") is
                SnapshotJsonCodec.DecodeResult.NotBackup,
        )
        assertTrue(
            SnapshotJsonCodec.decodeClassified("https://example.com") is
                SnapshotJsonCodec.DecodeResult.NotBackup,
        )
    }

    @Test
    fun maintenancePreferencesRoundTrip() {
        val encoded = SnapshotJsonCodec.encode(
            AppSnapshot(
                settings = AppSettings(
                    automaticMaintenanceEnabled = false,
                    trashRetentionDays = 75,
                ),
            ),
        )
        val decoded = SnapshotJsonCodec.decode(encoded) ?: error("backup did not decode")
        assertEquals(false, decoded.settings.automaticMaintenanceEnabled)
        assertEquals(75, decoded.settings.trashRetentionDays)
    }
}
