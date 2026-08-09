package com.alechilles.alecsconsole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
