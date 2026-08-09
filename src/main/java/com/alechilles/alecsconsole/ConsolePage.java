package com.alechilles.alecsconsole;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

/** Bounded, paged console viewer. */
public final class ConsolePage extends InteractiveCustomUIPage<ConsoleEventData> {
    private static final String LAYOUT = "Pages/AlecsConsole.ui";
    private static final String ROW_LAYOUT = "Pages/AlecsConsoleRow.ui";
    private static final String LIVE = "LIVE";
    private static final String SERVER = "SERVER";
    private static final String CLIENT = "CLIENT";
    private static final int MAX_SELECTED_ROWS = 200;
    private static final int MAX_SELECTED_CHARS = 20_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ConsolePageManager manager;
    private final LiveLogStore liveStore;
    private final AlecsConsoleConfig config;
    private final Map<Long, LogEntry> renderedRows = new LinkedHashMap<>();
    private final LinkedHashMap<Long, LogEntry> selectedRows = new LinkedHashMap<>();
    private List<LogFileCatalog.Option> fileOptions;
    private List<LogEntry> historicalRows = List.of();
    private List<LogEntry> pausedSnapshot = List.of();
    private String source = LIVE;
    private String catalogId;
    private String logger = LogFilter.ALL_LOGGERS;
    private int minimumLevel = Integer.MIN_VALUE;
    private String searchText = "";
    private boolean paused;
    private boolean multiSelect;
    private boolean canceled;
    private long lastDeliveredSequence;
    private long unseen;
    private long debounceGeneration;
    private long searchGeneration;
    private int pageIndex;
    private String status = "Live";

    ConsolePage(PlayerRef playerRef, ConsolePageManager manager, LiveLogStore liveStore,
                AlecsConsoleConfig config, List<LogFileCatalog.Option> fileOptions) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ConsoleEventData.CODEC);
        this.manager = manager;
        this.liveStore = liveStore;
        this.config = config;
        this.fileOptions = List.copyOf(fileOptions);
        this.lastDeliveredSequence = liveStore.latestSequence();
    }

    @Override
    public synchronized void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                                   @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        bindStaticEvents(events);
        commands.set("#SearchText.Value", searchText);
        setControlState(commands);
        renderRows(commands, events, liveRows());
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        manager.pageDismissed(this);
    }

    @Override
    public synchronized void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull ConsoleEventData data) {
        ConsoleEventData.Action action = data.action();
        if (action == null || canceled) {
            return;
        }
        switch (action) {
            case SourceChanged -> changeSource(data.value());
            case FileChanged -> {
                catalogId = data.catalogId() == null ? data.value() : data.catalogId();
                historicalRows = List.of();
                selectedRows.clear();
                pageIndex = 0;
                status = "Ready to search";
                updateRowsAndControls(List.of());
            }
            case LevelChanged -> {
                minimumLevel = levelValue(data.value());
                refreshForFilterChange();
            }
            case LoggerChanged -> {
                logger = data.value() == null ? LogFilter.ALL_LOGGERS : data.value();
                refreshForFilterChange();
            }
            case SearchTextChanged -> {
                searchText = data.value() == null ? "" : data.value();
                long generation = ++debounceGeneration;
                manager.scheduleDebounce(this, generation);
            }
            case SearchSubmitted -> submitHistoricalSearch();
            case PauseToggled -> togglePause();
            case MultiSelectToggled -> {
                multiSelect = !multiSelect;
                updateControlsOnly();
            }
            case RowSelected -> selectRow(data.parsedRowSequence());
            case ClearSelection -> {
                selectedRows.clear();
                updateRowsAndControls(currentRows());
            }
            case PreviousPage -> changePage(-1);
            case NextPage -> changePage(1);
            case RefreshFiles -> refreshFiles();
        }
    }

    public synchronized void flushLive() {
        if (canceled || !LIVE.equals(source)) {
            return;
        }
        long latest = liveStore.latestSequence();
        if (latest <= lastDeliveredSequence) {
            return;
        }
        if (paused) {
            unseen += latest - lastDeliveredSequence;
            lastDeliveredSequence = latest;
            updateControlsOnly();
            return;
        }
        lastDeliveredSequence = latest;
        unseen = 0;
        pageIndex = 0;
        updateRowsAndControls(liveRows());
    }

    public synchronized void applyDebouncedText(long generation) {
        if (canceled || generation != debounceGeneration || !LIVE.equals(source)) {
            return;
        }
        pageIndex = 0;
        updateRowsAndControls(liveRows());
    }

    public synchronized void applySearchResult(long generation, HytaleLogReader.SearchResult result) {
        if (!isSearchCurrent(generation) || result.status() == HytaleLogReader.Status.CANCELED) {
            return;
        }
        historicalRows = result.newestFirst();
        pageIndex = 0;
        status = switch (result.status()) {
            case COMPLETE -> result.capped()
                    ? result.totalMatches() + " matches (showing newest " + historicalRows.size() + ")"
                    : result.totalMatches() + " matches";
            case FAILED -> "Unavailable: " + result.detail();
            case CANCELED -> "Search canceled";
        };
        updateRowsAndControls(currentRows());
    }

    synchronized void applyFileOptions(List<LogFileCatalog.Option> options) {
        if (canceled) {
            return;
        }
        fileOptions = List.copyOf(options);
        chooseFirstFile();
        status = "Files refreshed";
        updateControlsOnly();
    }

    public synchronized void cancelPendingWork() {
        canceled = true;
        debounceGeneration++;
        searchGeneration++;
        renderedRows.clear();
        selectedRows.clear();
    }

    synchronized boolean isSearchCurrent(long generation) {
        return !canceled && generation == searchGeneration;
    }

    private void changeSource(String value) {
        source = switch (value == null ? "" : value) {
            case SERVER -> SERVER;
            case CLIENT -> CLIENT;
            default -> LIVE;
        };
        pageIndex = 0;
        historicalRows = List.of();
        pausedSnapshot = List.of();
        selectedRows.clear();
        status = LIVE.equals(source) ? "Live" : "Choose a file, then Search";
        chooseFirstFile();
        updateRowsAndControls(currentRows());
    }

    private void refreshForFilterChange() {
        pageIndex = 0;
        if (LIVE.equals(source)) {
            updateRowsAndControls(liveRows());
        } else {
            submitHistoricalSearch();
        }
    }

    private void submitHistoricalSearch() {
        if (LIVE.equals(source)) {
            long generation = ++debounceGeneration;
            applyDebouncedText(generation);
            return;
        }
        if (catalogId == null || catalogId.isBlank()) {
            status = "No log file available";
            updateControlsOnly();
            return;
        }
        long generation = ++searchGeneration;
        status = "Searching…";
        updateControlsOnly();
        manager.submitFileSearch(this, catalogId, filter(), generation);
    }

    private void togglePause() {
        if (!LIVE.equals(source)) {
            return;
        }
        paused = !paused;
        if (paused) {
            pausedSnapshot = liveStore.snapshotNewest(config.getLiveCapacity());
            updateControlsOnly();
        } else {
            pausedSnapshot = List.of();
            lastDeliveredSequence = liveStore.latestSequence();
            unseen = 0;
            pageIndex = 0;
            updateRowsAndControls(liveRows());
        }
    }

    private void selectRow(Long sequence) {
        if (sequence == null) {
            return;
        }
        LogEntry entry = renderedRows.get(sequence);
        if (entry == null) {
            return;
        }
        if (!multiSelect) {
            selectedRows.clear();
            selectedRows.put(sequence, entry);
        } else if (selectedRows.containsKey(sequence)) {
            selectedRows.remove(sequence);
        } else if (selectedRows.size() < MAX_SELECTED_ROWS) {
            selectedRows.put(sequence, entry);
        }
        updateRowsAndControls(currentRows());
    }

    private void changePage(int delta) {
        List<LogEntry> rows = LIVE.equals(source) ? filteredLiveRows() : historicalRows;
        int pageCount = pageCount(rows.size());
        pageIndex = Math.max(0, Math.min(Math.max(0, pageCount - 1), pageIndex + delta));
        updateRowsAndControls(page(rows));
    }

    private void refreshFiles() {
        status = "Refreshing files…";
        updateControlsOnly();
        manager.refreshFiles(this);
    }

    private void chooseFirstFile() {
        List<LogFileCatalog.Option> available = visibleFileOptions();
        boolean selectionStillExists = available.stream().anyMatch(option -> option.id().equals(catalogId));
        if (!selectionStillExists) {
            catalogId = available.isEmpty() ? null : available.get(0).id();
        }
    }

    private List<LogEntry> currentRows() {
        return LIVE.equals(source) ? liveRows() : page(historicalRows);
    }

    private List<LogEntry> liveRows() {
        return page(filteredLiveRows());
    }

    private List<LogEntry> filteredLiveRows() {
        List<LogEntry> snapshot = paused
                ? pausedSnapshot
                : liveStore.snapshotNewest(config.getLiveCapacity());
        ArrayList<LogEntry> matches = new ArrayList<>();
        LogFilter filter = filter();
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            LogEntry entry = snapshot.get(index);
            if (filter.matches(entry)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private List<LogEntry> page(List<LogEntry> rows) {
        int start = Math.min(rows.size(), pageIndex * config.getPageSize());
        int end = Math.min(rows.size(), start + config.getPageSize());
        return rows.subList(start, end);
    }

    private int pageCount(int rowCount) {
        return Math.max(1, (rowCount + config.getPageSize() - 1) / config.getPageSize());
    }

    private LogFilter filter() {
        return new LogFilter(minimumLevel, logger, searchText);
    }

    private void updateRowsAndControls(List<LogEntry> rows) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        setControlState(commands);
        renderRows(commands, events, rows);
        sendUpdate(commands, events, false);
    }

    private void updateControlsOnly() {
        UICommandBuilder commands = new UICommandBuilder();
        setControlState(commands);
        sendUpdate(commands, null, false);
    }

    private void setControlState(UICommandBuilder commands) {
        commands.set("#Source.Entries", sourceEntries());
        commands.set("#Source.Value", source);
        commands.set("#File.Entries", fileEntries());
        commands.set("#File.Value", catalogId == null ? "" : catalogId);
        commands.set("#Level.Entries", levelEntries());
        commands.set("#Level.Value", levelName(minimumLevel));
        commands.set("#Logger.Entries", loggerEntries());
        commands.set("#Logger.Value", logger);
        commands.set("#Pause.Text", paused ? "Follow" : "Pause");
        commands.set("#MultiSelect.Text", multiSelect ? "Multi: on" : "Multi: off");
        commands.set("#Status.Text", liveStatus());
        commands.set("#Page.Text", "Page " + (pageIndex + 1) + " / " + pageCount(currentRowCount()));
        commands.set("#Previous.Disabled", pageIndex <= 0);
        commands.set("#Next.Disabled", pageIndex + 1 >= pageCount(currentRowCount()));
        commands.set("#Selected.Value", selectedText());
    }

    private String liveStatus() {
        if (LIVE.equals(source)) {
            return paused && unseen > 0 ? "Paused · " + unseen + " new" : paused ? "Paused" : "Live";
        }
        return status;
    }

    private int currentRowCount() {
        return LIVE.equals(source) ? filteredLiveRows().size() : historicalRows.size();
    }

    private void renderRows(UICommandBuilder commands, UIEventBuilder events, List<LogEntry> rows) {
        commands.clear("#Rows");
        renderedRows.clear();
        for (int index = 0; index < rows.size(); index++) {
            LogEntry entry = rows.get(index);
            renderedRows.put(entry.sequence(), entry);
            commands.append("#Rows", ROW_LAYOUT);
            String row = "#Rows[" + index + "] ";
            commands.set(row + "#Timestamp.Text", displayTime(entry));
            commands.set(row + "#Level.Text", entry.levelName());
            commands.set(row + "#Logger.Text", entry.loggerName());
            commands.set(row + "#Message.Text", firstLine(entry.message()));
            commands.set(row + "#Level.Style.TextColor", levelColor(entry.levelValue()));
            commands.set(row + "#Message.Style.TextColor", levelColor(entry.levelValue()));
            commands.set(row + "#Selection.Visible", selectedRows.containsKey(entry.sequence()));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + "#SelectButton",
                    new EventData().append("Action", "RowSelected")
                            .append("RowSequence", Long.toString(entry.sequence())), false);
        }
    }

    private void bindStaticEvents(UIEventBuilder events) {
        bindValue(events, "#Source", "SourceChanged", "Value");
        bindValue(events, "#File", "FileChanged", "CatalogId");
        bindValue(events, "#Level", "LevelChanged", "Value");
        bindValue(events, "#Logger", "LoggerChanged", "Value");
        bindValue(events, "#SearchText", "SearchTextChanged", "Value");
        bindAction(events, "#Search", "SearchSubmitted");
        bindAction(events, "#Pause", "PauseToggled");
        bindAction(events, "#MultiSelect", "MultiSelectToggled");
        bindAction(events, "#ClearSelection", "ClearSelection");
        bindAction(events, "#Previous", "PreviousPage");
        bindAction(events, "#Next", "NextPage");
        bindAction(events, "#RefreshFiles", "RefreshFiles");
    }

    private static void bindValue(UIEventBuilder events, String selector, String action, String key) {
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector,
                new EventData().append("Action", action).append(key, selector + ".Value"), false);
    }

    private static void bindAction(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action), false);
    }

    private List<DropdownEntryInfo> sourceEntries() {
        return List.of(entry("Live", LIVE), entry("Server logs", SERVER), entry("Client logs (same host)", CLIENT));
    }

    private List<DropdownEntryInfo> fileEntries() {
        return visibleFileOptions().stream()
                .map(option -> entry(option.displayName(), option.id()))
                .toList();
    }

    private List<LogFileCatalog.Option> visibleFileOptions() {
        if (LIVE.equals(source)) {
            return List.of();
        }
        return fileOptions.stream()
                .filter(option -> CLIENT.equals(source)
                        ? option.source() == LogFileCatalog.Source.CLIENT
                        : option.source() != LogFileCatalog.Source.CLIENT)
                .toList();
    }

    private List<DropdownEntryInfo> levelEntries() {
        return List.of(entry("ALL", "ALL"), entry("SEVERE", "SEVERE"),
                entry("WARNING", "WARNING"), entry("INFO", "INFO"),
                entry("CONFIG", "CONFIG"), entry("FINE", "FINE"),
                entry("FINER", "FINER"), entry("FINEST", "FINEST"), entry("OFF", "OFF"));
    }

    private List<DropdownEntryInfo> loggerEntries() {
        Set<String> names = new TreeSet<>();
        List<LogEntry> sourceRows = LIVE.equals(source)
                ? liveStore.snapshotNewest(config.getLiveCapacity())
                : historicalRows;
        for (LogEntry entry : sourceRows) {
            names.add(entry.loggerName());
        }
        names.add(logger);
        ArrayList<DropdownEntryInfo> entries = new ArrayList<>(names.size() + 1);
        entries.add(entry("ALL", LogFilter.ALL_LOGGERS));
        for (String name : names) {
            if (!LogFilter.ALL_LOGGERS.equals(name)) {
                entries.add(entry(name.isEmpty() ? "(unnamed)" : name, name));
            }
        }
        return entries;
    }

    private String selectedText() {
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (LogEntry entry : selectedRows.values()) {
            String line = fullText(entry);
            if (count >= MAX_SELECTED_ROWS || text.length() + line.length() + 1 > MAX_SELECTED_CHARS) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append("… (selection truncated)");
                break;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(line);
            count++;
        }
        return text.toString();
    }

    private static DropdownEntryInfo entry(String label, String value) {
        return new DropdownEntryInfo(LocalizableString.fromString(label), value);
    }

    private static String displayTime(LogEntry entry) {
        return entry.epochMillis() <= 0 ? "--:--:--" : TIME_FORMAT.format(Instant.ofEpochMilli(entry.epochMillis()));
    }

    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline) + " …";
    }

    private static String fullText(LogEntry entry) {
        return "[" + displayTime(entry) + "][" + entry.levelName() + "][" + entry.loggerName() + "] "
                + entry.message();
    }

    private static String levelColor(int value) {
        if (value >= Level.SEVERE.intValue()) {
            return "#ff6666";
        }
        if (value >= Level.WARNING.intValue()) {
            return "#ffb86c";
        }
        if (value >= Level.INFO.intValue()) {
            return "#e6e6e6";
        }
        if (value >= Level.CONFIG.intValue()) {
            return "#9aa5b1";
        }
        return "#8be9fd";
    }

    private static int levelValue(String value) {
        if (value == null || "ALL".equals(value)) {
            return Integer.MIN_VALUE;
        }
        if ("OFF".equals(value)) {
            return Integer.MAX_VALUE;
        }
        try {
            return Level.parse(value).intValue();
        } catch (IllegalArgumentException exception) {
            return Integer.MIN_VALUE;
        }
    }

    private static String levelName(int value) {
        if (value == Integer.MIN_VALUE) {
            return "ALL";
        }
        if (value == Integer.MAX_VALUE) {
            return "OFF";
        }
        return Level.parse(Integer.toString(value)).getName();
    }
}
