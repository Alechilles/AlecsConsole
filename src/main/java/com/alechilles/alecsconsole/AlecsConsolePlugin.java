package com.alechilles.alecsconsole;

import com.hypixel.hytale.logger.backend.HytaleLoggerBackend;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.util.logging.Level;

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
        if (settings.validate()) {
            getLogger().at(Level.WARNING).log(
                    "Some Alec's Console settings were outside supported limits and were clamped.");
        }
        try {
            config.save().join();
        } catch (RuntimeException exception) {
            getLogger().at(Level.WARNING).withCause(exception)
                    .log("Could not save Alec's Console config; using the in-memory settings.");
        }

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
