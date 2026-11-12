package com.embabel.smith.studio.improve;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.studio.rest.BuildController;
import com.embabel.smith.studio.rest.ImproveController;
import com.embabel.smith.studio.rest.RunController;
import com.embabel.smith.studio.rest.dto.BuildRequest;
import com.embabel.smith.studio.rest.dto.BuildResponse;
import com.embabel.smith.studio.rest.dto.CandidateInfo;
import com.embabel.smith.studio.rest.dto.IterationInfo;
import com.embabel.smith.studio.rest.dto.RunRequest;
import com.embabel.smith.studio.rest.dto.RunResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoint-gated integration test (task 5.2): drive the three production surfaces — run a goal
 * (change-03 runtime), build a pack (change-04 builder), and trigger one loop iteration (change-05
 * loop) that yields a held candidate from a seeded failing trace — through the REST controllers with
 * the real, model-backed cores wired. Skipped when the model endpoint is not configured.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class RestIntegrationTest {

    static final Path ROOT;
    static final Path SOURCE;
    static final Path TRACE_DIR;

    static {
        try {
            ROOT = Files.createTempDirectory("smith-studio-rest-it-");
            SOURCE = ROOT.resolve("source");
            TRACE_DIR = ROOT.resolve("traces");
            Files.createDirectories(TRACE_DIR);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired
    RunController runController;

    @Autowired
    BuildController buildController;

    @Autowired
    ImproveController improveController;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("smith.studio.sources", () -> "folder:" + SOURCE);
        registry.add("smith.studio.improve-source", () -> "folder:" + SOURCE);
        registry.add("smith.studio.trace-dir", () -> TRACE_DIR.toString());
        registry.add("smith.studio.loop.interval", () -> "3600000");
        registry.add("smith.studio.loop.auto-publish", () -> "false");
    }

    @Test
    void runBuildAndLoopOverTheRestSurface() throws Exception {
        // A live pack in the source, plus a seeded failing trace to drive the loop's detection.
        new AgentSpecWriter().write(livePack(), SOURCE.resolve("summarize"));
        new JsonlTraceStore(TRACE_DIR.resolve("runs.jsonl")).append(
                TestFixtures.failedTrace("seed-1", "summarize", "Describe your use case.",
                        TestFixtures.step("summarize", "failed", "produced no summary")));

        // 1. Run a goal through the runtime core.
        RunResponse run = runController.run(new RunRequest(
                "folder:" + SOURCE, "summarize", "Describe your use case.", null, null));
        assertThat(run.success()).isTrue();
        assertThat(run.output()).isNotBlank();

        // 2. Build a pack through the builder core.
        BuildResponse build = buildController.build(new BuildRequest(
                "Summarize a piece of text concisely", "folder:" + SOURCE, null, null, null));
        assertThat(build.errorCode()).isNull();
        assertThat(build.status()).isIn("BUILT", "BLOCKED");

        // 3. Trigger one loop iteration.
        IterationInfo iteration = improveController.trigger();
        assertThat(iteration.ran()).isTrue();
        assertThat(iteration.errorCode()).isNull();

        // 4. The seeded failing trace produced a held candidate.
        List<CandidateInfo> candidates = improveController.candidates();
        assertThat(candidates).extracting(CandidateInfo::spec).contains("summarize");
    }

    /** A realistic single-step summarizer, deployable by the runtime (canonical FQN types). */
    private static AgentSpec livePack() {
        return new AgentSpec(
                new AgentSpecManifest("summarize", "1.0.0"),
                GoalStepSpec.exportedGoal("summarize", "Summarize an input", BACKGROUND_MESSAGE),
                List.of(ActionStepSpec.action("summarize", "Summarize the input",
                        List.of(USER_INPUT), BACKGROUND_MESSAGE,
                        "Summarize the following text concisely: {{userInput.content}}")),
                List.of());
    }

    private static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";
    private static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";
}
