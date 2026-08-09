# AlecsConsole Design

Date: 2026-08-09

## Goal

Build a new Hytale server plugin named **Alec's Console** from a fresh AzureDoom Gradle template. It must preserve the useful features of `hytale-console`, including the unmerged select/copy work, while keeping memory, filtering, UI payloads, and historical-log I/O bounded.

The plugin is an in-game log viewer, not a command-execution console or a general log-management platform.

## Repository and build

- Repository: standalone Git repository at `AlecsConsole`, default branch `main`.
- Local development: also register the project as `:alecs-console` in the parent Hytale mod workspace.
- Template: AzureDoom simple Java mod, Groovy Gradle DSL, single project.
- Build plugin: `com.azuredoom.hytale-tools` 1.0.46.
- Java: 25.
- Initial version: `0.1.0`.
- Maven group: `com.alechilles`.
- Manifest group: `Alechilles`.
- Project/package slug: `alecsconsole`.
- Manifest name and display name: `Alec's Console` (AzureDoom 1.0.46 emits its `modId` as the Hytale manifest `Name`).
- Main class: `com.alechilles.alecsconsole.AlecsConsolePlugin`.
- Asset pack: included for the custom UI.
- Patchline/version defaults: standalone `gradle.properties` uses release 0.5.7; parent workspace registration consumes the same workspace properties.
- License: no license grant in the initial repository; publication licensing can be chosen before the first release.

The standalone repository owns its source, wrapper, build configuration, docs, and tests. Parent workspace registration is a separate scoped change in the parent repository.

## Required user-visible behavior

### Access

- `/console` opens the page for a player.
- The command uses the explicit permission `alechilles.alecsconsole.command.console`. This avoids Hytale's generated permission incorporating spaces and punctuation from the display name.
- Players without the permission cannot discover or execute the command through the normal command tree.

### Sources

The page has four logical sources:

1. **Live**: bounded records captured during the current plugin session.
2. **Current server log**: the newest `*_server.log` in the server process's `logs` directory.
3. **Previous server logs**: older matching files in that directory, newest first.
4. **Client logs**: `.log` files in explicitly allowed same-host client-log directories.

Client logs are host-local only. Hytale release 0.5.7 and pre-release 0.6.0-pre.7 expose no indexed server API or packet for requesting a remote player's local log files. On Windows, the plugin may auto-discover the standard release and pre-release client log directories under `%APPDATA%/Hytale/data`; the config can provide one additional client-log directory. If none exists, the UI marks the source unavailable. Dedicated servers do not imply access to remote clients.

Players never submit arbitrary paths. UI file choices resolve through catalog IDs, and the resolved canonical file must remain below an allowed root and end in `.log`.

### Filters and display

- Preserve minimum-level, logger, and case-insensitive text filters.
- Text matching covers timestamp/prefix, logger, and full message.
- Preserve severity coloring and visible timestamps/logger names.
- Preserve full-message viewing, multi-line selection, selected-text display, clipboard copying through the text field, and clearing the selection.
- Replace the original double-right-click hover gesture with an explicit multi-select mode. A normal click selects one row and displays its complete message; in multi-select mode, clicking toggles rows in the copy selection.
- Render no more than one configured page at a time. Defaults are 200 visible rows and 2,000 retained disk-search matches.
- Historical result pages are newest first and display total retained matches plus a clear capped indicator when more matches existed.

### Live controls

- Live text filtering uses a trailing debounce, default 300 ms.
- Discrete logger and severity changes apply immediately.
- **Pause** freezes visible rows while capture continues. The UI shows a count of unseen new records.
- **Follow** resumes updates and catches the page up to the newest matching records.
- Live capture continues when no page is open, but only within the fixed ring capacity.

### Historical search controls

- Disk search starts only through Enter or an explicit Search action. Typing does not scan files.
- Changing the file or submitting a new search cancels the prior search.
- Search status is visible: searching, complete, capped, unavailable, canceled, or failed.
- The reader accepts the standard Hytale format `[yyyy/MM/dd HH:mm:ss LEVEL] [logger] message`. A non-header line is attached to the preceding record when possible; otherwise it is shown as a raw record with unknown metadata.

## Architecture

### `AlecsConsolePlugin`

Owns lifecycle only: create services, subscribe/unsubscribe the logger backend, register the command, and close scheduled/executor resources during shutdown. Shutdown must be idempotent.

### `LogEntry`

An immutable normalized record containing a monotonic sequence, time/display prefix, numeric and display level, logger name, full message, and one precomputed lowercase search string. UI formatting must not mutate entries.

### `LiveLogStore`

A small synchronized fixed-capacity ring, default 5,000 entries.

- Append is constant-time and never copies the full collection.
- Evict the oldest record at capacity.
- Snapshot operations return only the bounded range needed by the caller.
- Logger callbacks append only; they never touch UI, disk, or plugin logging.
- Logger-name choices are derived from bounded entries, so names from evicted records do not accumulate forever.

This directly replaces the original unbounded `CopyOnWriteArrayList<LogRecord>` backlog.

### `FilterEngine`

A pure component applying minimum level, exact logger choice, and case-insensitive text matching. It consumes the precomputed search string and has no Hytale/UI dependency.

### `ConsolePage` and `ConsolePageManager`

Each page owns only per-player state: selected source/file, filters, debounce generation, visible result page, selected rows, pause/follow sequence, and status text.

- Open with the newest matching live page.
- Batch live updates at a modest interval, approximately 250 ms.
- Prefer row deltas when supported by the UI command API. If a rebuild is required, rebuild only the configured visible page, never all retained records.
- A filter/source/page change may rebuild the bounded page.
- UI mutations run on the required player/world executor.
- Dismissal removes the page and cancels its debounce/search work.
- Async completions carry a page/query generation and are ignored when stale.

### `LogCatalog`

Enumerates allowed `.log` files without opening them. Server logs come from the process working directory's `logs` folder and match `*_server.log`. The newest file is the current log; the remainder are previous logs. Client roots are same-host roots from standard Windows discovery and the optional configured directory.

Missing or unreadable roots make only that source unavailable. Catalog errors must not disable live viewing.

### `FileLogReader`

Runs on one plugin-owned background executor.

- Stream the selected file once per submitted search; never load the whole file.
- Apply parsed severity/logger/text filters during the stream.
- Retain only the newest configured number of matches in a bounded ring, default 2,000.
- Check cancellation/interruption regularly.
- Close the file promptly on completion, cancellation, error, or shutdown.
- Return data/status only; the page manager performs UI updates on the correct executor.

Search time may scale with the selected file's size, but it never blocks the world thread and retained memory does not.

### `AlecsConsoleConfig`

Stored as `config.json` below `PluginBase#getDataDirectory()` and created with defaults on first run:

- `liveCapacity`: 5,000
- `pageSize`: 200
- `searchDebounceMs`: 300
- `diskResultLimit`: 2,000
- `clientLogDirectory`: absent by default

Validation clamps invalid numeric values to conservative ranges and reports one startup warning: `liveCapacity` 100–50,000, `pageSize` 25–500, `searchDebounceMs` 100–2,000, and `diskResultLimit` 100–20,000. No per-player preferences are persisted.

## Error handling and safety

- A missing/locked/deleted disk file produces an inline status and leaves the page usable.
- Malformed file lines degrade to raw records instead of aborting a scan.
- Stale debounce and file-search results cannot overwrite a newer page state.
- Search failures are reported once to the page. The logger-capture callback must never log about its own processing, avoiding recursion.
- Path containment is checked after normalization/canonicalization before every open.
- The logger subscription, scheduled UI work, file reads, and executor are released during plugin shutdown.
- Live behavior remains available in Hytale bare mode, where the server may not create a `logs` directory.

## Performance acceptance criteria

- Live memory is bounded by configuration regardless of plugin uptime, open pages, or disk-log size.
- A logger append does not copy the retained collection and performs no UI or disk work.
- Typing a live query schedules at most one filter pass after the trailing debounce window.
- No UI operation renders more than `pageSize` rows.
- Normal live refresh cost scales with new matching entries and open pages, not total server-log file size.
- Historical file I/O never runs on a Hytale world/player executor.
- Disk result retention never exceeds `diskResultLimit`.

## Tests and verification

Add only behavior-focused tests that protect a concrete regression:

1. `LiveLogStore` capacity: appending past capacity returns the newest entries and evicts the oldest, preventing the original unbounded-backlog failure.
2. `FilterEngine`: combined level/logger/text criteria return the correct observable records, protecting the viewer's core filtering behavior.
3. `FileLogReader`: a minimal real-format temporary log parses/searches correctly and reports/enforces the result cap, protecting bounded historical browsing.

Do not add tests for source shape, declarations, asset presence/counts, raw manifest text, private fields, UI file text, or framework permission mechanics.

Verification before completion:

- AzureDoom Gradle build, tests, manifest generation, and manifest validation.
- Hytale Workshop UI grammar validation for authored `.ui` assets.
- Hytale Workshop engine-reference validation for authored Java files that touch engine APIs.
- A bounded local-server smoke run covering: permission denial/allow, live capture, debounced search, level/logger filters, pause/follow and unseen count, single/multi-select copy, current/previous server-log search, unavailable client-log behavior, dismissal, and shutdown.
- Stop the development server/Gradle daemon after verification and inspect both repository worktrees for unintended changes.

## Explicit non-goals for v1

- Executing server commands from the UI.
- Reading or uploading logs from remote player machines.
- Regex, saved searches, persistent player preferences, or cross-file search.
- A database, persistent index, filesystem watcher, or background pre-indexing.
- Showing more than one selected file per historical search.
- Pixel-for-pixel reproduction of the original UI or preservation of its unusual mouse gestures.

## Evidence behind the redesign

The original `hytale-console` CopyFeature branch keeps pending records in an unbounded `CopyOnWriteArrayList`, drains only while pages are open, scans up to 5,000 rows for every text change, and clears/rebuilds all matching UI rows with per-row bindings. Its selection/copy behavior is nevertheless part of the required feature baseline.

Hytale Workshop evidence for release 0.5.7 identifies `com.hypixel.hytale.logger.backend.HytaleFileHandler#enable` as creating relative `logs/<timestamp>_server.log` files; it exposes no history enumeration API. Hytale's supported command permission path is through plugin command registration plus `AbstractCommand` permission checks. Searches of release 0.5.7 and pre-release 0.6.0-pre.7 found no supported server API or packet for client-local log retrieval, so remote client-log access is excluded.
