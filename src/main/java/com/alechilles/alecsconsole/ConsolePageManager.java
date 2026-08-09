package com.alechilles.alecsconsole;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the small amount of background work shared by open console pages. */
public final class ConsolePageManager implements AutoCloseable {
    private final LiveLogStore liveStore;
    private final AlecsConsoleConfig config;
    private final LogFileCatalog catalog;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "alecs-console-live");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "alecs-console-files");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<ConsolePage> pages = ConcurrentHashMap.newKeySet();
    private final Map<ConsolePage, ScheduledFuture<?>> debounceTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private ScheduledFuture<?> liveTask;

    public ConsolePageManager(LiveLogStore liveStore, AlecsConsoleConfig config, LogFileCatalog catalog) {
        this.liveStore = liveStore;
        this.config = config;
        this.catalog = catalog;
    }

    public synchronized void start() {
        if (liveTask != null || closed.get()) {
            return;
        }
        liveTask = scheduler.scheduleWithFixedDelay(this::flushPages, 250, 250, TimeUnit.MILLISECONDS);
    }

    public void openFor(PlayerRef playerRef) {
        if (closed.get()) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (closed.get() || !ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            ConsolePage page = new ConsolePage(playerRef, this, liveStore, config, List.of());
            PageManager pageManager = player.getPageManager();
            pageManager.openCustomPage(ref, store, page);
            pages.add(page);
            refreshFiles(page);
        });
    }

    void pageDismissed(ConsolePage page) {
        pages.remove(page);
        ScheduledFuture<?> debounce = debounceTasks.remove(page);
        if (debounce != null) {
            debounce.cancel(false);
        }
        page.cancelPendingWork();
    }

    void scheduleDebounce(ConsolePage page, long generation) {
        ScheduledFuture<?> previous = debounceTasks.put(page,
                scheduler.schedule(() -> {
                    debounceTasks.remove(page);
                    page.applyDebouncedText(generation);
                }, config.getSearchDebounceMs(), TimeUnit.MILLISECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    void submitFileSearch(ConsolePage page, String catalogId, LogFilter filter, long generation) {
        fileExecutor.execute(() -> {
            LogFileCatalog.Option option = catalog.resolve(catalogId).orElse(null);
            HytaleLogReader.SearchResult result = option == null
                    ? new HytaleLogReader.SearchResult(List.of(), 0, false,
                    HytaleLogReader.Status.FAILED, "Log file is unavailable")
                    : HytaleLogReader.search(option.path(), option.allowedRoot(), filter,
                    config.getDiskResultLimit(), () -> !page.isSearchCurrent(generation));
            page.applySearchResult(generation, result);
        });
    }

    List<LogFileCatalog.Option> refreshFiles() {
        return catalog.refresh();
    }

    void refreshFiles(ConsolePage page) {
        fileExecutor.execute(() -> page.applyFileOptions(refreshFiles()));
    }

    private void flushPages() {
        if (closed.get()) {
            return;
        }
        for (ConsolePage page : pages) {
            try {
                page.flushLive();
            } catch (RuntimeException ignored) {
                // One closed/broken page must not stop refreshes for the others.
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            if (liveTask != null) {
                liveTask.cancel(false);
                liveTask = null;
            }
        }
        for (ConsolePage page : pages) {
            page.cancelPendingWork();
        }
        pages.clear();
        for (ScheduledFuture<?> task : debounceTasks.values()) {
            task.cancel(false);
        }
        debounceTasks.clear();
        scheduler.shutdownNow();
        fileExecutor.shutdownNow();
    }
}
