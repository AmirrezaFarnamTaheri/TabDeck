#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


coordinator = text("app/src/main/java/com/tabdeck/app/transfer/BrowserTransferCoordinator.kt")
view_model = text("app/src/main/java/com/tabdeck/app/TabDeckViewModel.kt")
settings = text("app/src/main/java/com/tabdeck/app/data/SettingsStore.kt")
models = text("app/src/main/java/com/tabdeck/app/model/Models.kt")
bridge_parser = text("app/src/main/java/com/tabdeck/app/bridge/BridgePayloadParser.kt")
repository = text("app/src/main/java/com/tabdeck/app/data/TabDeckRepository.kt")
snapshot = text("app/src/main/java/com/tabdeck/app/data/SnapshotJsonCodec.kt")
query_builder = text("app/src/main/java/com/tabdeck/app/data/local/TabQueryBuilder.kt")
query_codec = text("app/src/main/java/com/tabdeck/app/data/local/LibraryQueryCodec.kt")
regex_engine = text("app/src/main/java/com/tabdeck/app/engine/RegexCategorizer.kt")
bridge_service = text("app/src/main/java/com/tabdeck/app/bridge/LocalBridgeService.kt")
bridge_network = text("app/src/main/java/com/tabdeck/app/bridge/BridgeNetwork.kt")
root = text("app/src/main/java/com/tabdeck/app/ui/TabDeckRoot.kt")
screens = text("app/src/main/java/com/tabdeck/app/ui/Screens.kt")
dialogs = text("app/src/main/java/com/tabdeck/app/ui/Dialogs.kt")
desktop = text("desktop-link/TabDeckLink.ps1")

require("Browser.EXTRA_CREATE_NEW_TAB" in coordinator, "browser launches must request a new tab")
require("batchLimit" not in coordinator and "MAX_BATCH_LIMIT" not in coordinator, "browser transfer must process the complete requested selection")
require("tabs.take(" not in coordinator, "browser transfer must not truncate selections")

for forbidden in (
    "MAX_INTENT_URLS",
    "MAX_SHARE_TABS",
    "MAX_SELECT_ALL",
    "MAX_CLIPBOARD_CHARACTERS",
    "setTransferBatchLimit",
    "transferBatchLimit",
    "safety cap",
    "Transfer capped",
):
    require(forbidden not in view_model, f"view model still contains arbitrary cap: {forbidden}")

require("transferBatchLimit" not in settings, "settings must not expose a transfer hard cap")
require("transferBatchLimit" not in models, "app settings model must not retain a transfer hard cap")
for forbidden in ("coerceIn(5, 120)", "coerceIn(1, 3650)"):
    require(forbidden not in view_model and forbidden not in settings and forbidden not in snapshot and forbidden not in bridge_service, f"user configuration still has a preset upper ceiling: {forbidden}")
require("MAX_TABS_PER_IMPORT" not in bridge_parser and "minOf(tabArray.length()" not in bridge_parser, "bridge imports must not truncate tab snapshots")

for forbidden in (
    "MAX_IMPORT_TABS",
    "MAX_RULES",
    "MAX_GROUPS",
    "MAX_DECK_TABS",
    "MAX_SMART_VIEWS",
    "MAX_DECKS",
    "MAX_DUPLICATE_CLUSTERS",
    "A maximum of",
):
    require(forbidden not in repository, f"repository still contains arbitrary collection cap: {forbidden}")

for forbidden in ("MAX_BACKUP_TABS", "MAX_BACKUP_RULES", "MAX_BACKUP_GROUPS", "MAX_BACKUP_VIEWS", "MAX_BACKUP_DECKS", "MAX_BACKUP_DECK_TABS"):
    require(forbidden not in snapshot, f"backup codec still truncates data: {forbidden}")

for forbidden in ("MAX_SEARCH_TOKENS", "MAX_TAG_FILTERS", "MAX_LIMIT"):
    require(forbidden not in query_builder, f"query builder still contains an arbitrary result/filter cap: {forbidden}")
require("MAX_SET_ITEMS" not in query_codec, "saved views must preserve all selected filters")
require("MAX_RULES" not in regex_engine, "rule evaluation must not silently ignore later rules")
require("MAX_PATTERN_LENGTH" in regex_engine, "rule validation must retain a bounded regex pattern size")
require("SQLITE_MAX_BIND_ARGUMENTS" in query_builder and "requireSupported" in query_builder, "query construction must enforce SQLite's bind-argument boundary")
require("ROOM_PAGING_BIND_ARGUMENTS = 2" in query_builder, "Room paging queries must reserve LIMIT and OFFSET bind arguments")
require("TabQueryBuilder.requireSupported(stableQuery)" in repository, "sanitized paging queries must be validated before Room adds paging binds")
for cap in ("MAX_TAB_TITLE_CHARS", "MAX_NOTES_CHARS", "MAX_TAG_TEXT_BUDGET", "MAX_SOURCE_TAB_ID_CHARS"):
    require(cap in repository, f"incoming tab rows must retain the persistence safety boundary: {cap}")
require("MAX_SESSION_MINUTES = 6 * 60" in bridge_network, "bridge sessions must reflect Android's six-hour dataSync limit")
require("coerceIn(1, BridgeNetwork.MAX_SESSION_MINUTES)" in bridge_service, "bridge service expiry must use the effective foreground-service bound")
require("no preset ceiling" not in screens, "capture UI must not advertise an unbounded bridge duration")
require("withContext(Dispatchers.Default)" in dialogs and "delay(200)" in dialogs, "large URL previews must be debounced off the main thread")
require("priorityValue == null" in dialogs and "sortOrderValue == null" in dialogs, "numeric dialog fields must expose parse errors")

for label in ('HOME("Home"', 'TABS("Tabs"', 'OPEN("Open"', 'CAPTURE("Capture"', 'SETTINGS("Settings"'):
    require(label in root, f"guided utility navigation is missing {label}")

for forbidden in ("control plane", "Control room", "Browser readiness", "Source topology", "Hard cap", "safety cap"):
    require(forbidden not in screens, f"misleading or implementation-centric UI copy remains: {forbidden}")

for forbidden in ("25,000-link safety cap", "take(25_000)", "take(500_000)", "take(64)", "coerceIn(-10_000, 10_000)", "coerceIn(0, 100_000)"):
    require(forbidden not in dialogs, f"import dialog still truncates user input: {forbidden}")

for forbidden in ("MaxBridgeTabs", "MaxLiveActionTabs", "capped at", "Bridge imports are capped"):
    require(forbidden not in desktop, f"desktop companion still contains an arbitrary cap: {forbidden}")
require("Capture workspace" in desktop, "desktop companion must present a guided capture workspace")
require("Send selected tabs" in desktop, "desktop companion must provide a direct capture action")
require("visible tab(s) $verb" in desktop, "desktop selection status must use descriptive wording")
require("were already accepted" in desktop, "desktop batch failures must retain partial progress")

for manifest in (
    text("extensions/chromium-desktop/manifest.json"),
    text("extensions/firefox-android/manifest.json"),
):
    require("http://*/*" not in manifest, "extensions must not request public HTTP host access")
    require("http://127.0.0.1:48721/*" in manifest, "extensions must request only the supported loopback bridge origin")

print("Guided utility contract: PASS")
