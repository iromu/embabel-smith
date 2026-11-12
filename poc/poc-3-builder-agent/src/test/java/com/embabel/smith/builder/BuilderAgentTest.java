package com.embabel.smith.builder;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests of the builder's pure steps (tasks 5.2, 5.3, 5.5). The {@code compile} step is
 * endpoint-gated and is not exercised here; {@code validateDraft} and {@code writeAgentSpec} are
 * pure Java and are tested directly against a hand-built agent instance.
 */
class BuilderAgentTest {

    private BuilderAgent agent(Path outDir) {
        return new BuilderAgent(null, null, outDir.toString());
    }

    @Test
    void validateDraftAcceptsAConsistentPack(@TempDir Path out) {
        CompiledDraft draft = new CompiledDraft("explainer", SampleAgentSpecs.explainer(), List.of());

        ValidatedDraft validated = agent(out).validateDraft(draft);

        assertThat(validated.valid()).isTrue();
        assertThat(validated.issues()).isEmpty();
    }

    @Test
    void validateDraftRejectsADanglingTypeReference(@TempDir Path out) {
        AgentSpec broken = new AgentSpec(
                new AgentSpecManifest("broken", "1.0.0"),
                GoalStepSpec.exportedGoal("broken", "broken", "BackgroundMessage"),
                List.of(ActionStepSpec.action("badStep", "bad", List.of("UserInput"), "NonExistentType", "do it")),
                List.of());
        CompiledDraft draft = new CompiledDraft("broken", broken, List.of());

        ValidatedDraft validated = agent(out).validateDraft(draft);

        assertThat(validated.valid()).isFalse();
        assertThat(validated.issues()).anyMatch(issue -> issue.contains("NonExistentType"));
    }

    @Test
    void writeAgentSpecWritesAndRevalidatesAValidDraft(@TempDir Path out) throws Exception {
        ValidatedDraft validated = new ValidatedDraft(SampleAgentSpecs.explainer(), true, List.of());

        AgentSpecRef ref = agent(out).writeAgentSpec(validated);

        assertThat(Files.isRegularFile(ref.path().resolve("manifest.yml"))).isTrue();
        assertThat(Files.isRegularFile(ref.path().resolve("goal.yml"))).isTrue();
        assertThat(ref.name()).isEqualTo("explainer");
        // Re-reading from disk yields the same, still-valid pack (group-1 re-validation passed).
        assertThat(new AgentSpecReader().read(ref.path()).name()).isEqualTo("explainer");
    }

    @Test
    void writeAgentSpecRefusesAnInvalidDraft(@TempDir Path out) {
        ValidatedDraft invalid = new ValidatedDraft(SampleAgentSpecs.explainer(), false, List.of("GOAL_NOT_EXPORTED"));

        assertThatThrownBy(() -> agent(out).writeAgentSpec(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to write an invalid draft");
        // Nothing was written.
        assertThat(out.toFile().list()).isEmpty();
    }
}
