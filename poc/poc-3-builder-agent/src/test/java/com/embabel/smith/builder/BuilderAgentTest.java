package com.embabel.smith.builder;

import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests of the builder's pure steps (tasks 5.2, 5.3, 5.5). The {@code compile} step is
 * endpoint-gated and is not exercised here; {@code validateDraft} and {@code writeSkillPack} are
 * pure Java and are tested directly against a hand-built agent instance.
 */
class BuilderAgentTest {

    private BuilderAgent agent(Path outDir) {
        return new BuilderAgent(null, null, outDir.toString());
    }

    @Test
    void validateDraftAcceptsAConsistentPack(@TempDir Path out) {
        CompiledDraft draft = new CompiledDraft("explainer", SampleSkillPacks.explainer(), List.of());

        ValidatedDraft validated = agent(out).validateDraft(draft);

        assertThat(validated.valid()).isTrue();
        assertThat(validated.issues()).isEmpty();
    }

    @Test
    void validateDraftRejectsADanglingTypeReference(@TempDir Path out) {
        SkillPack broken = new SkillPack(
                new SkillPackManifest("broken", "1.0.0"),
                GoalStepSpec.exportedGoal("broken", "broken", "BackgroundMessage"),
                List.of(ActionStepSpec.action("badStep", "bad", List.of("UserInput"), "NonExistentType", "do it")),
                List.of());
        CompiledDraft draft = new CompiledDraft("broken", broken, List.of());

        ValidatedDraft validated = agent(out).validateDraft(draft);

        assertThat(validated.valid()).isFalse();
        assertThat(validated.issues()).anyMatch(issue -> issue.contains("NonExistentType"));
    }

    @Test
    void writeSkillPackWritesAndRevalidatesAValidDraft(@TempDir Path out) throws Exception {
        ValidatedDraft validated = new ValidatedDraft(SampleSkillPacks.explainer(), true, List.of());

        SkillPackRef ref = agent(out).writeSkillPack(validated);

        assertThat(Files.isRegularFile(ref.path().resolve("manifest.yml"))).isTrue();
        assertThat(Files.isRegularFile(ref.path().resolve("goal.yml"))).isTrue();
        assertThat(ref.name()).isEqualTo("explainer");
        // Re-reading from disk yields the same, still-valid pack (group-1 re-validation passed).
        assertThat(new SkillPackReader().read(ref.path()).name()).isEqualTo("explainer");
    }

    @Test
    void writeSkillPackRefusesAnInvalidDraft(@TempDir Path out) {
        ValidatedDraft invalid = new ValidatedDraft(SampleSkillPacks.explainer(), false, List.of("GOAL_NOT_EXPORTED"));

        assertThatThrownBy(() -> agent(out).writeSkillPack(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to write an invalid draft");
        // Nothing was written.
        assertThat(out.toFile().list()).isEmpty();
    }
}
