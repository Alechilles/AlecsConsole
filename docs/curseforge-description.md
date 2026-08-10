# Alec's Console

Keep an eye on your Hytale server without leaving the game. **Alec's Console** gives authorized players a responsive in-game view of live and historical logs, with the tools needed to find important entries quickly.

## Features

- Watch live server logs from the current session.
- Browse the current and previous server `.log` files.
- View release and pre-release client logs when those files are on the same machine as the server.
- Filter entries by minimum severity, logger, or text.
- Move through large result sets with simple pagination.
- Use **Pause/Follow** to freeze the live view while you inspect an entry, then resume updates when you are ready.
- Select one or multiple rows to expose their full text for copying. Multi-select keeps entries in display order.
- Keep resource use predictable with bounded live history, search results, rendered rows, discovered files, and pending work.

## How to use

Run:

```text
/console
```

Players need the following permission:

```text
alechilles.alecsconsole.command.console
```

Live text filtering updates shortly after you stop typing. Historical files are loaded on demand; after changing filters for a historical source, press **Search** or submit the search field to scan the file again.

## Client log support

Hytale's plugin API does not let a server retrieve files from a remote player's computer. Client logs are therefore available only when they are stored on the same machine as the server. The optional `ClientLogDirectory` setting can point Alec's Console at one additional local directory.

## Configuration

On first launch, Hytale creates `config.json` in the plugin's data directory with these defaults:

```json
{
  "LiveCapacity": 5000,
  "PageSize": 200,
  "SearchDebounceMs": 300,
  "DiskResultLimit": 2000,
  "ClientLogDirectory": null
}
```

These settings control the retained live-entry limit, rows per page, live-search delay, maximum historical results, and optional local client-log directory. Alec's Console does not continuously rescan the disk or maintain an on-disk search index.

## Compatibility

- Hytale Server `0.5.x`

The original feature set was inspired by `duke-darius/hytale-console`. Alec's Console is an independent, ground-up rebuild.
