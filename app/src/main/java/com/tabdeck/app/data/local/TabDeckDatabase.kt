package com.tabdeck.app.data.local

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tabdeck.app.engine.UrlNormalizer
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.DashboardStats
import com.tabdeck.app.model.DeckDefinition
import com.tabdeck.app.model.FacetCount
import com.tabdeck.app.model.GroupDefinition
import com.tabdeck.app.model.ImportSession
import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.RegexTarget
import com.tabdeck.app.model.SmartView
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TabStatus
import com.tabdeck.app.model.TransferEvent
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(
    tableName = "tabs",
    indices = [
        Index("normalizedUrl"),
        Index("hostPath"),
        Index("host"),
        Index("assignedGroup"),
        Index("browser"),
        Index("status"),
        Index("importedAtEpochMs"),
        Index("lastSeenAtEpochMs"),
        Index("pinned"),
        Index("sourceDevice"),
        Index("sourceGroup"),
        Index(value = ["status", "importedAtEpochMs"]),
        Index(value = ["status", "assignedGroup"]),
        Index(value = ["status", "browser"]),
        Index(value = ["status", "normalizedUrl"]),
        Index(value = ["sourceDevice", "browser", "sourceTabId"]),
    ],
)
data class TabEntity(
    @androidx.room.PrimaryKey val id: String,
    val url: String,
    val normalizedUrl: String,
    val host: String,
    val hostPath: String,
    val title: String,
    val browser: String,
    val sourceGroup: String,
    val assignedGroup: String,
    val createdAtEpochMs: Long,
    val importedAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
    val pinned: Boolean,
    val notes: String,
    val tagsJson: String,
    val status: String,
    val snoozedUntilEpochMs: Long?,
    val sourceDevice: String,
    val sourceTabId: String,
    val lastTransferredAtEpochMs: Long?,
    val transferCount: Int,
)

@Entity(tableName = "regex_rules", indices = [Index("priority"), Index("enabled")])
data class RegexRuleEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val pattern: String,
    val target: String,
    val destinationGroup: String,
    val priority: Int,
    val enabled: Boolean,
    val ignoreCase: Boolean,
    val addTagsJson: String,
    val stopAfterMatch: Boolean,
)

@Entity(tableName = "groups", indices = [Index(value = ["name"], unique = true), Index("sortOrder")])
data class GroupEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val colorKey: String,
    val iconKey: String,
    val sortOrder: Int,
    val isSystem: Boolean,
)

@Entity(tableName = "transfer_history", indices = [Index("createdAtEpochMs")])
data class TransferEntity(
    @androidx.room.PrimaryKey val id: String,
    val targetBrowser: String,
    val attempted: Int,
    val opened: Int,
    val failed: Int,
    val cancelled: Boolean,
    val durationMs: Long,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "import_history", indices = [Index("createdAtEpochMs")])
data class ImportEntity(
    @androidx.room.PrimaryKey val id: String,
    val source: String,
    val sourceLabel: String,
    val received: Int,
    val accepted: Int,
    val rejected: Int,
    val deviceName: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "smart_views", indices = [Index("pinned"), Index("sortOrder")])
data class SmartViewEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val queryJson: String,
    val iconKey: String,
    val colorKey: String,
    val pinned: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "decks", indices = [Index("updatedAtEpochMs")])
data class DeckEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val colorKey: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "deck_tabs",
    primaryKeys = ["deckId", "tabId"],
    foreignKeys = [
        ForeignKey(entity = DeckEntity::class, parentColumns = ["id"], childColumns = ["deckId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TabEntity::class, parentColumns = ["id"], childColumns = ["tabId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("deckId"), Index("tabId"), Index(value = ["deckId", "position"])],
)
data class DeckTabEntity(
    val deckId: String,
    val tabId: String,
    val position: Int,
    val addedAtEpochMs: Long,
)

data class DashboardStatsRow(
    val total: Int,
    val active: Int,
    val archived: Int,
    val snoozed: Int,
    val trashed: Int,
    val pinned: Int,
    val inbox: Int,
    val untitled: Int,
    val stale: Int,
    val transferred: Int,
)

data class FacetCountRow(val key: String, val count: Int)
data class DuplicateKeyRow(val key: String, val count: Int)
data class DeckSummaryRow(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val colorKey: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val tabCount: Int,
)

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY importedAtEpochMs DESC")
    suspend fun listAll(): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<TabEntity>

    @RawQuery(observedEntities = [TabEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, TabEntity>

    @RawQuery
    suspend fun queryTabs(query: SupportSQLiteQuery): List<TabEntity>

    @RawQuery
    suspend fun queryCount(query: SupportSQLiteQuery): Int

    @Query("""
        SELECT
            COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active,
            COALESCE(SUM(CASE WHEN status = 'ARCHIVED' THEN 1 ELSE 0 END), 0) AS archived,
            COALESCE(SUM(CASE WHEN status = 'SNOOZED' THEN 1 ELSE 0 END), 0) AS snoozed,
            COALESCE(SUM(CASE WHEN status = 'TRASHED' THEN 1 ELSE 0 END), 0) AS trashed,
            COALESCE(SUM(CASE WHEN pinned = 1 AND status != 'TRASHED' THEN 1 ELSE 0 END), 0) AS pinned,
            COALESCE(SUM(CASE WHEN assignedGroup = 'Inbox' AND status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS inbox,
            COALESCE(SUM(CASE WHEN TRIM(title) = '' AND status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS untitled,
            COALESCE(SUM(CASE WHEN lastSeenAtEpochMs < :staleBefore AND status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS stale,
            COALESCE(SUM(CASE WHEN transferCount > 0 THEN 1 ELSE 0 END), 0) AS transferred
        FROM tabs
    """)
    fun observeStats(staleBefore: Long): Flow<DashboardStatsRow>

    @Query("SELECT assignedGroup AS `key`, COUNT(*) AS count FROM tabs WHERE status = 'ACTIVE' GROUP BY assignedGroup ORDER BY count DESC, assignedGroup LIMIT :limit")
    fun observeGroupCounts(limit: Int = 24): Flow<List<FacetCountRow>>

    @Query("SELECT browser AS `key`, COUNT(*) AS count FROM tabs WHERE status = 'ACTIVE' GROUP BY browser ORDER BY count DESC, browser LIMIT :limit")
    fun observeBrowserCounts(limit: Int = 24): Flow<List<FacetCountRow>>

    @Query("SELECT sourceDevice AS `key`, COUNT(*) AS count FROM tabs WHERE status = 'ACTIVE' AND sourceDevice != '' GROUP BY sourceDevice ORDER BY count DESC, sourceDevice LIMIT :limit")
    fun observeSourceDeviceCounts(limit: Int = 32): Flow<List<FacetCountRow>>

    @Query("SELECT sourceGroup AS `key`, COUNT(*) AS count FROM tabs WHERE status = 'ACTIVE' AND sourceGroup != '' GROUP BY sourceGroup ORDER BY count DESC, sourceGroup LIMIT :limit")
    fun observeSourceGroupCounts(limit: Int = 32): Flow<List<FacetCountRow>>

    @Query("SELECT COALESCE(SUM(copies), 0) FROM (SELECT COUNT(*) - 1 AS copies FROM tabs WHERE status = 'ACTIVE' GROUP BY normalizedUrl HAVING COUNT(*) > 1)")
    fun observeDuplicateCopies(): Flow<Int>

    @Query("SELECT * FROM tabs WHERE status = 'ACTIVE' ORDER BY importedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 12): Flow<List<TabEntity>>

    @Query("SELECT COUNT(*) FROM tabs")
    suspend fun count(): Int

    @Query("SELECT * FROM tabs WHERE sourceDevice = :sourceDevice AND browser = :browser AND sourceTabId IN (:sourceTabIds)")
    suspend fun findBySourceIds(sourceDevice: String, browser: String, sourceTabIds: List<String>): List<TabEntity>

    @Query("SELECT sourceTabId FROM tabs WHERE sourceDevice = :sourceDevice AND browser = :browser AND sourceTabId != ''")
    suspend fun sourceIdsForIdentity(sourceDevice: String, browser: String): List<String>

    @Query("UPDATE tabs SET status = 'ARCHIVED', snoozedUntilEpochMs = NULL WHERE sourceDevice = :sourceDevice AND browser = :browser AND sourceTabId != '' AND sourceTabId NOT IN (:presentIds)")
    suspend fun archiveMissingSourceTabs(sourceDevice: String, browser: String, presentIds: List<String>): Int

    @Query("UPDATE tabs SET status = 'ARCHIVED', snoozedUntilEpochMs = NULL WHERE sourceDevice = :sourceDevice AND browser = :browser AND sourceTabId != ''")
    suspend fun archiveAllSourceTabs(sourceDevice: String, browser: String): Int

    @Query("SELECT url AS `key`, COUNT(*) AS count FROM tabs WHERE status = 'ACTIVE' GROUP BY url HAVING COUNT(*) > 1 ORDER BY count DESC LIMIT :limit")
    suspend fun exactDuplicateKeys(limit: Int): List<DuplicateKeyRow>

    @Query("SELECT normalizedUrl AS `key`, COUNT(*) AS count FROM tabs WHERE status = 'ACTIVE' GROUP BY normalizedUrl HAVING COUNT(*) > 1 ORDER BY count DESC LIMIT :limit")
    suspend fun normalizedDuplicateKeys(limit: Int): List<DuplicateKeyRow>

    @Query("SELECT hostPath AS `key`, COUNT(*) AS count FROM tabs WHERE status = 'ACTIVE' GROUP BY hostPath HAVING COUNT(*) > 1 ORDER BY count DESC LIMIT :limit")
    suspend fun hostPathDuplicateKeys(limit: Int): List<DuplicateKeyRow>

    @Query("SELECT * FROM tabs WHERE status = 'ACTIVE' AND url IN (:keys)")
    suspend fun tabsForExactKeys(keys: List<String>): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE status = 'ACTIVE' AND normalizedUrl IN (:keys)")
    suspend fun tabsForNormalizedKeys(keys: List<String>): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE status = 'ACTIVE' AND hostPath IN (:keys)")
    suspend fun tabsForHostPathKeys(keys: List<String>): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE status = 'ACTIVE' ORDER BY importedAtEpochMs DESC LIMIT :limit OFFSET :offset")
    suspend fun activePage(limit: Int, offset: Int): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TabEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TabEntity)

    @Query("UPDATE tabs SET assignedGroup = :groupName WHERE id IN (:ids)")
    suspend fun assignGroup(ids: Set<String>, groupName: String)

    @Query("UPDATE tabs SET assignedGroup = :newName WHERE assignedGroup = :oldName")
    suspend fun renameAssignedGroup(oldName: String, newName: String)

    @Query("UPDATE tabs SET status = :status, snoozedUntilEpochMs = NULL WHERE id IN (:ids)")
    suspend fun setStatus(ids: Set<String>, status: String)

    @Query("UPDATE tabs SET status = :status, snoozedUntilEpochMs = :untilEpochMs WHERE id IN (:ids)")
    suspend fun snooze(ids: Set<String>, status: String, untilEpochMs: Long)

    @Query("UPDATE tabs SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE tabs SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: Set<String>, pinned: Boolean)

    @Query("UPDATE tabs SET lastTransferredAtEpochMs = :atEpochMs, transferCount = transferCount + 1 WHERE id IN (:ids)")
    suspend fun markTransferred(ids: Set<String>, atEpochMs: Long)

    @Query("DELETE FROM tabs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<String>)

    @Query("DELETE FROM tabs WHERE status = 'TRASHED'")
    suspend fun emptyTrash()

    @Query("DELETE FROM tabs WHERE status = 'TRASHED' AND lastSeenAtEpochMs < :cutoff")
    suspend fun pruneTrash(cutoff: Long): Int

    @Query("UPDATE tabs SET status = 'ACTIVE', snoozedUntilEpochMs = NULL WHERE status = 'SNOOZED' AND snoozedUntilEpochMs IS NOT NULL AND snoozedUntilEpochMs <= :now")
    suspend fun wakeDueTabs(now: Long): Int

    @Query("DELETE FROM tabs")
    suspend fun deleteAll()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM regex_rules ORDER BY priority, name")
    fun observeAll(): Flow<List<RegexRuleEntity>>

    @Query("SELECT * FROM regex_rules ORDER BY priority, name")
    suspend fun listAll(): List<RegexRuleEntity>

    @Query("SELECT COUNT(*) FROM regex_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RegexRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RegexRuleEntity>)

    @Query("DELETE FROM regex_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE regex_rules SET destinationGroup = :newName WHERE destinationGroup = :oldName")
    suspend fun renameDestinationGroup(oldName: String, newName: String)

    @Query("DELETE FROM regex_rules")
    suspend fun deleteAll()
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY sortOrder, name")
    suspend fun listAll(): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GroupEntity>)

    @Query("DELETE FROM groups WHERE id = :id AND isSystem = 0")
    suspend fun delete(id: String)

    @Query("DELETE FROM groups")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM transfer_history ORDER BY createdAtEpochMs DESC LIMIT 100")
    fun observeTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM import_history ORDER BY createdAtEpochMs DESC LIMIT 100")
    fun observeImports(): Flow<List<ImportEntity>>

    @Query("SELECT * FROM transfer_history ORDER BY createdAtEpochMs DESC LIMIT 100")
    suspend fun listTransfers(): List<TransferEntity>

    @Query("SELECT * FROM import_history ORDER BY createdAtEpochMs DESC LIMIT 100")
    suspend fun listImports(): List<ImportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(item: TransferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImport(item: ImportEntity)

    @Query("DELETE FROM transfer_history")
    suspend fun deleteTransfers()

    @Query("DELETE FROM import_history")
    suspend fun deleteImports()
}

@Dao
interface SmartViewDao {
    @Query("SELECT * FROM smart_views ORDER BY pinned DESC, sortOrder, name")
    fun observeAll(): Flow<List<SmartViewEntity>>

    @Query("SELECT * FROM smart_views ORDER BY pinned DESC, sortOrder, name")
    suspend fun listAll(): List<SmartViewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SmartViewEntity)

    @Query("DELETE FROM smart_views WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM smart_views")
    suspend fun deleteAll()
}

@Dao
interface DeckDao {
    @Query("""
        SELECT d.id, d.name, d.description, d.iconKey, d.colorKey, d.createdAtEpochMs, d.updatedAtEpochMs,
               COUNT(dt.tabId) AS tabCount
        FROM decks d
        LEFT JOIN deck_tabs dt ON dt.deckId = d.id
        GROUP BY d.id
        ORDER BY d.updatedAtEpochMs DESC, d.name
    """)
    fun observeSummaries(): Flow<List<DeckSummaryRow>>

    @Query("""
        SELECT d.id, d.name, d.description, d.iconKey, d.colorKey, d.createdAtEpochMs, d.updatedAtEpochMs,
               COUNT(dt.tabId) AS tabCount
        FROM decks d
        LEFT JOIN deck_tabs dt ON dt.deckId = d.id
        GROUP BY d.id
        ORDER BY d.updatedAtEpochMs DESC, d.name
    """)
    suspend fun listSummaries(): List<DeckSummaryRow>

    @Query("SELECT tabId FROM deck_tabs WHERE deckId = :deckId ORDER BY position")
    suspend fun tabIdsForDeck(deckId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeck(item: DeckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeckTabs(items: List<DeckTabEntity>)

    @Query("DELETE FROM deck_tabs WHERE deckId = :deckId")
    suspend fun clearDeckTabs(deckId: String)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: String)

    @Query("SELECT t.* FROM tabs t INNER JOIN deck_tabs dt ON dt.tabId = t.id WHERE dt.deckId = :deckId ORDER BY dt.position")
    suspend fun tabsForDeck(deckId: String): List<TabEntity>

    @Query("DELETE FROM decks")
    suspend fun deleteAllDecks()
}

@Database(
    entities = [
        TabEntity::class,
        RegexRuleEntity::class,
        GroupEntity::class,
        TransferEntity::class,
        ImportEntity::class,
        SmartViewEntity::class,
        DeckEntity::class,
        DeckTabEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class TabDeckDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
    abstract fun ruleDao(): RuleDao
    abstract fun groupDao(): GroupDao
    abstract fun historyDao(): HistoryDao
    abstract fun smartViewDao(): SmartViewDao
    abstract fun deckDao(): DeckDao

    companion object {
        @Volatile private var instance: TabDeckDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_host ON tabs(host)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_lastSeenAtEpochMs ON tabs(lastSeenAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_pinned ON tabs(pinned)")
                db.execSQL("CREATE TABLE IF NOT EXISTS smart_views (id TEXT NOT NULL, name TEXT NOT NULL, queryJson TEXT NOT NULL, iconKey TEXT NOT NULL, colorKey TEXT NOT NULL, pinned INTEGER NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_smart_views_pinned ON smart_views(pinned)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_smart_views_sortOrder ON smart_views(sortOrder)")
                db.execSQL("CREATE TABLE IF NOT EXISTS decks (id TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, iconKey TEXT NOT NULL, colorKey TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_decks_updatedAtEpochMs ON decks(updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS deck_tabs (deckId TEXT NOT NULL, tabId TEXT NOT NULL, position INTEGER NOT NULL, addedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(deckId, tabId), FOREIGN KEY(deckId) REFERENCES decks(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tabId) REFERENCES tabs(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_deck_tabs_deckId ON deck_tabs(deckId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_deck_tabs_tabId ON deck_tabs(tabId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_deck_tabs_deckId_position ON deck_tabs(deckId, position)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_sourceDevice ON tabs(sourceDevice)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_sourceGroup ON tabs(sourceGroup)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_status_importedAtEpochMs ON tabs(status, importedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_status_assignedGroup ON tabs(status, assignedGroup)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_status_browser ON tabs(status, browser)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_status_normalizedUrl ON tabs(status, normalizedUrl)")
            }
        }

        fun get(context: Context): TabDeckDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TabDeckDatabase::class.java,
                "tabdeck.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}

fun DashboardStatsRow.toModel(duplicateCopies: Int): DashboardStats = DashboardStats(
    total = total,
    active = active,
    archived = archived,
    snoozed = snoozed,
    trashed = trashed,
    pinned = pinned,
    inbox = inbox,
    untitled = untitled,
    stale = stale,
    duplicateCopies = duplicateCopies,
    transferred = transferred,
)

fun FacetCountRow.toModel(): FacetCount = FacetCount(key, count)
fun DeckSummaryRow.toModel(): DeckDefinition = DeckDefinition(id, name, description, iconKey, colorKey, tabCount, createdAtEpochMs, updatedAtEpochMs)

fun TabItem.toEntity(): TabEntity = TabEntity(
    id = id,
    url = url,
    normalizedUrl = UrlNormalizer.normalized(url),
    host = UrlNormalizer.host(url),
    hostPath = UrlNormalizer.hostAndPath(url),
    title = title,
    browser = browser.name,
    sourceGroup = sourceGroup,
    assignedGroup = assignedGroup,
    createdAtEpochMs = createdAtEpochMs,
    importedAtEpochMs = importedAtEpochMs,
    lastSeenAtEpochMs = lastSeenAtEpochMs,
    pinned = pinned,
    notes = notes,
    tagsJson = tags.toJson(),
    status = status.name,
    snoozedUntilEpochMs = snoozedUntilEpochMs,
    sourceDevice = sourceDevice,
    sourceTabId = sourceTabId,
    lastTransferredAtEpochMs = lastTransferredAtEpochMs,
    transferCount = transferCount,
)

fun TabEntity.toModel(): TabItem = TabItem(
    id = id,
    url = url,
    title = title,
    browser = enumOrDefault(browser, BrowserId.UNKNOWN),
    sourceGroup = sourceGroup,
    assignedGroup = assignedGroup,
    createdAtEpochMs = createdAtEpochMs,
    importedAtEpochMs = importedAtEpochMs,
    lastSeenAtEpochMs = lastSeenAtEpochMs,
    pinned = pinned,
    notes = notes,
    tags = tagsJson.toStringSet(),
    status = enumOrDefault(status, TabStatus.ACTIVE),
    snoozedUntilEpochMs = snoozedUntilEpochMs,
    sourceDevice = sourceDevice,
    sourceTabId = sourceTabId,
    lastTransferredAtEpochMs = lastTransferredAtEpochMs,
    transferCount = transferCount,
)

fun RegexRule.toEntity(): RegexRuleEntity = RegexRuleEntity(
    id, name, pattern, target.name, destinationGroup, priority, enabled, ignoreCase,
    addTags.toJson(), stopAfterMatch,
)

fun RegexRuleEntity.toModel(): RegexRule = RegexRule(
    id, name, pattern, enumOrDefault(target, RegexTarget.ANY), destinationGroup,
    priority, enabled, ignoreCase, addTagsJson.toStringSet(), stopAfterMatch,
)

fun GroupDefinition.toEntity(): GroupEntity = GroupEntity(id, name, colorKey, iconKey, sortOrder, isSystem)
fun GroupEntity.toModel(): GroupDefinition = GroupDefinition(id, name, colorKey, iconKey, sortOrder, isSystem)

fun TransferEvent.toEntity(): TransferEntity = TransferEntity(
    id, targetBrowser.name, attempted, opened, failed, cancelled, durationMs, createdAtEpochMs,
)
fun TransferEntity.toModel(): TransferEvent = TransferEvent(
    id, enumOrDefault(targetBrowser, BrowserId.UNKNOWN), attempted, opened, failed, cancelled, durationMs, createdAtEpochMs,
)

fun ImportSession.toEntity(): ImportEntity = ImportEntity(
    id, source.name, sourceLabel, received, accepted, rejected, deviceName, createdAtEpochMs,
)
fun ImportEntity.toModel(): ImportSession = ImportSession(
    id, enumOrDefault(source, BrowserId.UNKNOWN), sourceLabel, received, accepted, rejected, deviceName, createdAtEpochMs,
)

fun SmartView.toEntity(): SmartViewEntity = SmartViewEntity(id, name, LibraryQueryCodec.encode(query), iconKey, colorKey, pinned, sortOrder)
fun SmartViewEntity.toModel(): SmartView = SmartView(id, name, LibraryQueryCodec.decode(queryJson), iconKey, colorKey, pinned, sortOrder)

fun DeckDefinition.toEntity(): DeckEntity = DeckEntity(id, name, description, iconKey, colorKey, createdAtEpochMs, updatedAtEpochMs)

private fun Set<String>.toJson(): String = JSONArray(toList().sorted()).toString()
private fun String.toStringSet(): Set<String> = runCatching {
    val array = JSONArray(this)
    buildSet { for (i in 0 until array.length()) array.optString(i).trim().takeIf(String::isNotBlank)?.let(::add) }
}.getOrDefault(emptySet())

private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
