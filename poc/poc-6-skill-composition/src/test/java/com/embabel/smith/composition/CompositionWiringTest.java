package com.embabel.smith.composition;

import com.embabel.agent.core.DomainType;
import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.samples.SampleSkillPacks;
import com.embabel.smith.common.validation.SkillPackValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the composition wiring (offline, no model): the two independent skill-packs are valid on
 * their own, share the intermediate type that is the composition's glue, and merge into one flat
 * step-spec directory with both goals and both actions. The actual GOAP chain across the two packs
 * is exercised by the endpoint-gated run.
 */
class CompositionWiringTest {

    private final SkillPackReader reader = new SkillPackReader();
    private final SkillPackValidator validator = new SkillPackValidator();
    private final CompositionRunner runner = new CompositionRunner(null);

    private Path pack(String name) {
        return repoRoot().resolve("skill-packs").resolve(name);
    }

    private SkillPack load(String name) {
        try {
            return reader.read(pack(name));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + name, e);
        }
    }

    @Test
    void bothPacksAreValidIndependentSkills() {
        assertThat(validator.validate(pack("compose-extract")).isValid()).isTrue();
        assertThat(validator.validate(pack("compose-explain")).isValid()).isTrue();
    }

    @Test
    void theFinalGoalDependsOnTheOtherSkillThroughTheSharedType() {
        SkillPack extract = load("compose-extract");
        SkillPack explain = load("compose-explain");

        // explain's goal is BackgroundMessage, produced by an action that consumes the shared type.
        assertThat(explain.goal().outputTypeName()).isEqualTo(SampleSkillPacks.BACKGROUND_MESSAGE);
        ActionStepSpec producer = explain.steps().stream()
                .filter(s -> SampleSkillPacks.BACKGROUND_MESSAGE.equals(s.outputTypeName()))
                .findFirst().orElseThrow();
        assertThat(producer.inputTypeNames()).contains("TopicBrief");

        // ...and explain never produces TopicBrief itself — only extract does, from UserInput.
        assertThat(explain.steps()).noneMatch(s -> "TopicBrief".equals(s.outputTypeName()));
        ActionStepSpec source = extract.steps().stream()
                .filter(s -> "TopicBrief".equals(s.outputTypeName()))
                .findFirst().orElseThrow();
        assertThat(source.inputTypeNames()).contains(SampleSkillPacks.USER_INPUT);
    }

    @Test
    void combinedTypesDeduplicateTheSharedIntermediate() {
        List<DomainType> types = runner.combinedTypes(List.of(load("compose-extract"), load("compose-explain")));

        assertThat(types)
                .extracting(DomainType::getName)
                .containsExactlyInAnyOrder(
                        SampleSkillPacks.USER_INPUT, SampleSkillPacks.BACKGROUND_MESSAGE, "TopicBrief");
    }

    @Test
    void assembleCombinedMergesBothGoalsAndBothActions() throws Exception {
        Path target = runner.assembleCombined(List.of(pack("compose-extract"), pack("compose-explain")));

        long goalCount;
        try (var files = Files.list(target)) {
            goalCount = files.filter(f -> f.getFileName().toString().startsWith("goal-")).count();
        }
        assertThat(goalCount).as("both goals are merged (renamed to avoid collision)").isEqualTo(2);
        assertThat(Files.isRegularFile(target.resolve("extractTopic.yml"))).isTrue();
        assertThat(Files.isRegularFile(target.resolve("writeExplainer.yml"))).isTrue();
        // manifest and types are not step specs, so they are not copied
        assertThat(Files.isRegularFile(target.resolve("manifest.yml"))).isFalse();
        assertThat(Files.isDirectory(target.resolve("types"))).isFalse();
    }

    /** Walks up from the working directory to the repo root (where {@code skill-packs/} lives). */
    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("skill-packs/compose-extract"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root (skill-packs/compose-extract not found)");
    }
}
