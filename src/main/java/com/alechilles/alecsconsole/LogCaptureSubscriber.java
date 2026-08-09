package com.alechilles.alecsconsole;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.LogRecord;

/**
 * Adapter accepted by Hytale's logger backend without retaining records in
 * the inherited subscriber collection.
 */
public final class LogCaptureSubscriber extends CopyOnWriteArrayList<LogRecord> {
    private final LiveLogStore store;

    public LogCaptureSubscriber(LiveLogStore store) {
        this.store = store;
    }

    @Override
    public boolean add(LogRecord record) {
        store.append(record);
        return true;
    }
}
