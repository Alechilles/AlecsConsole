package com.alechilles.alecsconsole;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Immutable, normalized representation of one captured log record.
 */
public record LogEntry(
        long sequence,
        long epochMillis,
        int levelValue,
        String levelName,
        String loggerName,
        String message,
        String searchText) {

    static LogEntry fromRecord(long sequence, LogRecord record) {
        Objects.requireNonNull(record, "record");

        Level level = record.getLevel();
        String levelName = level == null ? "" : level.getName();
        int levelValue = level == null ? 0 : level.intValue();
        String loggerName = Objects.requireNonNullElse(record.getLoggerName(), "");
        String message = Objects.requireNonNullElse(record.getMessage(), "");
        long epochMillis = record.getMillis();
        String searchText = (epochMillis + " " + levelName + " " + loggerName + " " + message)
                .toLowerCase(Locale.ROOT);

        return new LogEntry(sequence, epochMillis, levelValue, levelName,
                loggerName, message, searchText);
    }
}
