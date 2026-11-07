package com.embabel.smith.studio.improve;

import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;

import java.util.List;

/**
 * Reads a rolling window of the most recent traces per skill from the JSONL trace store (change 03).
 * The store is a single append-only file across all skills; this reader filters it by the pack that
 * exported each run's goal and keeps only the most recent {@code windowSize} per skill. Corrupt lines
 * are already skipped (and reported) by the store, so the window is built from well-formed records
 * only.
 */
public class TraceWindowReader {

    /** The default rolling window size (design D3). */
    public static final int DEFAULT_WINDOW = 200;

    private final JsonlTraceStore store;
    private final int windowSize;

    public TraceWindowReader(JsonlTraceStore store) {
        this(store, DEFAULT_WINDOW);
    }

    public TraceWindowReader(JsonlTraceStore store, int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("window size must be positive: " + windowSize);
        }
        this.store = store;
        this.windowSize = windowSize;
    }

    /**
     * The most recent {@code windowSize} traces for the named skill, in completion order. A skill
     * with no traces yields an empty list.
     */
    public List<TraceRecord> window(String skill) {
        List<TraceRecord> all = store.read().records().stream()
                .filter(record -> skill.equals(packName(record)))
                .toList();
        int from = Math.max(0, all.size() - windowSize);
        return List.copyOf(all.subList(from, all.size()));
    }

    /** The distinct skill names that have at least one trace in the store. */
    public List<String> skills() {
        return store.read().records().stream()
                .map(TraceWindowReader::packName)
                .filter(name -> name != null)
                .distinct()
                .sorted()
                .toList();
    }

    private static String packName(TraceRecord record) {
        return record.pack() == null ? null : record.pack().name();
    }
}
