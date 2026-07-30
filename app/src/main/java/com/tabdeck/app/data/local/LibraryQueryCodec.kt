package com.tabdeck.app.data.local

import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.LibraryQuery
import com.tabdeck.app.model.TabSort
import com.tabdeck.app.model.TabStatus
import org.json.JSONArray
import org.json.JSONObject

object LibraryQueryCodec {
    fun encode(query: LibraryQuery): String = JSONObject()
        .put("version", 2)
        .put("search", query.search.take(MAX_SEARCH))
        .put("statuses", JSONArray(query.statuses.map { it.name }.sorted()))
        .put("browsers", JSONArray(query.browsers.map { it.name }.sorted()))
        .put("groups", JSONArray(query.groups.map { it.take(MAX_GROUP) }.sorted()))
        .put("sourceDevices", JSONArray(query.sourceDevices.map { it.take(MAX_DEVICE) }.sorted()))
        .put("sourceGroups", JSONArray(query.sourceGroups.map { it.take(MAX_SOURCE_GROUP) }.sorted()))
        .put("tags", JSONArray(query.tags.map { it.take(MAX_TAG) }.sorted()))
        .put("pinnedOnly", query.pinnedOnly)
        .put("hasNotesOnly", query.hasNotesOnly)
        .put("staleOnly", query.staleOnly)
        .put("sort", query.sort.name)
        .put("descending", query.descending)
        .toString()

    fun decode(raw: String): LibraryQuery = runCatching {
        val root = JSONObject(raw)
        LibraryQuery(
            search = root.optString("search").trim().take(MAX_SEARCH),
            statuses = root.optJSONArray("statuses").enumSet(TabStatus::valueOf).ifEmpty { setOf(TabStatus.ACTIVE) },
            browsers = root.optJSONArray("browsers").enumSet(BrowserId::valueOf),
            groups = root.optJSONArray("groups").stringSet(MAX_GROUP),
            sourceDevices = root.optJSONArray("sourceDevices").stringSet(MAX_DEVICE),
            sourceGroups = root.optJSONArray("sourceGroups").stringSet(MAX_SOURCE_GROUP),
            tags = root.optJSONArray("tags").stringSet(MAX_TAG),
            pinnedOnly = root.optBoolean("pinnedOnly"),
            hasNotesOnly = root.optBoolean("hasNotesOnly"),
            staleOnly = root.optBoolean("staleOnly"),
            sort = runCatching { TabSort.valueOf(root.optString("sort")) }.getOrDefault(TabSort.IMPORTED_NEWEST),
            descending = root.optBoolean("descending", true),
        )
    }.getOrDefault(LibraryQuery())

    private fun <T> JSONArray?.enumSet(mapper: (String) -> T): Set<T> = buildSet {
        val source = this@enumSet ?: return@buildSet
        for (index in 0 until minOf(source.length(), MAX_SET_ITEMS)) {
            runCatching { mapper(source.optString(index)) }.getOrNull()?.let(::add)
        }
    }

    private fun JSONArray?.stringSet(maxLength: Int): Set<String> = buildSet {
        val source = this@stringSet ?: return@buildSet
        for (index in 0 until minOf(source.length(), MAX_SET_ITEMS)) {
            source.optString(index).trim().take(maxLength).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private const val MAX_SEARCH = 500
    private const val MAX_GROUP = 80
    private const val MAX_DEVICE = 120
    private const val MAX_SOURCE_GROUP = 120
    private const val MAX_TAG = 40
    private const val MAX_SET_ITEMS = 128
}
