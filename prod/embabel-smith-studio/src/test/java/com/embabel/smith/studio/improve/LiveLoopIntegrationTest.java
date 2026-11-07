package com.embabel.smith.studio.improve;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.eval.AgentSkillInvokerFactory;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.LlmPromptRewriter;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.io.SkillPackWriter;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.skillsource.FolderSkillSource;
import com.embabel.smith.skillsource.SkillSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoint-gated loop integration test (task 3.2): one full loop iteration on a live pack, with the
 * real refine and re-eval, publishes an improved pack to a local folder source. Skipped when the
 * model endpoint is not configured.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class LiveLoopIntegrationTest {

    @Autowired
    Ai ai;

    @Autowired
    AgentPlatform platform;

    @Test
    void oneIterationPublishesAnImprovedPack(@TempDir Path tmp) throws Exception {
        // A live pack in a folder source, with a failing trace to trigger refinement.
        Path sourceRoot = tmp.resolve("source");
        SkillPack original = livePack();
        new SkillPackWriter().write(original, sourceRoot.resolve("summarize"));
        SkillSource source = new FolderSkillSource("folder:" + sourceRoot, sourceRoot);

        JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces.jsonl"));
        store.append(TestFixtures.failedTrace("r1", "summarize", "Describe your use case.",
                TestFixtures.step("summarize", "failed", "produced no summary")));

        LlmPromptRewriter rewriter = new LlmPromptRewriter(
                env("OPENAI_CUSTOM_BASE_URL"), env("OPENAI_CUSTOM_API_KEY"), firstModel());
        // An empty-expected task passes on any non-null output, so the gate reliably clears the
        // (zero) baseline once the real re-eval produces output.
        List<EvalTask> tasks = List.of(
                new EvalTask("runs-and-produces-output", "Describe your use case.", ""));
        FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + sourceRoot));

        ImprovementLoop loop = new ImprovementLoop(source, store, rewriter,
                new AgentSkillInvokerFactory(platform), tasks, publisher, true); // auto-publish

        List<PendingCandidate> held = loop.runOnce();

        assertThat(held).isEmpty(); // auto-published, not held
        Path improved = sourceRoot.resolve("summarize");
        assertThat(improved.resolve("manifest.yml")).isRegularFile();
        assertThat(improved.resolve("lifecycle.yml")).isRegularFile();
        // The published pack transitioned incubating -> active (a first proven improvement).
        LifecycleMetadata lifecycle = Yaml.mapper().readValue(
                Files.readString(improved.resolve("lifecycle.yml")), LifecycleMetadata.class);
        assertThat(lifecycle.state()).isEqualTo(LifecycleState.ACTIVE);
        assertThat(new SkillPackReader().read(improved).step("summarize")).isNotNull();
    }

    /** A realistic single-step summarizer skill, deployable by the runtime (canonical FQN types). */
    private static SkillPack livePack() {
        return new SkillPack(
                new SkillPackManifest("summarize", "1.0.0"),
                GoalStepSpec.exportedGoal("summarize", "Summarize an input", BACKGROUND_MESSAGE),
                List.of(ActionStepSpec.action("summarize", "Summarize the input",
                        List.of(USER_INPUT), BACKGROUND_MESSAGE,
                        "Summarize the following text concisely: {{userInput.content}}")),
                List.of());
    }

    /** The canonical returnable output type and entry input type (the builder's normalized form). */
    private static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";
    private static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String firstModel() {
        String models = env("OPENAI_CUSTOM_MODELS");
        return models.isBlank() ? "" : models.split(",")[0].trim();
    }
}
