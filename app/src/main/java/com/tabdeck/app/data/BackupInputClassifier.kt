package com.tabdeck.app.data

/** Fast, bounded pre-classification used before parsing user-selected documents as JSON. */
object BackupInputClassifier {
    enum class Kind { NOT_BACKUP, BACKUP_SHAPED }

    fun classify(raw: String?): Kind {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return Kind.NOT_BACKUP
        val sample = text.take(MAX_CLASSIFICATION_CHARS)
        return if (BACKUP_HINT.containsMatchIn(sample)) Kind.BACKUP_SHAPED else Kind.NOT_BACKUP
    }

    private const val MAX_CLASSIFICATION_CHARS = 32_768
    private val BACKUP_HINT = Regex(
        "\\\"(?:format|tabs|version|settings|bridgeToken|rules|groups|decks)\\\"\\s*:",
        RegexOption.IGNORE_CASE,
    )
}
