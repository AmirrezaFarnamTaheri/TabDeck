# User guide

## 1. Choose an acquisition route

Use the least-complex route that captures the needed tabs:

- **Share to TabDeck** from any Android browser for individual pages or browser-provided URL lists.
- **Paste/import** for copied text, Markdown, HTML, CSV, bookmarks, or URL-list files.
- **Firefox Android connector** for an authorized Firefox snapshot.
- **Local bridge** for a desktop extension or Windows Desktop Link.

Imports are previews first. TabDeck validates and normalizes candidates but does not silently delete duplicates.

## 2. Work from Overview

Overview shows inventory health, lifecycle totals, duplicate pressure, browser/source topology, recent imports and transfers, pinned smart views, and launch decks. Use an actionable metric or source facet to open the corresponding filtered Library view.

## 3. Search and filter the Library

Library search covers URL, title, notes, tags, browser, groups, source device, and source/native group metadata. Combine it with:

- lifecycle lanes: Active, Archived, Snoozed, Trash;
- browser and TabDeck-group filters;
- source-device and source-group filters;
- pinned, notes, stale, and tag filters;
- sort mode and direction;
- list/grid and density preferences.

The list is loaded incrementally from Room. **Select visible** affects currently loaded rows; **select all matching** resolves the entire current query up to the bounded control ceiling.

## 4. Organize durable knowledge

- **Groups** provide a stable TabDeck taxonomy independent of browser-native groups.
- **Tags** support add, remove, replace, and clear semantics in bulk.
- **Notes** retain context that browsers usually do not preserve.
- **Smart views** save the complete filter and sort state; pinned views appear on Overview.
- **Launch decks** preserve explicit ordered membership for recurring sessions.
- **Regex rules** match URL, title, host, source group, tags, or combined text. Test a rule against the active library before applying it.

## 5. Review duplicates safely

Choose a matching level:

- exact URL;
- normalized URL;
- host plus path.

Then choose a survivor policy: newest, oldest, richest metadata, or pinned-first. Preview clusters before applying. Optional metadata merge retains useful tags, notes, timestamps, pin state, group, and transfer metadata. Redundant records move to Trash instead of being permanently removed.

## 6. Transfer to an Android browser

1. Select tabs, a matching query, a group, or a launch deck.
2. Choose an installed destination browser.
3. Review the bounded count and pacing mode.
4. Confirm the destination.
5. Watch live progress or cancel.

Transfer opens validated copies. It does not universally close source tabs or recreate native browser groups. Partial success and failure counts are recorded.

## 7. Lifecycle controls

- **Archive** removes completed material from the active flow without deleting it.
- **Snooze** returns tabs after a selected time.
- **Trash** is recoverable until permanent deletion.
- Emptying Trash and permanent selected deletion require explicit confirmation.

## 8. Export and recovery

- **Full JSON backup v3** is the authoritative portable recovery format.
- **Markdown** is a grouped readable outline.
- **CSV** is a metadata-rich table with spreadsheet-formula neutralization.
- **Bookmarks HTML** is suitable for browser bookmark import.

Readable exports exclude Trash; the full backup retains bounded recoverable data and excludes live bridge credentials.
