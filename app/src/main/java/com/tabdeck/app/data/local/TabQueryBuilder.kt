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

    fun requireSupported(query: LibraryQuery) {
        validateBindBudget(
            query = query,
            cleanTagCount = cleanTags(query).size,
            searchTokenCount = searchTokens(query.search).size,
            includeLimit = true,
        )
    }

    private fun build(
        query: LibraryQuery,
        staleBefore: Long,
        countOnly: Boolean,
        limit: Int?,
    ): SupportSQLiteQuery {
        val sql = StringBuilder(if (countOnly) "SELECT COUNT(*) FROM tabs" else "SELECT * FROM tabs")
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        val cleanTags = cleanTags(query)
        val tokens = searchTokens(query.search)

        validateBindBudget(
            query = query,
            cleanTagCount = cleanTags.size,
            searchTokenCount = tokens.size,
            includeLimit = !countOnly && limit != null && limit > 0,
        )

        if (query.statuses.isNotEmpty()) {
            clauses += "status IN (${placeholders(query.statuses.size)})"
            args.addAll(query.statuses.map { it.name })
        }
        if (query.browsers.isNotEmpty()) {
            clauses += "browser IN (${placeholders(query.browsers.size)})"
            args.addAll(query.browsers.map { it.name })
        }
        if (query.groups.isNotEmpty()) {
            clauses += "assignedGroup IN (${placeholders(query.groups.size)})"
            args.addAll(query.groups.sorted())
        }
        if (query.sourceDevices.isNotEmpty()) {
            clauses += "sourceDevice IN (${placeholders(query.sourceDevices.size)})"
            args.addAll(query.sourceDevices.sorted())
        }
        if (query.sourceGroups.isNotEmpty()) {
            clauses += "sourceGroup IN (${placeholders(query.sourceGroups.size)})"
            args.addAll(query.sourceGroups.sorted())
        }
        if (query.pinnedOnly) clauses += "pinned = 1"
        if (query.hasNotesOnly) clauses += "TRIM(notes) != ''"
        if (query.staleOnly) {
            clauses += "lastSeenAtEpochMs < ?"
            args += staleBefore
        }
        cleanTags.forEach { tag ->
            clauses += "tagsJson LIKE ? ESCAPE '\\'"
            args += "%\"${escapeLike(tag)}\"%"
        }

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
            limit?.takeIf { it > 0 }?.let {
                sql.append(" LIMIT ?")
                args += it
            }
        }
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    private fun placeholders(size: Int): String = List(size) { "?" }.joinToString(",")

    private fun cleanTags(query: LibraryQuery): List<String> = query.tags.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()

    private fun searchTokens(search: String): List<String> = search.trim()
        .split(Regex("\\s+"))
        .asSequence()
        .map { it.lowercase(Locale.ROOT) }
        .filter(String::isNotBlank)
        .distinct()
        .toList()

    private fun validateBindBudget(
        query: LibraryQuery,
        cleanTagCount: Int,
        searchTokenCount: Int,
        includeLimit: Boolean,
    ) {
        val required = query.statuses.size +
            query.browsers.size +
            query.groups.size +
            query.sourceDevices.size +
            query.sourceGroups.size +
            cleanTagCount +
            (if (query.staleOnly) 1 else 0) +
            (searchTokenCount * SEARCH_COLUMNS.size) +
            (if (includeLimit) 1 else 0)
        require(required <= SQLITE_MAX_BIND_ARGUMENTS) {
            "Query requires $required SQLite bind arguments; the supported maximum is $SQLITE_MAX_BIND_ARGUMENTS. " +
                "Reduce the combined number of filters or unique search terms."
        }
    }

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private val SEARCH_COLUMNS = listOf("title", "url", "host", "notes", "tagsJson", "sourceGroup", "assignedGroup")
    private const val SQLITE_MAX_BIND_ARGUMENTS = 999
}
