package com.tabdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupInputClassifierTest {
    @Test fun plainTextIsNotBackup() = assertEquals(
        BackupInputClassifier.Kind.NOT_BACKUP,
        BackupInputClassifier.classify("https://example.com"),
    )

    @Test fun malformedBackupShapeIsStillRecognized() = assertEquals(
        BackupInputClassifier.Kind.BACKUP_SHAPED,
        BackupInputClassifier.classify("{\"format\":\"tabdeck-backup\",\"tabs\":["),
    )

    @Test fun unrelatedJsonIsNotBackup() = assertEquals(
        BackupInputClassifier.Kind.NOT_BACKUP,
        BackupInputClassifier.classify("{\"hello\":\"world\"}"),
    )
}
