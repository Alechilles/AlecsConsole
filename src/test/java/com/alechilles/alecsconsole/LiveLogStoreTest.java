package com.alechilles.alecsconsole;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveLogStoreTest {
    @Test
    void evictsOldestRecordsAtCapacity() {
        LiveLogStore store = new LiveLogStore(3);
        store.append(record(Level.INFO, "one"));
        store.append(record(Level.WARNING, "two"));
        store.append(record(Level.SEVERE, "three"));
        store.append(record(Level.INFO, "four"));

        assertEquals(List.of("two", "three", "four"),
                store.snapshotNewest(10).stream().map(LogEntry::message).toList());
        assertEquals(3, store.size());
    }

    @Test
    void subscriberDelegatesWithoutRetainingRecords() {
        LiveLogStore store = new LiveLogStore(2);
        LogCaptureSubscriber subscriber = new LogCaptureSubscriber(store);

        assertTrue(subscriber.add(record(Level.INFO, "captured")));

        assertEquals(List.of("captured"),
                store.snapshotNewest(10).stream().map(LogEntry::message).toList());
        assertTrue(subscriber.isEmpty());
    }

    private static LogRecord record(Level level, String message) {
        return new LogRecord(level, message);
    }
}
