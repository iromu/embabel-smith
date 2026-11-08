package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.eval.RefinementOutcome;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.rest.dto.EvalRequest;
import com.embabel.smith.studio.rest.dto.EvalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline tests for the Eval pane: the endpoint gate, delegation to the seam, and the source read. */
class EvalControllerTest {

    @Test
    void returnsStructuredErrorWhenNoEndpoint() {
        EvalController controller = new EvalController(
                (pack, tasks) -> {
                    throw new AssertionError("must not evaluate without an endpoint");
                },
                new SourceService("", ""),
                new ModelEndpointConfig("", "", "", ""));
        EvalResponse response = controller.eval(new EvalRequest("folder:/tmp", "skill", null));
        assertThat(response.errorCode()).isEqualTo("MODEL_NOT_CONFIGURED");
        assertThat(response.error()).contains("No resolvable model endpoint");
    }

    @Test
    void returnsErrorWhenNoSourceOrSkill() {
        EvalController controller = new EvalController(
                (pack, tasks) -> {
                    throw new AssertionError("must not evaluate without a source/skill");
                },
                new SourceService("", ""),
                configured());
        EvalResponse response = controller.eval(new EvalRequest("", "", null));
        assertThat(response.error()).contains("No source or skill");
    }

    @Test
    void evaluatesPackFromSourceWhenConfigured(@TempDir Path tmp) {
        RestTestFixtures.writePack(tmp, RestTestFixtures.pack("summarizer", "summarize"));
        SourceService sourceService = new SourceService("folder:" + tmp, "");
        EvalController controller = new EvalController(
                (pack, tasks) -> new RefinementOutcome(pack, 0.5, 1, List.of("task1"), false),
                sourceService,
                configured());
        EvalResponse response = controller.eval(new EvalRequest("folder:" + tmp, "summarizer", null));
        assertThat(response.skill()).isEqualTo("summarizer");
        assertThat(response.score()).isEqualTo(0.5);
        assertThat(response.refinementCount()).isEqualTo(1);
        assertThat(response.failingTasks()).contains("task1");
        assertThat(response.passed()).isFalse();
    }

    @Test
    void returnsErrorForUnknownSkill(@TempDir Path tmp) {
        SourceService sourceService = new SourceService("folder:" + tmp, "");
        EvalController controller = new EvalController(
                (pack, tasks) -> {
                    throw new AssertionError("must not evaluate an unknown skill");
                },
                sourceService,
                configured());
        EvalResponse response = controller.eval(new EvalRequest("folder:" + tmp, "nope", null));
        assertThat(response.error()).contains("not found");
    }

    private static ModelEndpointConfig configured() {
        return new ModelEndpointConfig("http://localhost:4000", "key", "gpt", "gpt");
    }
}
