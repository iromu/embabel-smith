package com.embabel.smith.runtime;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the service's trace contract: every run attempt emits a trace record, and a
 * trace-write failure never changes the run's result (the success path is covered by the
 * endpoint-gated integration test).
 */
@SpringBootTest(properties = {
        "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1",
        "OPENAI_CUSTOM_API_KEY=test",
        "OPENAI_CUSTOM_MODELS=test-model",
        "embabel.models.default-llm=test-model",
        // Fail fast on action failures (ActionQos defaults to 5 attempts with backoff).
        "embabel.agent.platform.action-qos.default.max-attempts=1"
})
class SkillRunServiceTest {

    @Autowired
    private AgentPlatform platform;

    @Test
    void aSourceResolutionFailureEmitsATraceRecord(@TempDir Path tmp) throws Exception {
        JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces/runs.jsonl"));
        SkillRunService service = new SkillRunService(platform, store.file());
        String uri = "folder:" + tmp.resolve("does-not-exist");

        SkillRunService.RunResult result = service.run(uri, null, null, null, TrustTier.STANDARD);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotBlank();
        assertThat(store.read().records()).satisfiesExactly(record -> {
            assertThat(record.success()).isFalse();
            assertThat(record.pack()).isNull();
            assertThat(record.steps()).isEmpty();
            assertThat(record.source().uri()).isEqualTo(uri);
        });
    }

    @Test
    void anIngestFailureEmitsATraceRecord(@TempDir Path tmp) throws Exception {
        JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces/runs.jsonl"));
        SkillRunService service = new SkillRunService(platform, store.file());
        Path library = TestPacks.library(tmp, "dup-a", "dup-b");

        SkillRunService.RunResult result = service.run("folder:" + library, null, null, null, TrustTier.STANDARD);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("duplicate goal name");
        assertThat(store.read().records()).satisfiesExactly(record -> {
            assertThat(record.success()).isFalse();
            assertThat(record.pack()).isNull();
            assertThat(record.steps()).isEmpty();
        });
    }

    @Test
    void aTraceWriteFailureDoesNotChangeTheOutcome(@TempDir Path tmp) throws Exception {
        // A regular file where the trace's parent directory should be makes the write fail.
        Path badParent = tmp.resolve("badfile");
        Files.createFile(badParent);
        Path traceFile = badParent.resolve("runs.jsonl");
        SkillRunService service = new SkillRunService(platform, traceFile);
        String uri = "folder:" + tmp.resolve("does-not-exist");

        SkillRunService.RunResult result = service.run(uri, null, null, null, TrustTier.STANDARD);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("does not exist");
        assertThat(result.trace()).isNull();
    }
}
