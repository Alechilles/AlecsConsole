package com.alechilles.alecsconsole;

import java.util.Locale;

/**
 * Pure, normalized criteria for selecting captured log entries.
 */
public record LogFilter(int minimumLevel, String loggerName, String text) {
    public static final String ALL_LOGGERS = "ALL";

    public LogFilter {
        loggerName = normalizeLogger(loggerName);
        text = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    public boolean matches(LogEntry entry) {
        return entry.levelValue() >= minimumLevel
                && (ALL_LOGGERS.equals(loggerName) || loggerName.equals(entry.loggerName()))
                && (text.isEmpty() || entry.searchText().contains(text));
    }

    private static String normalizeLogger(String loggerName) {
        return loggerName == null ? "" : loggerName;
    }
}
