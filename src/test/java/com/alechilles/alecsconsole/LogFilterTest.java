package com.alechilles.alecsconsole;

import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFilterTest {
    @Test
    void combinesMinimumLevelLoggerAndText() {
        LogEntry warning = entry(Level.WARNING, "NPC", "Dragon path failed");
        LogEntry info = entry(Level.INFO, "NPC", "Dragon spawned");
        LogEntry other = entry(Level.SEVERE, "World", "Dragon path failed");
        LogEntry sameLevelDifferentText = entry(Level.WARNING, "NPC", "Dragon spawned");
        LogFilter filter = new LogFilter(Level.WARNING.intValue(), "NPC", "PATH FAILED");

        assertTrue(filter.matches(warning));
        assertFalse(filter.matches(info));
        assertFalse(filter.matches(other));
        assertFalse(filter.matches(sameLevelDifferentText));
    }

    @Test
    void emptyTextAndAllLoggersAcceptRecordsAtOrAboveThreshold() {
        LogEntry warning = entry(Level.WARNING, "NPC", "Dragon path failed");
        LogEntry severe = entry(Level.SEVERE, "World", "Server stopped");
        LogEntry info = entry(Level.INFO, "NPC", "Dragon spawned");
        LogFilter filter = new LogFilter(Level.WARNING.intValue(), LogFilter.ALL_LOGGERS, "");

        assertTrue(filter.matches(warning));
        assertTrue(filter.matches(severe));
        assertFalse(filter.matches(info));
    }

    @Test
    void preservesWhitespaceInExactLoggerSelection() {
        LogEntry spaced = entry(Level.WARNING, " NPC ", "Dragon path failed");
        LogEntry unspaced = entry(Level.WARNING, "NPC", "Dragon path failed");
        LogFilter filter = new LogFilter(Level.WARNING.intValue(), " NPC ", "");

        assertTrue(filter.matches(spaced));
        assertFalse(filter.matches(unspaced));
    }

    @Test
    void unnamedLoggerSelectionDoesNotMatchNamedEntries() {
        LogEntry unnamed = entry(Level.WARNING, "", "Dragon path failed");
        LogEntry named = entry(Level.WARNING, "NPC", "Dragon path failed");
        LogFilter filter = new LogFilter(Level.WARNING.intValue(), "", "");

        assertTrue(filter.matches(unnamed));
        assertFalse(filter.matches(named));
    }

    private static LogEntry entry(Level level, String loggerName, String message) {
        LogRecord record = new LogRecord(level, message);
        record.setLoggerName(loggerName);
        return LogEntry.fromRecord(1, record);
    }
}
