package com.alechilles.alecsconsole;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Adapter accepted by Hytale's logger backend without retaining records in
 * the inherited subscriber collection.
 */
public final class LogCaptureSubscriber extends CopyOnWriteArrayList<LogRecord> {
    private final LiveLogStore store;

    public LogCaptureSubscriber(LiveLogStore store) {
        this.store = store;
        // Hytale tracks subscriber lists with equality-based contains/remove.
        // One unique, never-published token keeps this adapter distinct from
        // other empty subscribers without retaining any captured records.
        super.add(new LogRecord(Level.OFF, "Alec's Console subscriber identity"));
    }

    @Override
    public boolean add(LogRecord record) {
        store.append(record);
        return true;
    }
}
