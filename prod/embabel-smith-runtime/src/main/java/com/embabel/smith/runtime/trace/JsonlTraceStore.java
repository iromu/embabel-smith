package com.embabel.smith.runtime.trace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The file-based trace store: one JSON object per line, one line per run, append-only.
 *
 * <p>Default location {@code ~/.smith/traces/runs.jsonl}; the directory is created on first
 * write. The writer stays dumb — no locking beyond {@code SYNC} appends (v1 is single-process
 * per host), no retention. Readers skip corrupt lines and report them as warnings.
 */
public class JsonlTraceStore {

    private final Path file;
    private final TraceRecordJson codec = new TraceRecordJson();

    public JsonlTraceStore(Path file) {
        this.file = file;
    }

    /** The default trace file: {@code ~/.smith/traces/runs.jsonl}. */
    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".smith", "traces", "runs.jsonl");
    }

    public Path file() {
        return file;
    }

    /** Appends one record as one line, creating the parent directory on first write. */
    public void append(TraceRecord record) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, codec.serialize(record) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot append trace record to " + file, e);
        }
    }

    /**
     * Reads the store in completion order. Corrupt (non-blank, unparseable) lines are skipped
     * and reported in {@link ReadResult#warnings()}.
     */
    public ReadResult read() {
        List<TraceRecord> records = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return new ReadResult(List.of(), List.of());
        }
        try (Stream<String> lines = Files.lines(file)) {
            AtomicInteger lineNumber = new AtomicInteger();
            lines.forEach(line -> {
                int n = lineNumber.incrementAndGet();
                if (line.isBlank()) {
                    return;
                }
                try {
                    records.add(codec.deserialize(line));
                } catch (IllegalArgumentException e) {
                    warnings.add("skipping corrupt line " + n + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read trace store " + file, e);
        }
        return new ReadResult(List.copyOf(records), List.copyOf(warnings));
    }

    /** The records read from the store plus the corrupt-line warnings. */
    public record ReadResult(List<TraceRecord> records, List<String> warnings) {
    }
}
