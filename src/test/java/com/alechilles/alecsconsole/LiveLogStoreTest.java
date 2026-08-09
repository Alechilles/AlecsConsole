package com.alechilles.alecsconsole;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        LogRecord captured = record(Level.INFO, "captured");
        int baselineSize = subscriber.size();

        assertTrue(subscriber.add(captured));

        assertEquals(List.of("captured"),
                store.snapshotNewest(10).stream().map(LogEntry::message).toList());
        assertEquals(baselineSize, subscriber.size());
        assertFalse(subscriber.contains(captured));
    }

    @Test
    void subscriberCoexistsWithAnotherEmptyBackendSubscriber() {
        CopyOnWriteArrayList<CopyOnWriteArrayList<LogRecord>> backendSubscribers =
                new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<LogRecord> other = new CopyOnWriteArrayList<>();
        LogCaptureSubscriber capture = new LogCaptureSubscriber(new LiveLogStore(2));

        backendSubscribers.add(other);
        if (!backendSubscribers.contains(capture)) {
            backendSubscribers.add(capture);
        }
        backendSubscribers.remove(capture);

        assertEquals(List.of(other), backendSubscribers);
    }

    private static LogRecord record(Level level, String message) {
        return new LogRecord(level, message);
    }
}
