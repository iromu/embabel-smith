/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies task 1.3: the validator accepts a well-formed pack and rejects packs with
 * structural faults (dangling type reference, non-exported goal, unbound prompt
 * reference, a declared input the prompt never interpolates, bad layout).
 */
class AgentSpecValidatorTest {

	private final AgentSpecValidator validator = new AgentSpecValidator();

	private static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";

	private static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";

	@Test
	void acceptsAWellFormedPack() {
		ValidationResult result = validator.validate(SampleAgentSpecs.explainer());

		assertThat(result.isValid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsADanglingOutputTypeReference() {
		ActionStepSpec step = ActionStepSpec.action("broken", "step", List.of(USER_INPUT), "NonexistentType",
				"Say: {{userInput.content}}");
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), exportedGoal(), List.of(step), List.of());

		ValidationResult result = validator.validate(pack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issuesWithCode("DANGLING_TYPE_REF")).isNotEmpty();
		assertThat(result.issues()).anyMatch(i -> i.message().contains("NonexistentType"));
	}

	@Test
	void rejectsADanglingInputTypeReference() {
		ActionStepSpec step = ActionStepSpec.action("broken", "step", List.of("GhostType"), BACKGROUND_MESSAGE,
				"Say: {{ghostType.x}}");
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), exportedGoal(), List.of(step), List.of());

		ValidationResult result = validator.validate(pack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issues())
			.anyMatch(i -> i.code().equals("DANGLING_TYPE_REF") && i.message().contains("GhostType"));
	}

	@Test
	void rejectsANonExportedGoal() {
		GoalStepSpec notExported = new GoalStepSpec("goal", "g", "A goal", BACKGROUND_MESSAGE, false);
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), notExported, List.of(), List.of());

		ValidationResult result = validator.validate(pack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issuesWithCode("GOAL_NOT_EXPORTED")).isNotEmpty();
	}

	@Test
	void rejectsAPromptThatReferencesAnUndeclaredInput() {
		ActionStepSpec step = ActionStepSpec.action("s", "step", List.of(USER_INPUT), BACKGROUND_MESSAGE,
				"Hello {{someone.name}}");
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), exportedGoal(), List.of(step), List.of());

		ValidationResult result = validator.validate(pack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issuesWithCode("PROMPT_UNDECLARED_INPUT")).isNotEmpty();
	}

	@Test
	void rejectsAPromptThatReferencesAnUnknownProperty() {
		DynamicTypeDef brief = new DynamicTypeDef("TopicBrief", "brief",
				List.of(new PropertyDef("topic", "string", "what")), List.of(), true);
		ActionStepSpec step = ActionStepSpec.action("s", "step", List.of("TopicBrief"), BACKGROUND_MESSAGE,
				"About {{topicBrief.nonexistent}}");
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), exportedGoal(), List.of(step), List.of(brief));

		ValidationResult result = validator.validate(pack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issuesWithCode("PROMPT_UNKNOWN_PROPERTY")).isNotEmpty();
	}

	@Test
	void rejectsAStepWhosePromptCannotSeeItsDeclaredInput() {
		// The POC 27 shape: the step declares UserInput, but its prompt is a fixed block
		// of policy text that names no variable, so the literal-template render sends the
		// same request whatever the user asked.
		ActionStepSpec step = ActionStepSpec.action("detect", "step detect", List.of("UserInput"), BACKGROUND_MESSAGE,
				"POLICY b27-inner-seed TRIGGER diff-mention THEN severity high");
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), exportedGoal(), List.of(step), List.of());

		ValidationResult result = validator.validate(pack);

		List<ValidationIssue> rejects = result.issuesWithCode("PROMPT_IGNORES_DECLARED_INPUT");
		assertThat(rejects).hasSize(1);
		assertThat(rejects.get(0).message()).contains("detect");
		assertThat(rejects.get(0).location()).isEqualTo("steps/detect.yml");
	}

	@Test
	void acceptsAStepThatInterpolatesItsDeclaredInputByProperty() {
		// The shape of agent-specs/explainer/steps/writeExplainer.yml: the input is
		// interpolated through its properties rather than as a bare variable.
		DynamicTypeDef brief = topicBrief();
		ActionStepSpec step = ActionStepSpec.action("writeExplainer", "step", List.of("TopicBrief"), BACKGROUND_MESSAGE,
				"About: {{topicBrief.topic}}\nAudience: {{topicBrief.audience}}\nTone: {{topicBrief.tone}}");
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), exportedGoal(), List.of(step), List.of(brief));

		assertThat(validator.validate(pack).isValid()).isTrue();
	}

	@Test
	void acceptsAStepThatIgnoresOneOfSeveralInputs() {
		// The rule is "names none of them", not "names every one of them": a step may
		// declare an input it does not interpolate, so a deliberate fixed-output step
		// that
		// also carries a binding is not rejected.
		DynamicTypeDef brief = topicBrief();
		ActionStepSpec step = ActionStepSpec.action("writeExplainer", "step", List.of(USER_INPUT, "TopicBrief"),
				BACKGROUND_MESSAGE, "Write an explainer about {{topicBrief.topic}}");
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), exportedGoal(), List.of(step), List.of(brief));

		assertThat(validator.validate(pack).isValid()).isTrue();
	}

	@Test
	void acceptsAFixedOutputStepThatDeclaresNoInput() {
		// A generator with nothing to interpolate declares nothing, so the rule's
		// precondition is not met.
		ActionStepSpec step = ActionStepSpec.action("announce", "step", List.of(), BACKGROUND_MESSAGE,
				"Say that the build finished.");
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("p", "1"), exportedGoal(), List.of(step), List.of());

		assertThat(validator.validate(pack).isValid()).isTrue();
	}

	@Test
	void rejectsAPackMissingItsGoalOnDisk(@TempDir Path dir) throws Exception {
		new AgentSpecWriter().write(SampleAgentSpecs.explainer(), dir);
		Files.delete(dir.resolve("goal.yml"));

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issuesWithCode("LAYOUT_MISSING_GOAL")).isNotEmpty();
	}

	@Test
	void validatesAPackWrittenToDiskEndToEnd(@TempDir Path dir) throws Exception {
		Path written = new AgentSpecWriter().write(SampleAgentSpecs.explainer(), dir);

		assertThat(validator.validate(written).isValid()).isTrue();
	}

	private static GoalStepSpec exportedGoal() {
		return new GoalStepSpec("goal", "g", "A goal", BACKGROUND_MESSAGE, true);
	}

	private static DynamicTypeDef topicBrief() {
		return new DynamicTypeDef(
				"TopicBrief", "brief", List.of(new PropertyDef("topic", "string", "what"),
						new PropertyDef("audience", "string", "who"), new PropertyDef("tone", "string", "how")),
				List.of(), true);
	}

}
