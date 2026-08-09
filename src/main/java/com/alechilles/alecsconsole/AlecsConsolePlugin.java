package com.alechilles.alecsconsole;

import com.hypixel.hytale.logger.backend.HytaleLoggerBackend;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;

/** Alec's Console plugin entry point. */
public final class AlecsConsolePlugin extends JavaPlugin {
    private final Config<AlecsConsoleConfig> config;
    private LogCaptureSubscriber subscriber;
    private ConsolePageManager pageManager;

    public AlecsConsolePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        config = withConfig(AlecsConsoleConfig.CODEC);
    }

    @Override
    protected void setup() {
        AlecsConsoleConfig settings = config.get();
        if (settings == null) {
            settings = new AlecsConsoleConfig();
        }
        settings.validated();

        LiveLogStore liveStore = new LiveLogStore(settings.getLiveCapacity());
        LogCaptureSubscriber capture = new LogCaptureSubscriber(liveStore);
        ConsolePageManager manager = new ConsolePageManager(
                liveStore, settings, new LogFileCatalog(settings.getClientLogDirectory()));

        subscriber = capture;
        pageManager = manager;
        HytaleLoggerBackend.subscribe(capture);
        getCommandRegistry().registerCommand(new OpenConsoleCommand(manager));
        manager.start();
        getLogger().atInfo().log("Alec's Console is ready. Use /console.");
    }

    @Override
    protected void shutdown() {
        LogCaptureSubscriber capture = subscriber;
        subscriber = null;
        if (capture != null) {
            HytaleLoggerBackend.unsubscribe(capture);
        }

        ConsolePageManager manager = pageManager;
        pageManager = null;
        if (manager != null) {
            manager.close();
        }
    }
}
