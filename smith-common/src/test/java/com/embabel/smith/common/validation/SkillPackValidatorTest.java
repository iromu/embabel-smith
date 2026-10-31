package com.embabel.smith.common.validation;

import com.embabel.smith.common.io.SkillPackWriter;
import com.embabel.smith.common.samples.SampleSkillPacks;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies task 1.3: the validator accepts a well-formed pack and rejects packs with structural
 * faults (dangling type reference, non-exported goal, unbound prompt reference, bad layout).
 */
class SkillPackValidatorTest {

    private final SkillPackValidator validator = new SkillPackValidator();

    private static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";
    private static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";

    @Test
    void acceptsAWellFormedPack() {
        ValidationResult result = validator.validate(SampleSkillPacks.explainer());

        assertThat(result.isValid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void rejectsADanglingOutputTypeReference() {
        ActionStepSpec step = ActionStepSpec.action("broken", "step",
                List.of(USER_INPUT), "NonexistentType", "Say: {{userInput.content}}");
        SkillPack pack = new SkillPack(
                new SkillPackManifest("p", "1"), exportedGoal(), List.of(step), List.of());

        ValidationResult result = validator.validate(pack);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issuesWithCode("DANGLING_TYPE_REF")).isNotEmpty();
        assertThat(result.issues()).anyMatch(i -> i.message().contains("NonexistentType"));
    }

    @Test
    void rejectsADanglingInputTypeReference() {
        ActionStepSpec step = ActionStepSpec.action("broken", "step",
                List.of("GhostType"), BACKGROUND_MESSAGE, "Say: {{ghostType.x}}");
        SkillPack pack = new SkillPack(
                new SkillPackManifest("p", "1"), exportedGoal(), List.of(step), List.of());

        ValidationResult result = validator.validate(pack);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> i.code().equals("DANGLING_TYPE_REF")
                && i.message().contains("GhostType"));
    }

    @Test
    void rejectsANonExportedGoal() {
        GoalStepSpec notExported = new GoalStepSpec("goal", "g", "A goal", BACKGROUND_MESSAGE, false);
        SkillPack pack = new SkillPack(new SkillPackManifest("p", "1"), notExported, List.of(), List.of());

        ValidationResult result = validator.validate(pack);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issuesWithCode("GOAL_NOT_EXPORTED")).isNotEmpty();
    }

    @Test
    void rejectsAPromptThatReferencesAnUndeclaredInput() {
        ActionStepSpec step = ActionStepSpec.action("s", "step",
                List.of(USER_INPUT), BACKGROUND_MESSAGE, "Hello {{someone.name}}");
        SkillPack pack = new SkillPack(
                new SkillPackManifest("p", "1"), exportedGoal(), List.of(step), List.of());

        ValidationResult result = validator.validate(pack);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issuesWithCode("PROMPT_UNDECLARED_INPUT")).isNotEmpty();
    }

    @Test
    void rejectsAPromptThatReferencesAnUnknownProperty() {
        DynamicTypeDef brief = new DynamicTypeDef("TopicBrief", "brief",
                List.of(new PropertyDef("topic", "string", "what")), List.of(), true);
        ActionStepSpec step = ActionStepSpec.action("s", "step",
                List.of("TopicBrief"), BACKGROUND_MESSAGE, "About {{topicBrief.nonexistent}}");
        SkillPack pack = new SkillPack(
                new SkillPackManifest("p", "1"), exportedGoal(), List.of(step), List.of(brief));

        ValidationResult result = validator.validate(pack);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issuesWithCode("PROMPT_UNKNOWN_PROPERTY")).isNotEmpty();
    }

    @Test
    void rejectsAPackMissingItsGoalOnDisk(@TempDir Path dir) throws Exception {
        new SkillPackWriter().write(SampleSkillPacks.explainer(), dir);
        Files.delete(dir.resolve("goal.yml"));

        ValidationResult result = validator.validate(dir);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issuesWithCode("LAYOUT_MISSING_GOAL")).isNotEmpty();
    }

    @Test
    void validatesAPackWrittenToDiskEndToEnd(@TempDir Path dir) throws Exception {
        Path written = new SkillPackWriter().write(SampleSkillPacks.explainer(), dir);

        assertThat(validator.validate(written).isValid()).isTrue();
    }

    private static GoalStepSpec exportedGoal() {
        return new GoalStepSpec("goal", "g", "A goal", BACKGROUND_MESSAGE, true);
    }
}
