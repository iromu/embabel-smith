package com.embabel.smith.runtime.trace;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.TestPacks;
import com.embabel.smith.runtime.core.IngestedSource;
import com.embabel.smith.runtime.core.RunOutcome;
import com.embabel.smith.runtime.core.RuntimeCore;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.specsource.ResolvedSpecSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the recorder's failure paths (the success path with per-step capture
 * is covered by the endpoint-gated integration test).
 */
@SpringBootTest(properties = { "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=test",
		"OPENAI_CUSTOM_MODELS=test-model", "embabel.models.default-llm=test-model",
		// Fail fast on action failures (ActionQos defaults to 5 attempts with backoff).
		"embabel.agent.platform.action-qos.default.max-attempts=1" })
class TraceRecorderTest {

	@Autowired
	private AgentPlatform platform;

	@Test
	void recordsAFailedRunWithUnmetPreconditions(@TempDir Path tmp) throws Exception {
		JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces/runs.jsonl"));
		TraceRecorder recorder = new TraceRecorder(store);
		RuntimeCore core = new RuntimeCore(platform);
		ResolvedSpecSource resolved = TestPacks.resolve(TestPacks.library(tmp, "unreachable"));
		IngestedSource ingested = core.ingest(resolved);
		RunOutcome outcome = core.run(ingested, "ghostDone", "any input", null, TrustTier.STANDARD);

		TraceRecord record = recorder.record(ingested, "ghostDone", "any input", TrustTier.STANDARD, outcome);

		assertThat(record.success()).isFalse();
		assertThat(record.error()).contains("unmet precondition");
		assertThat(record.outputExcerpt()).isNull();
		assertThat(record.steps()).isEmpty();
		assertThat(record.goal()).isEqualTo("ghostDone");
		assertThat(record.input()).isEqualTo("any input");
		assertThat(record.planner()).isEqualTo("GOAP");
		assertThat(record.trustTier()).isEqualTo("standard");
		assertThat(record.pack().name()).isEqualTo("unreachable");
		assertThat(record.source().uri()).startsWith("folder:");
		assertThat(record.source().ref()).isNull();
		assertThat(record.runId()).isNotBlank();
		assertThat(store.read().records()).containsExactly(record);
	}

	@Test
	void recordsATierBlockedRunWithoutAProcess(@TempDir Path tmp) throws Exception {
		JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces/runs.jsonl"));
		TraceRecorder recorder = new TraceRecorder(store);
		RuntimeCore core = new RuntimeCore(platform);
		IngestedSource ingested = core.ingest(TestPacks.resolve(TestPacks.library(tmp, "shell")));

		// The service converts the tier-block exception into a failed outcome before
		// recording.
		RunOutcome outcome = RunOutcome.failed("Run blocked by trust tier: pack 'shell' declares tool group(s) [shell] "
				+ "that require a higher trust tier (granted: standard)");
		TraceRecord record = recorder.record(ingested, "cliDone", "ls", TrustTier.STANDARD, outcome);

		assertThat(record.success()).isFalse();
		assertThat(record.error()).contains("blocked by trust tier");
		assertThat(record.steps()).isEmpty();
		assertThat(record.planner()).isNull();
		assertThat(record.pack().name()).isEqualTo("shell");
		assertThat(store.read().records()).containsExactly(record);
	}

	@Test
	void recordsAGoalNotFoundRunWithoutAPack(@TempDir Path tmp) throws Exception {
		JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces/runs.jsonl"));
		TraceRecorder recorder = new TraceRecorder(store);
		RuntimeCore core = new RuntimeCore(platform);
		IngestedSource ingested = core.ingest(TestPacks.resolve(TestPacks.library(tmp, "extract")));

		TraceRecord record = recorder.record(ingested, "nope", "input", TrustTier.STANDARD,
				RunOutcome.failed("No goal named 'nope' is exported by the source. Available goals: [briefDone]"));

		assertThat(record.success()).isFalse();
		assertThat(record.goal()).isEqualTo("nope");
		assertThat(record.pack()).isNull();
		assertThat(record.steps()).isEmpty();
	}

	@Test
	void excerptsAreTruncatedToTheConfiguredLimit() {
		assertThat(TraceRecorder.truncate("short", 500)).isEqualTo("short");
		assertThat(TraceRecorder.truncate(null, 500)).isNull();
		String longValue = "a".repeat(TraceRecorder.DEFAULT_EXCERPT_LIMIT + 100);
		assertThat(TraceRecorder.truncate(longValue, TraceRecorder.DEFAULT_EXCERPT_LIMIT))
			.hasSize(TraceRecorder.DEFAULT_EXCERPT_LIMIT);
	}

}
