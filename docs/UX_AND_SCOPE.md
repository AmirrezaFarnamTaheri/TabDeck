# Guided utility UX and scope

## Product promise

TabDeck helps a person **capture links explicitly, find and organize them locally, and open a chosen set in an installed browser**. It does not imply privileged cross-app browser access.

## Primary navigation

- **Home** — current library state and three direct next actions: Capture, Browse, Open.
- **Tabs** — paged search, filters, selection, metadata, lifecycle, duplicate review, groups, views, decks, and rules.
- **Open** — source scope, destination browser, pacing, confirmation, progress, cancellation, and request history.
- **Capture** — Android Share, paste/file, extensions, Desktop Link, temporary bridge, and captured-source information.
- **Settings** — behavior, appearance, portability, maintenance, and diagnostics.

Organize remains a contextual secondary workspace rather than a sixth primary destination.

## Language rules

- Say **capture**, not acquire or ingest.
- Say **open target**, not browser readiness.
- Say **request sent**, not tab loaded, unless the destination confirms rendering.
- Explain that installed-package detection does not reveal live tabs.
- Put connector mechanics after the user goal, not before it.

## Large collections

- Search, filters, and sorting execute in Room and page into Compose.
- Select-all resolves the complete query.
- Imports, backups, filters, tags, groups, rules, views, decks, transfers, exports, and Desktop Link selections are not silently truncated by arbitrary item counts.
- Large bridge payloads are divided by bytes and sent completely.
- Display-only summaries may show a recent subset; the underlying data remains complete.

## Visual system

- Deep teal, navy, and warm amber form a stable TabDeck identity; Android dynamic color is optional and off by default.
- Flat outlined utility surfaces replace decorative gradients and elevated card stacks.
- Corner radii are restrained and consistent.
- Typography uses bold or semibold hierarchy rather than black/extra-bold display weight.
- Icons support labels instead of sitting in decorative pills wherever possible.
- Risk and capability boundaries are conveyed with direct text, not color alone.

## Safety hierarchy

- Import never silently deletes duplicates.
- Dedupe is previewed and recoverable through Trash.
- Permanent deletion and live-browser closure require confirmation.
- Transfer validates URLs, confirms the destination, supports cancellation, and records partial results.
- A dispatched Android intent is not presented as confirmed page load.
- Security boundaries for bytes, URL validity, authentication rate, identifier storage, and regex complexity remain explicit.

## Scope boundary

Android package visibility reveals installation, not browser tabs. TabDeck reads a live session only through an explicit authorized capture route: Android Share, an extension with the relevant permission, a file supplied by the user, or an ADB/DevTools session exposed to Desktop Link. No root, private databases, hidden APIs, accessibility scraping, VPN interception, or silent destructive action is used.
