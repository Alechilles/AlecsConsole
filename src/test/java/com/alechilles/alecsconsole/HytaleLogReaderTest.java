package com.alechilles.alecsconsole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HytaleLogReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void searchesRealHytaleFormatWithContinuationsAndBoundedNewestResults() throws IOException {
        Path log = tempDir.resolve("sample_server.log");
        Files.writeString(log, """
                [2026/08/09 04:23:02   INFO]           [HytaleServer] Starting HytaleServer
                [2026/08/09 04:23:03 WARNING]                    [NPC] Dragon path failed
                continued diagnostic detail
                [2026/08/09 04:23:04 SEVERE]                    [NPC] Dragon path failed again
                """, StandardCharsets.UTF_8);

        LogFilter filter = new LogFilter(Level.WARNING.intValue(), "NPC", "path");

        HytaleLogReader.SearchResult result = HytaleLogReader.search(log, filter, 10, () -> false);

        assertEquals(HytaleLogReader.Status.COMPLETE, result.status());
        assertEquals(2, result.totalMatches());
        assertEquals(List.of("Dragon path failed again", "Dragon path failed\ncontinued diagnostic detail"),
                result.newestFirst().stream().map(LogEntry::message).toList());
        assertTrue(result.newestFirst().get(1).message().contains("continued diagnostic detail"));

        HytaleLogReader.SearchResult capped = HytaleLogReader.search(log, filter, 1, () -> false);

        assertEquals(2, capped.totalMatches());
        assertTrue(capped.capped());
        assertEquals(List.of("Dragon path failed again"),
                capped.newestFirst().stream().map(LogEntry::message).toList());
    }

    @Test
    void searchesClientPipeFormatAsIndividualRecords() throws IOException {
        Path log = tempDir.resolve("sample_client.log");
        Files.writeString(log, """
                2026-08-09 19:32:48.0594|INFO|HytaleClient.Application.Program|HytaleClient v0.5.7
                2026-08-09 19:32:48.2898|WARN|HytaleClient.Application.Program|Revision mismatch
                continued client detail
                """, StandardCharsets.UTF_8);

        HytaleLogReader.SearchResult result = HytaleLogReader.search(log,
                new LogFilter(Level.WARNING.intValue(), "HytaleClient.Application.Program", "revision"),
                10, () -> false);

        assertEquals(HytaleLogReader.Status.COMPLETE, result.status());
        assertEquals(1, result.totalMatches());
        assertEquals("Revision mismatch\ncontinued client detail",
                result.newestFirst().get(0).message());
    }

    @Test
    void retainsLeadingRawLinesAsUnknownMetadataRecords() throws IOException {
        Path log = tempDir.resolve("raw_server.log");
        Files.writeString(log, """
                orphan diagnostic line
                second orphan diagnostic line
                [2026/08/09 04:23:02   INFO]           [HytaleServer] Starting HytaleServer
                """, StandardCharsets.UTF_8);

        LogFilter filter = new LogFilter(Level.ALL.intValue(), LogFilter.ALL_LOGGERS, "orphan");

        HytaleLogReader.SearchResult result = HytaleLogReader.search(log, filter, 10, () -> false);

        assertEquals(1, result.totalMatches());
        assertEquals("orphan diagnostic line\nsecond orphan diagnostic line",
                result.newestFirst().get(0).message());
        assertEquals("", result.newestFirst().get(0).levelName());
        assertEquals("", result.newestFirst().get(0).loggerName());
    }

    @Test
    void searchesOriginalHytaleTimestampPrefixText() throws IOException {
        Path log = tempDir.resolve("timestamp_server.log");
        Files.writeString(log, "[2026/08/09 04:23:03 WARNING] [NPC] Dragon path failed\n",
                StandardCharsets.UTF_8);

        LogFilter filter = new LogFilter(Level.WARNING.intValue(), "NPC", "2026/08/09");

        HytaleLogReader.SearchResult result = HytaleLogReader.search(log, filter, 10, () -> false);

        assertEquals(1, result.totalMatches());
        assertEquals("Dragon path failed", result.newestFirst().get(0).message());
    }

    @Test
    void rejectsNonLogPathsBeforeOpening() throws IOException {
        Path text = tempDir.resolve("not-a-log.txt");
        Files.writeString(text, "[2026/08/09 04:23:03 INFO] [NPC] should not be read\n",
                StandardCharsets.UTF_8);

        HytaleLogReader.SearchResult result = HytaleLogReader.search(text,
                new LogFilter(Level.ALL.intValue(), LogFilter.ALL_LOGGERS, ""), 10, () -> false);

        assertEquals(HytaleLogReader.Status.FAILED, result.status());
        assertTrue(result.detail().toLowerCase().contains(".log"));
        assertEquals(0, result.totalMatches());
    }

    @Test
    void refusesAReadableSymlinkLogPath() throws IOException {
        Path target = tempDir.resolve("target.log");
        Files.writeString(target, "[2026/08/09 04:23:03 INFO] [NPC] target\n", StandardCharsets.UTF_8);
        Path link = tempDir.resolve("link.log");
        boolean created;
        try {
            Files.createSymbolicLink(link, target);
            created = true;
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            created = false;
        }
        Assumptions.assumeTrue(created, "symbolic links are unavailable on this platform");

        HytaleLogReader.SearchResult result = HytaleLogReader.search(link,
                new LogFilter(Level.ALL.intValue(), LogFilter.ALL_LOGGERS, "target"), 10, () -> false);

        assertEquals(HytaleLogReader.Status.FAILED, result.status());
        assertEquals(0, result.totalMatches());
    }

    @Test
    void capsServerCatalogInventoryToNewestTwoHundredFiftyFiles() throws IOException {
        Path serverRoot = tempDir.resolve("logs");
        Files.createDirectories(serverRoot);
        for (int index = 0; index < 300; index++) {
            Path file = serverRoot.resolve("2026-08-09_04-23-" + String.format("%03d", index) + "_server.log");
            Files.writeString(file, "", StandardCharsets.UTF_8);
            Files.setLastModifiedTime(file, FileTime.fromMillis(index + 1L));
        }

        LogFileCatalog catalog = new LogFileCatalog(serverRoot, null);
        List<LogFileCatalog.Option> serverOptions = catalog.refresh().stream()
                .filter(option -> option.source() != LogFileCatalog.Source.CLIENT)
                .toList();

        assertEquals(250, serverOptions.size());
        assertEquals("2026-08-09_04-23-299_server.log",
                serverOptions.get(0).path().getFileName().toString());
    }

    @Test
    void boundsOnePendingRecordAndMarksTruncation() throws IOException {
        Path log = tempDir.resolve("large_server.log");
        Files.writeString(log,
                "[2026/08/09 04:23:03 INFO] [NPC] start\n" + "x".repeat(2_000_000) + "\n",
                StandardCharsets.UTF_8);

        HytaleLogReader.SearchResult result = HytaleLogReader.search(log,
                new LogFilter(Level.ALL.intValue(), LogFilter.ALL_LOGGERS, "start"), 10, () -> false);

        String message = result.newestFirst().get(0).message();
        assertTrue(message.contains("[truncated]"));
        assertTrue(message.length() <= LogEntry.MAX_MESSAGE_CHARS
                + LogEntry.TRUNCATION_MARKER.length());
    }

    @Test
    void searchesTheSameTimestampShownByTheUi() throws IOException {
        Path log = tempDir.resolve("display-time_server.log");
        Files.writeString(log, "[2026/08/09 04:23:03 INFO] [NPC] visible time\n",
                StandardCharsets.UTF_8);
        long epochMillis = java.time.LocalDateTime.parse("2026/08/09 04:23:03",
                        java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
                .toInstant(java.time.ZoneOffset.UTC).toEpochMilli();

        HytaleLogReader.SearchResult result = HytaleLogReader.search(log,
                new LogFilter(Level.ALL.intValue(), LogFilter.ALL_LOGGERS,
                        LogEntry.displayTime(epochMillis)), 10, () -> false);

        assertEquals(1, result.totalMatches());
    }

    @Test
    void malformedConfiguredClientDirectoryDisablesOnlyThatRoot() {
        assertDoesNotThrow(() -> new LogFileCatalog("\u0000"));
    }

    @Test
    void discoversClientLogsInHytaleUserData() throws IOException {
        Path serverRoot = tempDir.resolve("server-logs");
        Files.createDirectories(serverRoot);
        Path appData = tempDir.resolve("appdata");
        Path clientLogs = appData.resolve("Hytale/UserData/Logs");
        Files.createDirectories(clientLogs);
        Path clientLog = clientLogs.resolve("2026-08-09_19-28-52_client.log");
        Files.writeString(clientLog, "client message\n", StandardCharsets.UTF_8);

        LogFileCatalog catalog = new LogFileCatalog(serverRoot, null, appData);

        List<LogFileCatalog.Option> clientOptions = catalog.refresh().stream()
                .filter(option -> option.source() == LogFileCatalog.Source.CLIENT)
                .toList();
        assertEquals(1, clientOptions.size());
        assertEquals(clientLog.toRealPath(), clientOptions.get(0).path());
    }

    @Test
    void rejectsAReadableLogOutsideTheSuppliedAllowedRoot() throws IOException {
        Path allowedRoot = tempDir.resolve("allowed");
        Files.createDirectories(allowedRoot);
        Path outside = tempDir.resolve("outside.log");
        Files.writeString(outside, "[2026/08/09 04:23:03 INFO] [NPC] outside\n",
                StandardCharsets.UTF_8);

        HytaleLogReader.SearchResult result = HytaleLogReader.search(outside, allowedRoot,
                new LogFilter(Level.ALL.intValue(), LogFilter.ALL_LOGGERS, "outside"), 10, () -> false);

        assertEquals(HytaleLogReader.Status.FAILED, result.status());
        assertTrue(result.detail().toLowerCase().contains("allowed"));
        assertEquals(0, result.totalMatches());
    }
}
