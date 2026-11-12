package com.embabel.smith.studio.improve;

import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;

import java.util.List;

/**
 * Reads a rolling window of the most recent traces per spec from the JSONL trace store (change 03).
 * The store is a single append-only file across all specs; this reader filters it by the pack that
 * exported each run's goal and keeps only the most recent {@code windowSize} per spec. Corrupt lines
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
     * The most recent {@code windowSize} traces for the named spec, in completion order. A spec
     * with no traces yields an empty list.
     */
    public List<TraceRecord> window(String spec) {
        List<TraceRecord> all = store.read().records().stream()
                .filter(record -> spec.equals(packName(record)))
                .toList();
        int from = Math.max(0, all.size() - windowSize);
        return List.copyOf(all.subList(from, all.size()));
    }

    /** The distinct spec names that have at least one trace in the store. */
    public List<String> specs() {
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
