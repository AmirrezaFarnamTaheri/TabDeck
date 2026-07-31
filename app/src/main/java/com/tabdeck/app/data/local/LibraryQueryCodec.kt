package com.tabdeck.app.data.local

import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.LibraryQuery
import com.tabdeck.app.model.TabSort
import com.tabdeck.app.model.TabStatus
import org.json.JSONArray
import org.json.JSONObject

/** Portable codec for saved library queries. Values are normalized, never silently truncated. */
object LibraryQueryCodec {
    fun encode(query: LibraryQuery): String = JSONObject()
        .put("version", 2)
        .put("search", query.search)
        .put("statuses", JSONArray(query.statuses.map(Enum<*>::name).sorted()))
        .put("browsers", JSONArray(query.browsers.map(Enum<*>::name).sorted()))
        .put("groups", JSONArray(query.groups.sorted()))
        .put("sourceDevices", JSONArray(query.sourceDevices.sorted()))
        .put("sourceGroups", JSONArray(query.sourceGroups.sorted()))
        .put("tags", JSONArray(query.tags.sorted()))
        .put("pinnedOnly", query.pinnedOnly)
        .put("hasNotesOnly", query.hasNotesOnly)
        .put("staleOnly", query.staleOnly)
        .put("sort", query.sort.name)
        .put("descending", query.descending)
        .toString()

    fun decode(raw: String?): LibraryQuery {
        val root = runCatching { JSONObject(raw.orEmpty()) }.getOrNull() ?: return LibraryQuery()
        return LibraryQuery(
            search = root.optString("search").trim(),
            statuses = root.optJSONArray("statuses").enumSet<TabStatus>().ifEmpty { setOf(TabStatus.ACTIVE) },
            browsers = root.optJSONArray("browsers").enumSet(),
            groups = root.optJSONArray("groups").stringSet(),
            sourceDevices = root.optJSONArray("sourceDevices").stringSet(),
            sourceGroups = root.optJSONArray("sourceGroups").stringSet(),
            tags = root.optJSONArray("tags").stringSet(),
            pinnedOnly = root.optBoolean("pinnedOnly", false),
            hasNotesOnly = root.optBoolean("hasNotesOnly", false),
            staleOnly = root.optBoolean("staleOnly", false),
            sort = enumOrDefault(root.optString("sort"), TabSort.IMPORTED_NEWEST),
            descending = root.optBoolean("descending", true),
        )
    }

    private inline fun <reified T : Enum<T>> JSONArray?.enumSet(): Set<T> = buildSet {
        val source = this@enumSet ?: return@buildSet
        for (index in 0 until source.length()) {
            runCatching { enumValueOf<T>(source.optString(index)) }.getOrNull()?.let(::add)
        }
    }

    private fun JSONArray?.stringSet(): Set<String> = buildSet {
        val source = this@stringSet ?: return@buildSet
        for (index in 0 until source.length()) {
            source.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)
}
