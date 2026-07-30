package com.tabdeck.app.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.tabdeck.app.model.LibraryQuery
import com.tabdeck.app.model.TabSort
import java.util.Locale

object TabQueryBuilder {
    fun select(query: LibraryQuery, staleBefore: Long, limit: Int? = null): SupportSQLiteQuery =
        build(query, staleBefore, countOnly = false, limit = limit)

    fun count(query: LibraryQuery, staleBefore: Long): SupportSQLiteQuery =
        build(query, staleBefore, countOnly = true, limit = null)

    private fun build(
        query: LibraryQuery,
        staleBefore: Long,
        countOnly: Boolean,
        limit: Int?,
    ): SupportSQLiteQuery {
        val sql = StringBuilder(if (countOnly) "SELECT COUNT(*) FROM tabs" else "SELECT * FROM tabs")
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        if (query.statuses.isNotEmpty()) {
            clauses += "status IN (${placeholders(query.statuses.size)})"
            args += query.statuses.map { it.name }
        }
        if (query.browsers.isNotEmpty()) {
            clauses += "browser IN (${placeholders(query.browsers.size)})"
            args += query.browsers.map { it.name }
        }
        if (query.groups.isNotEmpty()) {
            clauses += "assignedGroup IN (${placeholders(query.groups.size)})"
            args += query.groups.sorted()
        }
        if (query.sourceDevices.isNotEmpty()) {
            clauses += "sourceDevice IN (${placeholders(query.sourceDevices.size)})"
            args += query.sourceDevices.sorted()
        }
        if (query.sourceGroups.isNotEmpty()) {
            clauses += "sourceGroup IN (${placeholders(query.sourceGroups.size)})"
            args += query.sourceGroups.sorted()
        }
        if (query.pinnedOnly) clauses += "pinned = 1"
        if (query.hasNotesOnly) clauses += "TRIM(notes) != ''"
        if (query.staleOnly) {
            clauses += "lastSeenAtEpochMs < ?"
            args += staleBefore
        }
        query.tags.asSequence().map(String::trim).filter(String::isNotBlank).take(MAX_TAG_FILTERS).forEach { tag ->
            clauses += "tagsJson LIKE ? ESCAPE '\\'"
            args += "%\"${escapeLike(tag)}\"%"
        }

        val tokens = query.search.trim()
            .split(Regex("\\s+"))
            .asSequence()
            .map { it.lowercase(Locale.ROOT).take(MAX_TOKEN_LENGTH) }
            .filter(String::isNotBlank)
            .take(MAX_SEARCH_TOKENS)
            .toList()
        tokens.forEach { token ->
            val value = "%${escapeLike(token)}%"
            clauses += "(" + SEARCH_COLUMNS.joinToString(" OR ") { "LOWER($it) LIKE ? ESCAPE '\\'" } + ")"
            repeat(SEARCH_COLUMNS.size) { args += value }
        }

        if (clauses.isNotEmpty()) sql.append(" WHERE ").append(clauses.joinToString(" AND "))
        if (!countOnly) {
            val (column, naturalDescending) = when (query.sort) {
                TabSort.IMPORTED_NEWEST -> "importedAtEpochMs" to true
                TabSort.IMPORTED_OLDEST -> "importedAtEpochMs" to false
                TabSort.LAST_SEEN -> "lastSeenAtEpochMs" to true
                TabSort.CREATED_NEWEST -> "createdAtEpochMs" to true
                TabSort.TITLE -> "title COLLATE NOCASE" to false
                TabSort.HOST -> "host COLLATE NOCASE" to false
                TabSort.GROUP -> "assignedGroup COLLATE NOCASE" to false
                TabSort.BROWSER -> "browser COLLATE NOCASE" to false
                TabSort.TRANSFER_COUNT -> "transferCount" to true
            }
            val descending = when (query.sort) {
                TabSort.IMPORTED_NEWEST, TabSort.IMPORTED_OLDEST -> naturalDescending
                else -> query.descending
            }
            sql.append(" ORDER BY pinned DESC, ").append(column).append(if (descending) " DESC" else " ASC")
            sql.append(", importedAtEpochMs DESC, id COLLATE NOCASE")
            limit?.coerceIn(1, MAX_LIMIT)?.let {
                sql.append(" LIMIT ?")
                args += it
            }
        }
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    private fun placeholders(size: Int): String = List(size) { "?" }.joinToString(",")

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private val SEARCH_COLUMNS = listOf("title", "url", "host", "notes", "tagsJson", "sourceGroup", "assignedGroup")
    private const val MAX_SEARCH_TOKENS = 8
    private const val MAX_TOKEN_LENGTH = 96
    private const val MAX_TAG_FILTERS = 16
    private const val MAX_LIMIT = 25_000
}
