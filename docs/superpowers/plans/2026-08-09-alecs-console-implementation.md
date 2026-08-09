# AlecsConsole Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a bounded, responsive in-game Hytale log viewer with live filtering, selection/copy, pause/follow, and explicit current/previous/same-host-client file search.

**Architecture:** Capture Hytale `LogRecord`s through a non-retaining `CopyOnWriteArrayList` adapter into a fixed-capacity synchronized ring. Keep filtering pure and pre-normalized, limit every rendered page, and run cancelable streaming disk searches on one plugin-owned executor. Hytale lifecycle, command, and custom-page classes compose these units without moving disk work onto a world executor.

**Tech Stack:** Java 25, Gradle, AzureDoom `com.azuredoom.hytale-tools` 1.0.46, Hytale server API 0.5.7, Hytale custom `.ui`, JUnit Jupiter 5.10.2.

## Global Constraints

- Standalone Git repository on `main`; register separately as `:alecs-console` in the parent mod workspace.
- Maven group `com.alechilles`, manifest group `Alechilles`, manifest name `Alec's Console`, project/package slug `alecsconsole`, version `0.1.0`.
- Main class `com.alechilles.alecsconsole.AlecsConsolePlugin`; display name `Alec's Console`; asset pack included.
- Live capacity default 5,000; visible page default 200; debounce default 300 ms; disk result cap default 2,000.
- `/console` must use the plugin-owned command permission and remain player-only.
- Never accept arbitrary filesystem paths from UI event data.
- No command execution, regex, saved searches, database/index, filesystem watcher, persistent player preferences, or remote client-log upload.
- Add only behavior tests for bounded capture, filtering, and file parsing/search; no source-shape or asset-presence tests.
- Use Git Bash. Stop local servers and Gradle daemons after verification.

## File map

- `settings.gradle` — plugin repositories and standalone project identity.
- `build.gradle` — Java 25, AzureDoom tool, JUnit, manifest, and jar configuration.
- `gradle.properties` — version, Hytale release, patchline, configuration cache.
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` — generated Gradle wrapper.
- `.gitignore` — Gradle, IDE, build, run, and local Hytale output exclusions.
- `README.md` — commands, permission, features, limits, client-log boundary, build/use instructions.
- `src/main/java/com/alechilles/alecsconsole/LogEntry.java` — immutable normalized log row.
- `src/main/java/com/alechilles/alecsconsole/LogFilter.java` — pure filter state and matching.
- `src/main/java/com/alechilles/alecsconsole/LiveLogStore.java` — synchronized bounded ring and snapshots.
- `src/main/java/com/alechilles/alecsconsole/LogCaptureSubscriber.java` — non-retaining Hytale logger subscriber adapter.
- `src/main/java/com/alechilles/alecsconsole/AlecsConsoleConfig.java` — Hytale codec-backed runtime settings and clamps.
- `src/main/java/com/alechilles/alecsconsole/LogFileCatalog.java` — safe current/previous/server-host-client file inventory.
- `src/main/java/com/alechilles/alecsconsole/HytaleLogReader.java` — cancelable streaming parse/filter with bounded newest matches.
- `src/main/java/com/alechilles/alecsconsole/OpenConsoleCommand.java` — player-only `/console` command.
- `src/main/java/com/alechilles/alecsconsole/ConsoleEventData.java` — custom UI event codec.
- `src/main/java/com/alechilles/alecsconsole/ConsolePage.java` — one player's UI state, event handling, and bounded rendering.
- `src/main/java/com/alechilles/alecsconsole/ConsolePageManager.java` — page lifecycle, refresh/debounce/search scheduling.
- `src/main/java/com/alechilles/alecsconsole/AlecsConsolePlugin.java` — composition and lifecycle.
- `src/main/resources/Common/UI/Custom/Pages/AlecsConsole.ui` — main viewer layout.
- `src/main/resources/Common/UI/Custom/Pages/AlecsConsoleRow.ui` — one log row template.
- `src/test/java/com/alechilles/alecsconsole/LiveLogStoreTest.java` — bounded capture regression.
- `src/test/java/com/alechilles/alecsconsole/LogFilterTest.java` — combined filter behavior.
- `src/test/java/com/alechilles/alecsconsole/HytaleLogReaderTest.java` — real-format historical parse/search/cap behavior.
- Parent `../settings.gradle` and `../build.gradle` — standalone-repo workspace registration only.

---

### Task 1: Scaffold the AzureDoom project and bounded live capture

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `src/main/java/com/alechilles/alecsconsole/LogEntry.java`
- Create: `src/main/java/com/alechilles/alecsconsole/LiveLogStore.java`
- Create: `src/main/java/com/alechilles/alecsconsole/LogCaptureSubscriber.java`
- Create: `src/test/java/com/alechilles/alecsconsole/LiveLogStoreTest.java`

**Interfaces:**
- Produces: `LogEntry.fromRecord(long sequence, LogRecord record)`, `LiveLogStore.append(LogRecord)`, `snapshotNewest(int limit)`, `snapshotAfter(long sequence, int limit)`, `size()`, `latestSequence()`, and `LogCaptureSubscriber(LiveLogStore)`.
- Consumes: Hytale `HytaleLoggerBackend.subscribe(CopyOnWriteArrayList<LogRecord>)`, whose backend invokes only `subscriber.add(record)`.

- [ ] **Step 1: Generate the simple Java/Groovy single-project template**

Use AzureDoom's current template conventions and produce this build identity:

```groovy
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools' version '1.0.46'
}

group = 'com.alechilles'
version = providers.gradleProperty('mod_version').get()

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

hytaleTools {
    hytaleVersion = providers.gradleProperty('hytale_version').get()
    patchline = providers.gradleProperty('hytale_patchline').get()
    manifestServerVersion = '0.5.x'
    manifestGroup = 'Alechilles'
    modId = "Alec's Console"
    modDescription = 'A responsive in-game viewer for live and historical Hytale logs.'
    modCredits = 'Alec (Alechilles)||https://www.curseforge.com/members/alechilles/projects'
    mainClass = 'com.alechilles.alecsconsole.AlecsConsolePlugin'
    includesPack = true
    bundleAssetEditorRuntime = false
}
```

Confirm through `./gradlew updatePluginManifest` that the manifest identifier is `Alechilles:Alec's Console` and the main class is exact.

Add JUnit exactly as nearby mods do and configure `Test` with JUnit Platform and Hytale's logging manager. Set:

```properties
mod_version=0.1.0
hytale_version=0.5.7
hytale_patchline=release
org.gradle.configuration-cache=true
```

- [ ] **Step 2: Write the failing bounded-capture tests**

```java
@Test
void evictsOldestRecordsAtCapacity() {
    LiveLogStore store = new LiveLogStore(3);
    store.append(record(Level.INFO, "one"));
    store.append(record(Level.WARNING, "two"));
    store.append(record(Level.SEVERE, "three"));
    store.append(record(Level.INFO, "four"));

    assertEquals(List.of("two", "three", "four"),
        store.snapshotNewest(10).stream().map(LogEntry::message).toList());
    assertEquals(3, store.size());
}

@Test
void subscriberDelegatesWithoutRetainingRecords() {
    LiveLogStore store = new LiveLogStore(2);
    LogCaptureSubscriber subscriber = new LogCaptureSubscriber(store);

    assertTrue(subscriber.add(record(Level.INFO, "captured")));

    assertEquals(List.of("captured"),
        store.snapshotNewest(10).stream().map(LogEntry::message).toList());
    assertTrue(subscriber.isEmpty());
}
```

- [ ] **Step 3: Run the focused test and verify RED**

Run: `./gradlew test --tests com.alechilles.alecsconsole.LiveLogStoreTest`

Expected: compilation fails because `LiveLogStore`, `LogEntry`, and `LogCaptureSubscriber` do not exist.

- [ ] **Step 4: Implement the minimal immutable entry, ring, and adapter**

Use a monotonically increasing sequence assigned under the store lock. `LogEntry.fromRecord` must normalize null logger/message values and build `searchText` once with `Locale.ROOT`.

```java
public record LogEntry(
        long sequence, long epochMillis, int levelValue, String levelName,
        String loggerName, String message, String searchText) {
    static LogEntry fromRecord(long sequence, LogRecord record) { /* normalize once */ }
}

public final class LiveLogStore {
    private final int capacity;
    private final ArrayDeque<LogEntry> entries;
    private long sequence;

    public synchronized LogEntry append(LogRecord record) { /* evict then append */ }
    public synchronized List<LogEntry> snapshotNewest(int limit) { /* immutable copy */ }
    public synchronized List<LogEntry> snapshotAfter(long sequence, int limit) { /* ascending */ }
    public synchronized int size() { return entries.size(); }
    public synchronized long latestSequence() { return sequence; }
}

public final class LogCaptureSubscriber extends CopyOnWriteArrayList<LogRecord> {
    private final LiveLogStore store;
    @Override public boolean add(LogRecord record) {
        store.append(record);
        return true;
    }
}
```

Do not retain records in the inherited list and do not log inside `add`.

- [ ] **Step 5: Run the focused and complete test suites**

Run: `./gradlew test --tests com.alechilles.alecsconsole.LiveLogStoreTest && ./gradlew test`

Expected: PASS.

- [ ] **Step 6: Commit the scoped deliverable**

```bash
git add .gitignore settings.gradle build.gradle gradle.properties gradle gradlew gradlew.bat src/main/java/com/alechilles/alecsconsole/LogEntry.java src/main/java/com/alechilles/alecsconsole/LiveLogStore.java src/main/java/com/alechilles/alecsconsole/LogCaptureSubscriber.java src/test/java/com/alechilles/alecsconsole/LiveLogStoreTest.java
git commit -m "Feat: scaffold bounded console log capture"
```

---

### Task 2: Add pure live and historical filtering

**Files:**
- Create: `src/main/java/com/alechilles/alecsconsole/LogFilter.java`
- Create: `src/test/java/com/alechilles/alecsconsole/LogFilterTest.java`
- Modify: `src/main/java/com/alechilles/alecsconsole/LogEntry.java`

**Interfaces:**
- Consumes: `LogEntry.levelValue()`, `loggerName()`, and `searchText()`.
- Produces: `LogFilter(int minimumLevel, String loggerName, String text)` and `boolean matches(LogEntry)`; `LogFilter.ALL_LOGGERS`.

- [ ] **Step 1: Write the failing combined-filter test**

```java
@Test
void combinesMinimumLevelLoggerAndText() {
    LogEntry warning = entry(Level.WARNING, "NPC", "Dragon path failed");
    LogEntry info = entry(Level.INFO, "NPC", "Dragon spawned");
    LogEntry other = entry(Level.SEVERE, "World", "Dragon path failed");
    LogFilter filter = new LogFilter(Level.WARNING.intValue(), "NPC", "PATH FAILED");

    assertTrue(filter.matches(warning));
    assertFalse(filter.matches(info));
    assertFalse(filter.matches(other));
}
```

Also assert that empty text and `ALL_LOGGERS` accept records at or above the threshold.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew test --tests com.alechilles.alecsconsole.LogFilterTest`

Expected: compilation fails because `LogFilter` does not exist.

- [ ] **Step 3: Implement the pure filter**

```java
public record LogFilter(int minimumLevel, String loggerName, String text) {
    public static final String ALL_LOGGERS = "ALL";

    public LogFilter {
        loggerName = normalizeLogger(loggerName);
        text = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    public boolean matches(LogEntry entry) {
        return entry.levelValue() >= minimumLevel
            && (ALL_LOGGERS.equals(loggerName) || loggerName.equals(entry.loggerName()))
            && (text.isEmpty() || entry.searchText().contains(text));
    }
}
```

- [ ] **Step 4: Run focused and complete tests**

Run: `./gradlew test --tests com.alechilles.alecsconsole.LogFilterTest && ./gradlew test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alechilles/alecsconsole/LogEntry.java src/main/java/com/alechilles/alecsconsole/LogFilter.java src/test/java/com/alechilles/alecsconsole/LogFilterTest.java
git commit -m "Feat: add console log filtering"
```

---

### Task 3: Add bounded streaming file browsing and configuration

**Files:**
- Create: `src/main/java/com/alechilles/alecsconsole/AlecsConsoleConfig.java`
- Create: `src/main/java/com/alechilles/alecsconsole/LogFileCatalog.java`
- Create: `src/main/java/com/alechilles/alecsconsole/HytaleLogReader.java`
- Create: `src/test/java/com/alechilles/alecsconsole/HytaleLogReaderTest.java`

**Interfaces:**
- Consumes: `LogFilter.matches(LogEntry)` and normalized/canonical allowed roots.
- Produces: `AlecsConsoleConfig.validated()`, `LogFileCatalog.refresh()`, `resolve(String catalogId)`, and `HytaleLogReader.search(Path, LogFilter, int, BooleanSupplier)` returning `SearchResult(List<LogEntry> newestFirst, long totalMatches, boolean capped, Status status, String detail)`.

- [ ] **Step 1: Write the failing real-format reader test**

Create a `@TempDir` file containing:

```text
[2026/08/09 04:23:02   INFO]           [HytaleServer] Starting HytaleServer
[2026/08/09 04:23:03 WARNING]                    [NPC] Dragon path failed
continued diagnostic detail
[2026/08/09 04:23:04 SEVERE]                    [NPC] Dragon path failed again
```

Assert that a WARNING/NPC/`path` filter returns the two newest-first NPC entries, continuation text is attached to the WARNING entry, and a result limit of one reports `totalMatches == 2`, `capped == true`, and retains only the SEVERE entry.

- [ ] **Step 2: Run the reader test and verify RED**

Run: `./gradlew test --tests com.alechilles.alecsconsole.HytaleLogReaderTest`

Expected: compilation fails because `HytaleLogReader` does not exist.

- [ ] **Step 3: Implement `AlecsConsoleConfig` with Hytale's codec**

Use `BuilderCodec<AlecsConsoleConfig>` with keyed integer/string fields and defaults. Provide getters plus:

```java
public AlecsConsoleConfig validated() {
    liveCapacity = clamp(liveCapacity, 100, 50_000);
    pageSize = clamp(pageSize, 25, 500);
    searchDebounceMs = clamp(searchDebounceMs, 100, 2_000);
    diskResultLimit = clamp(diskResultLimit, 100, 20_000);
    clientLogDirectory = blankToNull(clientLogDirectory);
    return this;
}
```

The plugin constructor will register it with `withConfig(AlecsConsoleConfig.CODEC)` so Hytale owns `config.json` creation/loading.

- [ ] **Step 4: Implement the safe file catalog**

Use a stable catalog ID derived from source plus normalized relative filename; never expose a path from event data.

```java
public final class LogFileCatalog {
    public enum Source { CURRENT_SERVER, PREVIOUS_SERVER, CLIENT }
    public record Option(String id, Source source, String displayName, Path path) {}

    public synchronized List<Option> refresh() { /* enumerate allowed roots */ }
    public synchronized Optional<Option> resolve(String id) { /* catalog lookup only */ }
}
```

Server root is `Path.of("logs").toAbsolutePath().normalize()`. Enumerate regular `*.log` files, classify newest `*_server.log` as current and older ones as previous, and sort newest first by last-modified time then filename. Client roots are standard Windows `%APPDATA%/Hytale/data/release/Logs` and `pre-release/Logs` plus the optional configured directory. Before returning or opening any option, resolve real/normalized paths and verify `candidate.startsWith(root)`.

- [ ] **Step 5: Implement the streaming reader**

Use `Files.newBufferedReader(path, UTF_8)`, the exact Hytale prefix regex, one pending parsed record for continuation lines, and an `ArrayDeque` capped at `resultLimit`. Increment `totalMatches` even when evicting an older match. Check `cancelled.getAsBoolean()` and `Thread.currentThread().isInterrupted()` inside the line loop and return `Status.CANCELED` without partial UI replacement.

- [ ] **Step 6: Run focused and complete tests**

Run: `./gradlew test --tests com.alechilles.alecsconsole.HytaleLogReaderTest && ./gradlew test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/alechilles/alecsconsole/AlecsConsoleConfig.java src/main/java/com/alechilles/alecsconsole/LogFileCatalog.java src/main/java/com/alechilles/alecsconsole/HytaleLogReader.java src/test/java/com/alechilles/alecsconsole/HytaleLogReaderTest.java
git commit -m "Feat: add bounded historical log search"
```

---

### Task 4: Build the bounded interactive console page and scheduler

**Files:**
- Create: `src/main/java/com/alechilles/alecsconsole/ConsoleEventData.java`
- Create: `src/main/java/com/alechilles/alecsconsole/ConsolePage.java`
- Create: `src/main/java/com/alechilles/alecsconsole/ConsolePageManager.java`
- Create: `src/main/resources/Common/UI/Custom/Pages/AlecsConsole.ui`
- Create: `src/main/resources/Common/UI/Custom/Pages/AlecsConsoleRow.ui`

**Interfaces:**
- Consumes: bounded live snapshots, `LogFilter`, catalog options, async `SearchResult`, manager scheduling methods.
- Produces: page methods `flushLive()`, `applyDebouncedText(long generation)`, `applySearchResult(long generation, SearchResult)`, `cancelPendingWork()`, and `onDismiss(Ref<EntityStore>, Store<EntityStore>)`; manager methods `openFor(PlayerRef)`, `pageDismissed(ConsolePage)`, `scheduleDebounce(ConsolePage, long generation)`, `submitFileSearch(ConsolePage, String catalogId, LogFilter, long generation)`, and idempotent `close()`.

- [ ] **Step 1: Define event payload codec and actions**

Use one `ConsoleEventData` codec with fields `Action`, `Value`, `RowSequence`, and `CatalogId`. Actions are an enum/string switch limited to:

```text
SourceChanged, FileChanged, LevelChanged, LoggerChanged, SearchTextChanged,
SearchSubmitted, PauseToggled, MultiSelectToggled, RowSelected,
ClearSelection, PreviousPage, NextPage, RefreshFiles
```

Reject missing/unknown actions and parse row sequences defensively.

- [ ] **Step 2: Implement the page manager**

```java
public final class ConsolePageManager implements AutoCloseable {
    private final ScheduledExecutorService scheduler;
    private final ExecutorService fileExecutor;
    private final Set<ConsolePage> pages = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public void start() { /* fixed 250 ms live flush */ }
    public void openFor(PlayerRef playerRef) { /* world.execute and page open */ }
    public void pageDismissed(ConsolePage page) { /* remove and cancel */ }
    public void close() { /* cancel tasks, close pages, shutdownNow */ }
}
```

Do not use a repeating file scan. Catalog refresh happens on page open and explicit source/file refresh only.

- [ ] **Step 3: Author the main UI and row template**

Use a dark full-page panel with:

```text
Title / status / match count
Source dropdown | file dropdown | refresh
Minimum level dropdown | logger dropdown | search input | Search
Pause/Follow | Multi-select | Clear selection
Bounded scrollable row list
Previous | page label | Next
Selected full-text multiline field
```

The row template must expose `#Timestamp`, `#Level`, `#Logger`, `#Message`, `#Selection`, and `#SelectButton`. Keep one-line truncation in the row; the selected field shows complete text.

- [ ] **Step 4: Implement bounded page build and rebuild helpers**

`build` appends `Pages/AlecsConsole.ui`, binds static controls once, loads the current state, and renders at most `pageSize` rows. `renderRows` clears only `#Rows`, appends `AlecsConsoleRow.ui` once per visible entry, sets fields/colors, and binds one row action per row. Preserve a `Map<Long, LogEntry>` only for the currently rendered page.

- [ ] **Step 5: Implement debounce, pause/follow, filters, and selection**

- Search text increments a generation and asks the manager to schedule one trailing callback after `searchDebounceMs`.
- Level/logger changes update the filter and rebuild immediately.
- Pause records the last delivered sequence and increments an unseen counter during `flushLive`; follow takes a newest bounded snapshot and resets unseen count.
- Normal selection replaces the selected sequence set; multi-select toggles it.
- Selected text preserves display order and caps at 200 rows/20,000 characters with a visible truncation marker.
- Dismissal removes the page from the manager and invalidates generations.

- [ ] **Step 6: Implement explicit historical search and paging**

Typing updates only the page field. Search/Enter submits one selected catalog ID plus current filter to the file executor. While searching, keep the previous results visible with `Searching…`. Apply only the matching generation. Render newest-first pages of at most `pageSize`; disable paging buttons at bounds; show capped and unavailable statuses.

- [ ] **Step 7: Validate UI and engine references**

Run Hytale Workshop `validate_hytale_ui` for both `.ui` files and correct every fatal/structural warning. Run `validate_hytale_code_refs` on `ConsolePage.java` and `ConsoleEventData.java`.

Run: `./gradlew compileJava processResources test validateManifest`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/alechilles/alecsconsole/ConsoleEventData.java src/main/java/com/alechilles/alecsconsole/ConsolePage.java src/main/java/com/alechilles/alecsconsole/ConsolePageManager.java src/main/resources/Common/UI/Custom/Pages/AlecsConsole.ui src/main/resources/Common/UI/Custom/Pages/AlecsConsoleRow.ui
git commit -m "Feat: add responsive console viewer UI"
```

---

### Task 5: Compose the Hytale lifecycle and command

**Files:**
- Create: `src/main/java/com/alechilles/alecsconsole/OpenConsoleCommand.java`
- Create: `src/main/java/com/alechilles/alecsconsole/AlecsConsolePlugin.java`

**Interfaces:**
- Consumes: `LiveLogStore`, `LogCaptureSubscriber`, `AlecsConsoleConfig`, `LogFileCatalog`, `HytaleLogReader`, and `ConsolePageManager`.
- Produces: a loadable `Alechilles:Alec's Console` plugin and permission-gated player command.

- [ ] **Step 1: Implement the player-only command**

Extend the current 0.5.7 player command base. Register name `console`, description `Open Alec's Console`, call `requirePermission("alechilles.alecsconsole.command.console")`, and delegate to `manager.openFor(playerRef)`.

- [ ] **Step 2: Implement plugin composition**

In the constructor, call `withConfig(AlecsConsoleConfig.CODEC)`. In `setup()`, read/validate config, build services, subscribe the adapter, register the command, and start the manager. In `shutdown()`, unsubscribe first, close the manager, null references, and tolerate repeated calls.

```java
public final class AlecsConsolePlugin extends JavaPlugin {
    private final Config<AlecsConsoleConfig> config;
    private LogCaptureSubscriber subscriber;
    private ConsolePageManager pageManager;

    public AlecsConsolePlugin(JavaPluginInit init) {
        super(init);
        config = withConfig(AlecsConsoleConfig.CODEC);
    }
}
```

- [ ] **Step 3: Validate engine references and compile**

Run Hytale Workshop `validate_hytale_code_refs` on both new Java files, then:

Run: `./gradlew compileJava test updatePluginManifest validateManifest`

Expected: all tasks succeed, manifest main class and plugin identifier are correct, and no deprecated/unknown engine reference remains unexplained.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alechilles/alecsconsole/OpenConsoleCommand.java src/main/java/com/alechilles/alecsconsole/AlecsConsolePlugin.java src/main/resources/manifest.json
git commit -m "Feat: wire console plugin lifecycle"
```

---

### Task 6: Register the workspace module and document operation

**Files:**
- Create: `README.md`
- Modify: `../settings.gradle`
- Modify: `../build.gradle`

**Interfaces:**
- Consumes: standalone build and manifest identity.
- Produces: parent workspace project `:alecs-console` and user-facing setup/permission documentation.

- [ ] **Step 1: Register the standalone repo in the parent workspace**

Add `'alecs-console'` to `include(...)`, map it with:

```groovy
project(':alecs-console').projectDir = file('AlecsConsole')
```

Add `':alecs-console'` to `hytaleWorkspace.modProjects`. Do not alter unrelated workspace settings.

- [ ] **Step 2: Write the concise README**

Document:

- `/console` and the confirmed permission node.
- Live, current, previous, and same-host client sources.
- 300 ms live debounce, explicit disk Search, pause/follow, selection/copy, and bounded defaults.
- Remote clients cannot upload logs; client source is same-host only.
- `./gradlew build`, `./gradlew runServer`, config location/fields, and jar location.
- Attribution that the feature set was inspired by `duke-darius/hytale-console`, while this implementation is from scratch.

- [ ] **Step 3: Verify standalone and workspace builds**

Run from the standalone repo:

```bash
./gradlew clean build validateManifest
```

Run from the parent workspace:

```bash
../gradlew :alecs-console:build :alecs-console:validateManifest
```

Expected: both succeed without changing unrelated repositories.

- [ ] **Step 4: Commit each repository independently**

Standalone repo:

```bash
git add README.md
git commit -m "Docs: document Alec's Console"
```

Parent workspace repo:

```bash
git -C .. add settings.gradle build.gradle
git -C .. commit -m "Build: register AlecsConsole workspace module"
```

---

### Task 7: Local Hytale smoke verification and cleanup

**Files:**
- Modify only if a verified runtime defect requires a focused fix.

**Interfaces:**
- Consumes: complete plugin and workspace integration.
- Produces: verified build artifact and stopped development processes.

- [ ] **Step 1: Run a bounded development server smoke**

Start `./gradlew runServer` in a PTY. Verify startup identifies `Alechilles:Alec's Console`, creates/loads config, and reports no UI parsing or plugin lifecycle errors. Do not leave it running unattended.

- [ ] **Step 2: Exercise the user-visible behavior**

Check permission denial/allow, `/console`, live capture, 300 ms text debounce, discrete filters, pause/unseen/follow, single and multi-select copying, current/previous file search, client-source unavailable behavior, paging, dismissal, and reopen.

- [ ] **Step 3: Exercise the size-sensitive paths**

Use an existing large server log through the historical browser. Confirm the world remains responsive, the UI page never exceeds its configured row count, only the newest 2,000 matches are retained, and typing without Search performs no disk scan.

- [ ] **Step 4: Stop processes and run final verification**

Stop the server normally, then run:

```bash
./gradlew test build validateManifest --no-daemon
./gradlew --stop
git status --short
git -C .. status --short
```

Expected: tests/build/manifest pass; no server or Gradle daemon remains; only intentional AlecsConsole and parent workspace changes are present.

- [ ] **Step 5: Review and commit any smoke fixes separately**

If runtime verification required code changes, stage only those files and commit with a focused `Fix:` message. If no changes were required, do not create an empty commit.
