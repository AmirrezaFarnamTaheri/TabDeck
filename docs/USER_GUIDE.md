# User guide

## 1. Start from Home

Home answers three questions: how many tabs are saved, what needs attention, and what to do next. Use **Capture tabs**, **Browse tabs**, or **Open tabs**. Installed-browser badges describe destinations only; they do not mean TabDeck can read those browsers' live sessions.

## 2. Capture tabs explicitly

Open **Capture** and choose the route that matches the source:

- **Android Share** for the current page or links supplied by the browser.
- **Paste or file import** for URL lists, text, Markdown, CSV, HTML bookmarks, or backups.
- **Browser extension** for a permitted Firefox or desktop Chromium snapshot.
- **Desktop Link** for an ADB-authorized Android Chromium session that exposes DevTools targets.

Imports are previewed and validated first. TabDeck never silently deletes duplicates or truncates later valid items because a collection is large.

## 3. Find and organize in Tabs

Search covers URL, title, notes, tags, browser, TabDeck group, source device, and captured source session. Combine search with lifecycle, browser, group, source, tag, pin, note, stale, and sort filters.

The list is paged from Room. **Select visible** affects loaded rows; **select all matching** resolves the complete current query. Bulk actions operate on the complete resolved selection.

Use:

- **Groups** for a stable taxonomy independent of browser-native groups.
- **Tags and notes** for durable context.
- **Smart views** to save a complete query.
- **Launch decks** for an explicitly ordered recurring set.
- **Rules** for tested, ordered categorization.
- **Duplicate review** to choose a survivor and move redundant records to recoverable Trash.

## 4. Open captured tabs

1. Open **Open**.
2. Choose selected tabs, the current view, all active tabs, a group, or a deck.
3. Choose an installed destination browser.
4. Choose pacing and confirm.
5. Follow dispatched, failed, and cancelled request counts.

TabDeck sends Android `ACTION_VIEW` intents with an explicit create-new-tab request for each valid URL. A successful dispatch means Android accepted the request; the destination browser still owns final rendering, task reuse, policy, and failure behavior. TabDeck does not claim a page loaded merely because the intent was dispatched.

## 5. Lifecycle and recovery

- **Archive** retains completed material outside the active flow.
- **Snooze** returns a tab after the chosen time.
- **Trash** stays recoverable until explicit permanent deletion.
- Full JSON backup is the authoritative recovery format; Markdown, CSV, and bookmarks HTML are portable readable exports.

Backups preserve the complete supported collection and exclude the active bridge credential.

## 6. Settings and bridge

Settings contains capture defaults, appearance, exports, maintenance, and diagnostics. Capture contains the temporary local bridge. Session duration and stale thresholds accept any positive integer rather than a fixed preset ceiling.

The bridge remains loopback-only, authenticated, temporary, request-size bounded, and rate-limited. Desktop Link splits large selections into multiple requests so these security boundaries do not become collection-count ceilings.
