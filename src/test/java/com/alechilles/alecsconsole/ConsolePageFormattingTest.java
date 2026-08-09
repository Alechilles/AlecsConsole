package com.alechilles.alecsconsole;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsolePageFormattingTest {
    @Test
    void selectedLargeRecordKeepsAUsefulPrefixBeforeTheTruncationMarker() {
        LogEntry entry = new LogEntry(1, 1_786_250_582_000L, Level.INFO.intValue(),
                "INFO", "NPC", "useful prefix " + "x".repeat(30_000), "");

        String selected = ConsolePage.selectedText(List.of(entry), false);

        assertTrue(selected.contains("useful prefix"));
        assertTrue(selected.endsWith("… (selection truncated)"));
        assertTrue(selected.length() <= 20_000);
    }

    @Test
    void rowPreviewDoesNotSendAnEntireLargeMessage() {
        assertTrue(ConsolePage.firstLine("x".repeat(30_000)).length() < 2_000);
    }
}
