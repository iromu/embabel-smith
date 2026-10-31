package com.embabel.smith.runtime;

import com.embabel.smith.common.io.SkillPackWriter;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the step-spec assembly: the goal and its action steps are merged into one flat
 * directory that {@code YmlStepSpecRepository} can read, leaving the source pack untouched.
 */
class StepSpecAssemblerTest {

    private final StepSpecAssembler assembler = new StepSpecAssembler();

    @Test
    void assemblesGoalAndStepsIntoOneFlatDirectory(@TempDir Path dir) throws Exception {
        Path packDir = new SkillPackWriter().write(SampleSkillPacks.explainer(), dir.resolve("pack"));
        Path target = dir.resolve("flat");

        assembler.assemble(packDir, target);

        assertThat(Files.isRegularFile(target.resolve("goal.yml"))).isTrue();
        assertThat(Files.isRegularFile(target.resolve("extractTopic.yml"))).isTrue();
        assertThat(Files.isRegularFile(target.resolve("writeExplainer.yml"))).isTrue();
        // the manifest and the types dir are NOT step specs, so they are not copied
        assertThat(Files.isRegularFile(target.resolve("manifest.yml"))).isFalse();
        assertThat(Files.isDirectory(target.resolve("types"))).isFalse();
        // the source pack is untouched
        assertThat(Files.isRegularFile(packDir.resolve("steps/extractTopic.yml"))).isTrue();
    }

    @Test
    void failsClearlyWhenThereIsNoGoal(@TempDir Path dir) throws Exception {
        Path packDir = new SkillPackWriter().write(SampleSkillPacks.explainer(), dir.resolve("pack"));
        Files.delete(packDir.resolve("goal.yml"));

        assertThatThrownBy(() -> assembler.assemble(packDir, dir.resolve("flat")))
                .hasMessageContaining("no goal.yml");
    }
}
