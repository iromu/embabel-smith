package com.embabel.smith.runtime;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.runtime.trace.TraceRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoint-gated integration tests: live runs end-to-end (source → ingest → run → trace).
 * The first runs the single-pack {@code briefDone} goal over the {@code extract} test pack;
 * the second runs the cross-pack {@code explainDone} goal over the {@code extract} +
 * {@code explain} packs, whose plan spans both packs. Skipped unless
 * {@code OPENAI_CUSTOM_BASE_URL} is set (the model endpoint is fully env-driven).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class LiveRunIntegrationTest {

    @Autowired
    private AgentPlatform platform;

    @Test
    void aLiveRunEmitsATraceRecordWithPerStepCapture(@TempDir Path tmp) throws Exception {
        Path library = TestPacks.library(tmp, "extract");
        SkillRunService service = new SkillRunService(platform, tmp.resolve("traces/runs.jsonl"));

        SkillRunService.RunResult result = service.run("folder:" + library, "briefDone",
                "Explain how a coffee machine works", null, TrustTier.STANDARD);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isNotBlank();
        assertThat(result.error()).isNull();

        JsonlTraceStore.ReadResult store = new JsonlTraceStore(tmp.resolve("traces/runs.jsonl")).read();
        assertThat(store.warnings()).isEmpty();
        assertThat(store.records()).hasSize(1);

        TraceRecord record = store.records().get(0);
        assertThat(record.success()).isTrue();
        assertThat(record.goal()).isEqualTo("briefDone");
        assertThat(record.planner()).isEqualTo("GOAP");
        assertThat(record.trustTier()).isEqualTo("standard");
        assertThat(record.pack().name()).isEqualTo("extract");
        assertThat(record.pack().contentHash()).matches("[0-9a-f]{64}");
        assertThat(record.outputExcerpt()).isNotBlank();
        assertThat(record.outputExcerpt()).hasSizeLessThanOrEqualTo(TraceRecorder.DEFAULT_EXCERPT_LIMIT);
        assertThat(record.steps()).hasSize(1);
        assertThat(record.steps().get(0).name()).isEqualTo("extractTopic");
        assertThat(record.steps().get(0).status()).isEqualTo("completed");
        assertThat(record.steps().get(0).durationMs()).isPositive();
        assertThat(record.steps().get(0).outputExcerpt()).contains("topic");
    }

    @Test
    void aCrossPackPlanSpansBothPacksInOrder(@TempDir Path tmp) throws Exception {
        Path library = TestPacks.library(tmp, "extract", "explain");
        SkillRunService service = new SkillRunService(platform, tmp.resolve("traces/runs.jsonl"));

        SkillRunService.RunResult result = service.run("folder:" + library, "explainDone",
                "Explain how a coffee machine works", null, TrustTier.STANDARD);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isNotBlank();
        assertThat(result.error()).isNull();

        JsonlTraceStore.ReadResult store = new JsonlTraceStore(tmp.resolve("traces/runs.jsonl")).read();
        assertThat(store.warnings()).isEmpty();
        assertThat(store.records()).hasSize(1);

        TraceRecord record = store.records().get(0);
        assertThat(record.success()).isTrue();
        assertThat(record.goal()).isEqualTo("explainDone");
        assertThat(record.steps()).hasSize(2);
        assertThat(record.steps().get(0).name()).isEqualTo("extractTopic");
        assertThat(record.steps().get(0).status()).isEqualTo("completed");
        assertThat(record.steps().get(1).name()).isEqualTo("writeExplainer");
        assertThat(record.steps().get(1).status()).isEqualTo("completed");
    }
}
