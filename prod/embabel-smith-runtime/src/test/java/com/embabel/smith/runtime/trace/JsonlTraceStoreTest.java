package com.embabel.smith.runtime.trace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonlTraceStoreTest {

    @TempDir
    Path tmp;

    private static TraceRecord record(String runId) {
        return new TraceRecord(runId, Instant.parse("2026-11-05T12:00:00Z"),
                new TraceRecord.SourceRef("folder:/tmp/packs", null, null),
                new TraceRecord.PackRef("pack", "sha256:x"),
                "goal", "input", "GOAP", "standard", List.of(), true, null, "output");
    }

    @Test
    void appendsOneLinePerRecordInOrderCreatingTheDirectory(@TempDir Path dir) throws Exception {
        JsonlTraceStore store = new JsonlTraceStore(dir.resolve("traces/runs.jsonl"));

        store.append(record("r1"));
        store.append(record("r2"));

        List<String> lines = Files.readAllLines(store.file());
        assertThat(lines).hasSize(2);
        TraceRecordJson codec = new TraceRecordJson();
        assertThat(codec.deserialize(lines.get(0)).runId()).isEqualTo("r1");
        assertThat(codec.deserialize(lines.get(1)).runId()).isEqualTo("r2");
    }

    @Test
    void readSkipsCorruptLinesAndReportsWarnings(@TempDir Path dir) throws Exception {
        JsonlTraceStore store = new JsonlTraceStore(dir.resolve("runs.jsonl"));
        store.append(record("r1"));
        Files.writeString(store.file(), "{corrupt line\n", StandardOpenOption.APPEND);
        store.append(record("r2"));

        JsonlTraceStore.ReadResult result = store.read();

        assertThat(result.records()).extracting(TraceRecord::runId).containsExactly("r1", "r2");
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("line 2");
    }

    @Test
    void readingAMissingFileYieldsNoRecordsAndNoWarnings(@TempDir Path dir) {
        JsonlTraceStore store = new JsonlTraceStore(dir.resolve("absent/runs.jsonl"));

        JsonlTraceStore.ReadResult result = store.read();

        assertThat(result.records()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
}
