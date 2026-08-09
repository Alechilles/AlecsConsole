package com.alechilles.alecsconsole;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    private static final int MAX_PENDING_TEXT_CHARS = 200_000;
    private static final String TRUNCATION_MARKER = "\n[truncated]";
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
        Path normalized = path == null ? null : path.toAbsolutePath().normalize();
        Path parent = normalized == null ? null : normalized.getParent();
        return search(path, parent, filter, resultLimit, cancelled);
    }

    public static SearchResult search(
            Path path,
            Path allowedRoot,
            LogFilter filter,
            int resultLimit,
            BooleanSupplier cancelled) {
        Objects.requireNonNull(filter, "filter");
        int retentionLimit = Math.max(0, resultLimit);
        ArrayDeque<LogEntry> newestMatches = new ArrayDeque<>(Math.max(1, retentionLimit));
        long[] sequence = {0};
        long[] totalMatches = {0};
        ParsedRecord[] pending = {null};

        try (var reader = openLog(path, allowedRoot)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled(cancelled)) {
                    return canceledResult();
                }

                Matcher matcher = PREFIX.matcher(line);
                if (matcher.matches()) {
                    ParsedRecord parsed = parse(matcher);
                    if (parsed != null) {
                        if (pending[0] != null) {
                            appendMatch(pending[0], filter, retentionLimit, newestMatches,
                                    sequence, totalMatches);
                        }
                        pending[0] = parsed;
                    } else {
                        appendRawLine(pending, line);
                    }
                } else if (pending[0] != null) {
                    pending[0].appendLine(line);
                } else {
                    appendRawLine(pending, line);
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
            return null;
        }
        String levelName = matcher.group("level").trim();
        String loggerName = matcher.group("logger");
        String prefixText = timestamp + " " + levelName + " [" + loggerName + "]";
        return new ParsedRecord(epochMillis, levelName, loggerName, prefixText,
                matcher.group("message"));
    }

    private static BufferedReader openLog(Path path, Path allowedRoot) throws IOException {
        if (allowedRoot == null) {
            throw new IOException("No allowed log root was supplied");
        }
        Path canonicalRoot = allowedRoot.toRealPath();
        Path canonical = path.toRealPath();
        if (!canonical.startsWith(canonicalRoot)) {
            throw new IOException("Log path is outside the allowed root");
        }
        String fileName = canonical.getFileName() == null
                ? ""
                : canonical.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".log")) {
            throw new IOException("Only .log files can be searched");
        }
        return new BufferedReader(new InputStreamReader(
                Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
                StandardCharsets.UTF_8));
    }

    private static void appendRawLine(ParsedRecord[] pending, String line) {
        if (pending[0] == null) {
            pending[0] = new ParsedRecord(0, "", "", "", line);
        } else {
            pending[0].appendLine(line);
        }
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
        String searchText = (parsed.prefixText + " " + parsed.epochMillis + " " + levelName + " "
                + loggerName + " " + message)
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
        private final String prefixText;
        private final StringBuilder message = new StringBuilder();
        private boolean truncated;

        private ParsedRecord(long epochMillis, String levelName, String loggerName,
                             String prefixText, String message) {
            this.epochMillis = epochMillis;
            this.levelName = levelName;
            this.loggerName = loggerName;
            this.prefixText = prefixText;
            appendText(message);
        }

        private void appendLine(String line) {
            appendText("\n");
            appendText(line);
        }

        private void appendText(String text) {
            if (truncated || text == null || text.isEmpty()) {
                return;
            }
            int remaining = MAX_PENDING_TEXT_CHARS - message.length();
            if (text.length() <= remaining) {
                message.append(text);
                return;
            }
            if (remaining > 0) {
                message.append(text, 0, remaining);
            }
            message.append(TRUNCATION_MARKER);
            truncated = true;
        }
    }
}
