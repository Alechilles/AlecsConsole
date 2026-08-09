package com.alechilles.alecsconsole;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsolePageHistoricalTest {
    @TempDir
    Path tempDir;

    @Test
    void selectingServerLogsLoadsTheFirstFileWithoutASecondAction() throws Exception {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("2026-08-09_19-17-50_server.log"),
                "[2026/08/09 23:17:50 INFO] [Test] historical message\n",
                StandardCharsets.UTF_8);
        LogFileCatalog catalog = new LogFileCatalog(logs, null);
        ConsolePageManager manager = new ConsolePageManager(
                new LiveLogStore(100), new AlecsConsoleConfig(), catalog);
        PlayerRef player = new PlayerRef(null, UUID.randomUUID(), "test", "en-US", null, null);
        ConsolePage page = new ConsolePage(player, manager, new LiveLogStore(100),
                new AlecsConsoleConfig(), catalog.refresh());

        try {
            page.handleDataEvent(null, null,
                    "{\"Action\":\"SourceChanged\",\"@Value\":\"SERVER\"}");

            assertTrue(waitForMessage(page, "historical message", Duration.ofSeconds(2)));
        } finally {
            manager.close();
        }
    }

    @Test
    void selectingAnotherFileLoadsThatFileImmediately() throws Exception {
        Path logs = tempDir.resolve("other-logs");
        Files.createDirectories(logs);
        Path older = logs.resolve("2026-08-09_19-00-00_server.log");
        Path newer = logs.resolve("2026-08-09_20-00-00_server.log");
        Files.writeString(older, "[2026/08/09 19:00:00 INFO] [Test] older message\n",
                StandardCharsets.UTF_8);
        Files.writeString(newer, "[2026/08/09 20:00:00 INFO] [Test] newer message\n",
                StandardCharsets.UTF_8);
        Files.setLastModifiedTime(older, FileTime.fromMillis(1));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2));
        LogFileCatalog catalog = new LogFileCatalog(logs, null);
        List<LogFileCatalog.Option> options = catalog.refresh();
        Path olderReal = older.toRealPath();
        String olderId = options.stream()
                .filter(option -> option.path().equals(olderReal))
                .findFirst().orElseThrow().id();
        ConsolePageManager manager = new ConsolePageManager(
                new LiveLogStore(100), new AlecsConsoleConfig(), catalog);
        PlayerRef player = new PlayerRef(null, UUID.randomUUID(), "test", "en-US", null, null);
        ConsolePage page = new ConsolePage(player, manager, new LiveLogStore(100),
                new AlecsConsoleConfig(), options);

        try {
            page.handleDataEvent(null, null,
                    "{\"Action\":\"SourceChanged\",\"@Value\":\"SERVER\"}");
            assertTrue(waitForMessage(page, "newer message", Duration.ofSeconds(2)));

            page.handleDataEvent(null, null,
                    "{\"Action\":\"FileChanged\",\"@CatalogId\":\"" + olderId + "\"}");

            assertTrue(waitForMessage(page, "older message", Duration.ofSeconds(2)));
        } finally {
            manager.close();
        }
    }

    private static boolean waitForMessage(ConsolePage page, String message, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            UICommandBuilder commands = new UICommandBuilder();
            page.build(null, commands, new UIEventBuilder(), null);
            for (var command : commands.getCommands()) {
                if ("#Rows[0] #Message.Text".equals(command.selector)
                        && command.data != null && command.data.contains(message)) {
                    return true;
                }
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        return false;
    }
}
