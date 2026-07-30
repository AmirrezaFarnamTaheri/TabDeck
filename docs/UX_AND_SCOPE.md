# TabDeck v1 UX and scope decisions

## Primary jobs

1. **Get authorized tabs in** through the strongest available connector.
2. **Understand the inventory** without browser-by-browser hunting.
3. **Reduce noise safely** through previewed, reversible duplicate handling.
4. **Create a durable taxonomy** independent of browser-native group support.
5. **Build repeatable workflows** with smart views, launch decks, rules, and query-wide actions.
6. **Move a chosen session** into an installed Android browser without accidental tab explosions.
7. **Keep an exit path** through complete backups and human-readable/browser-compatible exports.

## Information architecture

- **Overview** — health, readiness, sources, recent activity, pinned views, and decks.
- **Library** — paged daily workspace for search, filter, metadata, and lifecycle control.
- **Organize** — smart views, launch decks, rules, and group taxonomy.
- **Transfer** — bounded Android handoff with progress and cancellation.
- **Control room** — capture routes, bridge lifecycle, source identities, appearance, portability, and maintenance.

Phone layouts use bottom navigation. Medium layouts use a rail. Large layouts use a persistent drawer. The command palette exposes high-frequency navigation, analysis, automation, transfer, connector, backup, and export actions.

## Large-library interaction

- Search/filter/sort happens in Room, not over a complete Compose snapshot.
- The list/grid loads incrementally with Paging.
- Stable tab IDs preserve item state across reorder and refresh.
- Item content types improve composition reuse.
- Placeholder rows are disabled to avoid zero-height/false-content gaps.
- Dashboard aggregates are independent of the paged inventory.
- **Select visible** is immediate; **select all matching** is explicit and capped at 25,000.
- Bulk-action bars show the real resolved selection count.

## Saved control structures

### Smart views

A smart view stores the complete active `LibraryQuery`: text, statuses, browsers, TabDeck groups, source devices, source groups, tags, pin/notes/stale filters, sort, and direction. Pinned views surface on the Overview screen.

### Launch decks

A deck stores explicit ordered tab membership. It is suitable for recurring research sessions, workspaces, trip planning, incident response, or reading sets. Deck membership survives query changes and is included in backup v3.

### Rules

Rules are tested against the active library before mutation. The editor makes unsupported regex behavior visible. Apply remains explicit.

## Safety hierarchy

- Import never silently deletes duplicates.
- Live import preview shows detection and normalization before persistence.
- Dedupe previews clusters and moves copies to Trash.
- Permanent selected deletion and Empty Trash require confirmation.
- Group rename preserves tab/rule references.
- Group delete rehomes references to Inbox.
- Bulk tag control distinguishes add, remove, replace, and clear.
- Transfer shows the bounded count, target package, pacing, and requires destination confirmation.
- Transfer cancellation preserves partial bookkeeping.
- Connector cleanup is separate from connector capture and requires confirmation.
- Reset stops bridge/transfer activity before clearing local data.

## Library state model

- **Active** — normal working inventory.
- **Archived** — retained outside the active flow.
- **Snoozed** — automatically restored after a scheduled time.
- **Trash** — recoverable until explicit permanent removal.

Selection is transient. Database changes and query changes remove stale selected IDs.

## Visual language

- Material 3 surfaces establish clear hierarchy without hiding density.
- Hero panels communicate state and next action.
- Metric cards are actionable where a meaningful filtered view exists.
- Source/browser/group chips combine text with icon/status rather than relying on color.
- Comfortable is the default density; compact and dense are explicit choices.
- List and grid are independent preferences.
- Dynamic color is optional; five stable fallback accents exist.
- Risk states use explicit language, icons, and confirmations.

## Accessibility

- Semantic Material controls and minimum touch targets.
- Text alternatives for icon-only actions.
- Textual progress, counts, warnings, and connection state.
- No meaning encoded by color alone.
- Font-scale and large-screen behavior are release-test requirements.
- Keyboard/D-pad navigation is part of the large-screen test matrix.
- Reduced motion is a persistent preference.

## Scope boundary

“Control Android browsers” means controlling the **authorized session inventory and supported connector actions**. It does not mean bypassing Android's application sandbox. This boundary is visible during onboarding, capture, connector setup, and transfer—not hidden in legal text.
