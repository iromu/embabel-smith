package com.embabel.smith.studio.rest;

import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.studio.rest.dto.TraceInfo;
import com.embabel.smith.studio.storage.StorageContext;
import com.embabel.smith.studio.storage.StorageSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the Traces surface: per-spec window, most-recent-first, limit, step
 * drill-down.
 */
class TraceControllerTest {

	@Test
	void listsTracesMostRecentFirst(@TempDir Path tmp) {
		JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("runs.jsonl"));
		store.append(trace("r1", "summarizer", false));
		store.append(trace("r2", "summarizer", true));
		store.append(trace("r3", "other", true));

		TraceController controller = controller(store, tmp);
		List<TraceInfo> traces = controller.traces("summarizer", 100);
		assertThat(traces).hasSize(2);
		assertThat(traces.get(0).runId()).isEqualTo("r2");
		assertThat(traces.get(1).runId()).isEqualTo("r1");
		assertThat(traces.get(0).success()).isTrue();
		assertThat(traces.get(1).success()).isFalse();
	}

	@Test
	void filtersBySpec(@TempDir Path tmp) {
		JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("runs.jsonl"));
		store.append(trace("r1", "summarizer", true));
		store.append(trace("r2", "other", true));

		TraceController controller = controller(store, tmp);
		assertThat(controller.traces("summarizer", 100)).hasSize(1);
		assertThat(controller.traces("other", 100)).hasSize(1);
		assertThat(controller.traces("none", 100)).isEmpty();
	}

	@Test
	void boundsByLimit(@TempDir Path tmp) {
		JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("runs.jsonl"));
		for (int i = 0; i < 5; i++) {
			store.append(trace("r" + i, "summarizer", true));
		}
		TraceController controller = controller(store, tmp);
		assertThat(controller.traces("summarizer", 2)).hasSize(2);
	}

	@Test
	void carriesStepsAndFailureReasonForDrillDown(@TempDir Path tmp) {
		JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("runs.jsonl"));
		store.append(traceWithSteps("r1", "summarizer", "step failed"));

		TraceController controller = controller(store, tmp);
		TraceInfo info = controller.traces("summarizer", 100).get(0);
		assertThat(info.steps()).hasSize(1);
		assertThat(info.steps().get(0).name()).isEqualTo("summarize");
		assertThat(info.steps().get(0).status()).isEqualTo("failed");
		assertThat(info.error()).isEqualTo("step failed");
	}

	/**
	 * A controller whose trace backend is the given JSONL store (an unbound storage
	 * context).
	 */
	private static TraceController controller(JsonlTraceStore store, Path tmp) {
		StorageContext ctx = new StorageContext(new StorageSettingsService(tmp), store, null, null, null);
		return new TraceController(ctx);
	}

	private static TraceRecord trace(String runId, String spec, boolean success) {
		return new TraceRecord(runId, Instant.now(), new TraceRecord.SourceRef("folder:/tmp", null, null),
				new TraceRecord.PackRef(spec, "hash"), spec, "input", "GOAP", "STANDARD", List.of(), success,
				success ? null : "failed", success ? "ok" : null);
	}

	private static TraceRecord traceWithSteps(String runId, String spec, String error) {
		return new TraceRecord(runId, Instant.now(), new TraceRecord.SourceRef("folder:/tmp", null, null),
				new TraceRecord.PackRef(spec, "hash"), spec, "input", "GOAP", "STANDARD",
				List.of(new TraceRecord.StepRecord("summarize", "failed", 12, "no output")), false, error, null);
	}

}
