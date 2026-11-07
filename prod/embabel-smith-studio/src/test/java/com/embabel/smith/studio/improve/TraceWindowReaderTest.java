package com.embabel.smith.studio.improve;

import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceWindowReaderTest {

    @TempDir
    Path tmp;

    @Test
    void windowBoundsInputAtN() {
        JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces.jsonl"));
        for (int i = 0; i < 10; i++) {
            store.append(TestFixtures.okTrace("r" + i, "extract", "in",
                    TestFixtures.step("s1", "completed", "ok")));
        }

        TraceWindowReader reader = new TraceWindowReader(store, 3);
        List<TraceRecord> window = reader.window("extract");

        assertThat(window).hasSize(3); // bounded to the most recent 3
        assertThat(window.get(0).runId()).isEqualTo("r7");
        assertThat(window.get(2).runId()).isEqualTo("r9");
    }

    @Test
    void windowIsPerSkill() {
        JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces.jsonl"));
        store.append(TestFixtures.okTrace("r1", "extract", "in", TestFixtures.step("s1", "completed", "ok")));
        store.append(TestFixtures.okTrace("r2", "summarize", "in", TestFixtures.step("s1", "completed", "ok")));
        store.append(TestFixtures.okTrace("r3", "extract", "in", TestFixtures.step("s1", "completed", "ok")));

        TraceWindowReader reader = new TraceWindowReader(store);

        assertThat(reader.window("extract").stream().map(TraceRecord::runId).toList())
                .containsExactly("r1", "r3");
        assertThat(reader.window("summarize").stream().map(TraceRecord::runId).toList())
                .containsExactly("r2");
        assertThat(reader.skills()).containsExactly("extract", "summarize");
    }
}
