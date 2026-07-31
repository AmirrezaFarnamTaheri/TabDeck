# TabDeck v1.1.0 release notes

TabDeck v1.1.0 turns the first release into an honest guided utility and repairs Android browser restore behavior.

## Fixed

- Every Android restore now requests a distinct new browser tab instead of relying on a plain URL intent that a browser may reuse in the current task.
- Transfer status now reports dispatched requests and failures rather than claiming a destination page loaded without confirmation.
- Browser installation is presented only as an available open target, never as permission to read live tabs.

## Guided Android experience

- New primary structure: **Home, Tabs, Open, Capture, Settings**.
- Home leads with Capture, Browse, and Open instead of health scores and implementation terminology.
- Capture explains Android Share, import, extension, and Desktop Link routes before bridge details.
- Open clearly separates source scope, destination, pacing, confirmation, progress, and history.
- Flat outlined surfaces, restrained radii, lighter type hierarchy, and a deep teal/navy/amber identity replace the decorative generic-card treatment.
- Dynamic color is off by default so the product identity remains stable.

## Complete operations

- Removed arbitrary item-count ceilings from imports, backups, query selection, tags, rules, groups, views, decks, duplicate analysis, sharing, copying, transfers, and desktop capture.
- Removed arbitrary upper ceilings from bridge-session duration, stale thresholds, rule priority, and display ordering.
- Desktop Link sends complete selections by splitting payloads according to request bytes instead of dropping later tabs.
- Protocol protections remain for request bytes, URL validity, authentication rate, identifiers, timestamps, and regex complexity.

## Desktop Link

- Rebuilt as a four-step workspace: Device, Browser tabs, Selection, Send to TabDeck.
- Added clearer device, browser-session, and selection status.
- Made capture the primary action and moved open/close/export operations into secondary tools.
- Removed fixed socket, capture, open, and close item ceilings.

## Compatibility

- Room schema: v3
- Backup format: v3
- Saved-query codec: v2
- Bridge API: v3 with v1/v2 route compatibility

Install `TabDeck-v1.1.0.apk` from the matching GitHub Release and verify it against the release checksums and certificate fingerprint.
