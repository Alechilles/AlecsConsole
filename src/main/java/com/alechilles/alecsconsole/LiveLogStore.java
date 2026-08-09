package com.alechilles.alecsconsole;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogRecord;

/**
 * Synchronized fixed-capacity ring for records captured during this session.
 */
public final class LiveLogStore {
    private final int capacity;
    private final ArrayDeque<LogEntry> entries;
    private long sequence;

    public LiveLogStore(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new ArrayDeque<>(capacity);
    }

    public synchronized LogEntry append(LogRecord record) {
        long nextSequence = sequence + 1;
        LogEntry entry = LogEntry.fromRecord(nextSequence, record);
        if (entries.size() == capacity) {
            entries.removeFirst();
        }
        entries.addLast(entry);
        sequence = nextSequence;
        return entry;
    }

    public synchronized List<LogEntry> snapshotNewest(int limit) {
        int count = Math.min(Math.max(limit, 0), entries.size());
        if (count == 0) {
            return List.of();
        }

        ArrayList<LogEntry> snapshot = new ArrayList<>(count);
        int skip = entries.size() - count;
        int index = 0;
        for (LogEntry entry : entries) {
            if (index++ >= skip) {
                snapshot.add(entry);
            }
        }
        return List.copyOf(snapshot);
    }

    public synchronized List<LogEntry> snapshotAfter(long sequence, int limit) {
        int count = Math.max(limit, 0);
        if (count == 0 || entries.isEmpty()) {
            return List.of();
        }

        ArrayList<LogEntry> snapshot = new ArrayList<>(Math.min(count, entries.size()));
        for (LogEntry entry : entries) {
            if (entry.sequence() > sequence) {
                snapshot.add(entry);
                if (snapshot.size() == count) {
                    break;
                }
            }
        }
        return List.copyOf(snapshot);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long latestSequence() {
        return sequence;
    }
}
