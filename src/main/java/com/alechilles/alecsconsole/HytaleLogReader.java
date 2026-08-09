package com.alechilles.alecsconsole;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

/** Streaming parser for Hytale's UTF-8 server log format. */
public final class HytaleLogReader {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final Pattern PREFIX = Pattern.compile(
            "^\\[(?<timestamp>\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})\\s+"
                    + "(?<level>[^]]+)]\\s+\\[(?<logger>[^]]*)]\\s*(?<message>.*)$");

    private HytaleLogReader() {
    }

    public enum Status {
        COMPLETE,
        CANCELED,
        FAILED
    }

    public record SearchResult(
            List<LogEntry> newestFirst,
            long totalMatches,
            boolean capped,
            Status status,
            String detail) {
        public SearchResult {
            newestFirst = newestFirst == null ? List.of() : List.copyOf(newestFirst);
            detail = detail == null ? "" : detail;
        }
    }

    public static SearchResult search(
            Path path,
            LogFilter filter,
            int resultLimit,
            BooleanSupplier cancelled) {
        Objects.requireNonNull(filter, "filter");
        int retentionLimit = Math.max(0, resultLimit);
        ArrayDeque<LogEntry> newestMatches = new ArrayDeque<>(Math.max(1, retentionLimit));
        long[] sequence = {0};
        long[] totalMatches = {0};
        ParsedRecord[] pending = {null};

        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled(cancelled)) {
                    return canceledResult();
                }

                Matcher matcher = PREFIX.matcher(line);
                if (matcher.matches()) {
                    if (pending[0] != null) {
                        appendMatch(pending[0], filter, retentionLimit, newestMatches,
                                sequence, totalMatches);
                    }
                    pending[0] = parse(matcher);
                } else if (pending[0] != null) {
                    pending[0].message.append('\n').append(line);
                }
            }

            if (isCancelled(cancelled)) {
                return canceledResult();
            }
            if (pending[0] != null) {
                appendMatch(pending[0], filter, retentionLimit, newestMatches,
                        sequence, totalMatches);
            }
        } catch (IOException | RuntimeException exception) {
            if (isCancelled(cancelled) || Thread.currentThread().isInterrupted()) {
                return canceledResult();
            }
            String detail = exception.getMessage();
            return new SearchResult(List.of(), 0, false, Status.FAILED,
                    detail == null || detail.isBlank() ? exception.getClass().getSimpleName() : detail);
        }

        ArrayList<LogEntry> newestFirst = new ArrayList<>(newestMatches);
        Collections.reverse(newestFirst);
        return new SearchResult(newestFirst, totalMatches[0],
                totalMatches[0] > newestFirst.size(), Status.COMPLETE, "");
    }

    private static ParsedRecord parse(Matcher matcher) {
        String timestamp = matcher.group("timestamp");
        long epochMillis;
        try {
            epochMillis = LocalDateTime.parse(timestamp, TIMESTAMP_FORMAT)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException exception) {
            return new ParsedRecord(0, "", "", new StringBuilder(matcher.group("message")));
        }
        return new ParsedRecord(epochMillis, matcher.group("level").trim(),
                matcher.group("logger"), new StringBuilder(matcher.group("message")));
    }

    private static void appendMatch(
            ParsedRecord parsed,
            LogFilter filter,
            int retentionLimit,
            ArrayDeque<LogEntry> newestMatches,
            long[] sequence,
            long[] totalMatches) {
        long nextSequence = ++sequence[0];
        String levelName = parsed.levelName;
        int levelValue = levelValue(levelName);
        String loggerName = parsed.loggerName;
        String message = parsed.message.toString();
        String searchText = (parsed.epochMillis + " " + levelName + " " + loggerName + " " + message)
                .toLowerCase(Locale.ROOT);
        LogEntry entry = new LogEntry(nextSequence, parsed.epochMillis, levelValue,
                levelName, loggerName, message, searchText);
        if (!filter.matches(entry)) {
            return;
        }

        totalMatches[0]++;
        if (retentionLimit == 0) {
            return;
        }
        if (newestMatches.size() == retentionLimit) {
            newestMatches.removeFirst();
        }
        newestMatches.addLast(entry);
    }

    private static int levelValue(String levelName) {
        if ("WARN".equalsIgnoreCase(levelName) || "WARNING".equalsIgnoreCase(levelName)) {
            return Level.WARNING.intValue();
        }
        try {
            return Level.parse(levelName).intValue();
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        return Thread.currentThread().isInterrupted()
                || cancelled != null && cancelled.getAsBoolean();
    }

    private static SearchResult canceledResult() {
        return new SearchResult(List.of(), 0, false, Status.CANCELED, "Search canceled");
    }

    private static final class ParsedRecord {
        private final long epochMillis;
        private final String levelName;
        private final String loggerName;
        private final StringBuilder message;

        private ParsedRecord(long epochMillis, String levelName, String loggerName, StringBuilder message) {
            this.epochMillis = epochMillis;
            this.levelName = levelName;
            this.loggerName = loggerName;
            this.message = message;
        }
    }
}
