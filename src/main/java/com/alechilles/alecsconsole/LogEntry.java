package com.alechilles.alecsconsole;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    static final int MAX_MESSAGE_CHARS = 20_000;
    static final String TRUNCATION_MARKER = "\n[truncated]";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    static LogEntry fromRecord(long sequence, LogRecord record) {
        Objects.requireNonNull(record, "record");

        Level level = record.getLevel();
        String levelName = level == null ? "" : level.getName();
        int levelValue = level == null ? 0 : level.intValue();
        String loggerName = Objects.requireNonNullElse(record.getLoggerName(), "");
        return create(sequence, record.getMillis(), levelValue, levelName, loggerName,
                record.getMessage(), "");
    }

    static LogEntry create(long sequence, long epochMillis, int levelValue, String levelName,
                           String loggerName, String message, String extraSearchText) {
        String normalizedLevel = Objects.requireNonNullElse(levelName, "");
        String normalizedLogger = Objects.requireNonNullElse(loggerName, "");
        String boundedMessage = boundedMessage(message);
        String searchText = (displayTime(epochMillis) + " " + epochMillis + " "
                + Objects.requireNonNullElse(extraSearchText, "") + " " + normalizedLevel + " "
                + normalizedLogger + " " + boundedMessage).toLowerCase(Locale.ROOT);
        return new LogEntry(sequence, epochMillis, levelValue, normalizedLevel,
                normalizedLogger, boundedMessage, searchText);
    }

    static String displayTime(long epochMillis) {
        return epochMillis <= 0 ? "--:--:--" : TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String boundedMessage(String message) {
        String value = Objects.requireNonNullElse(message, "");
        if (value.length() <= MAX_MESSAGE_CHARS) {
            return value;
        }
        return value.substring(0, MAX_MESSAGE_CHARS) + TRUNCATION_MARKER;
    }
}
