/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The honesty check on step keys: the reader ignores a key it does not know, so a pack
 * can carry a written wish the runtime never implements, and it reads as configured. Two
 * different sentences come out of this, and the point of this file is that they stay
 * different.
 * <p>
 * A key nothing in the format implements — a mistyped one, or an invented control such as
 * a per-step approval or a retry count — is refused by name, because a silently ignored
 * safety key is worse than an absent one. A key the upstream model does implement but
 * that this repo's writer never emits ({@code llm}, {@code tools}, {@code export}) is not
 * refused: the runtime stages step YAML verbatim, so it works at deploy — it is reported
 * as a key that will not survive a rewrite.
 *
 * @author Ivan Rodriguez
 */
class AgentSpecStepKeyTest {

	private final AgentSpecValidator validator = new AgentSpecValidator();

	// --- a pack written by the format is clean -------------------------------

	@Test
	void acceptsEveryKeyTheWriterItselfEmits(@TempDir Path dir) throws Exception {
		// The both-directions half: if this reported anything, the check would be firing
		// on
		// every pack this repo writes. It caught exactly one such key when it was first
		// run —
		// the goal file's derived "exported" — which is why it is pinned here.
		write(pack(), dir);

		ValidationResult result = validator.validate(dir);

		assertThat(result.issues()).isEmpty();
		assertThat(Files.readString(dir.resolve("goal.yml"))).doesNotContain("exported:");
	}

	// --- a mistyped key ------------------------------------------------------

	@Test
	void reportsAMistypedKeyAsUnrecognizedNamingTheStepAndTheKey(@TempDir Path dir) throws Exception {
		write(pack(), dir);
		appendToStep(dir, "toolgroupz:\n  - file\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("UNKNOWN_STEP_KEY");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("toolgroupz");
		assertThat(issues.get(0).location()).isEqualTo("steps/actOnIt.yml");
	}

	// --- an invented control key ---------------------------------------------

	@Test
	void refusesAnInventedApprovalKey(@TempDir Path dir) throws Exception {
		// The case the format must not have: a step key that reads as "wait for a person"
		// while the runtime has no pause and no resume surface at all.
		write(pack(), dir);
		appendToStep(dir, "requires-approval: true\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("UNKNOWN_STEP_KEY");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("requires-approval").contains("doing nothing");
	}

	@Test
	void refusesAnInventedRetryCountKey(@TempDir Path dir) throws Exception {
		write(pack(), dir);
		appendToStep(dir, "retry: 3\n");

		assertThat(validator.validate(dir).issuesWithCode("UNKNOWN_STEP_KEY")).hasSize(1);
	}

	@Test
	void refusesAFlowKeyTheFormatHasNoConstructFor(@TempDir Path dir) throws Exception {
		// No loop construct exists, so a repeat key would be read, dropped, and left
		// looking
		// like a configured iteration.
		write(pack(), dir);
		appendToStep(dir, "repeat: until_done\n");

		assertThat(validator.validate(dir).issuesWithCode("UNKNOWN_STEP_KEY")).hasSize(1);
	}

	@Test
	void reportsAnUnknownKeyInGoalFileToo(@TempDir Path dir) throws Exception {
		write(pack(), dir);
		Files.writeString(dir.resolve("goal.yml"),
				Files.readString(dir.resolve("goal.yml")) + "on_approval: escalate\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("UNKNOWN_STEP_KEY");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).location()).isEqualTo("goal.yml");
	}

	// --- a key that survives staging but not a rewrite -----------------------

	@Test
	void reportsAStepModelKeyTheWriterNeverEmitsAsNotPreserved(@TempDir Path dir) throws Exception {
		// Upstream's action-step model carries llm, tools and export; the runtime stages
		// the
		// step file verbatim, so all three reach it and are honored at deploy. This
		// repo's
		// writer serializes the model, which has none of them, so a studio save or a
		// builder
		// repair loses the declaration silently.
		for (String key : List.of("llm", "tools", "export")) {
			Path dir2 = Files.createTempDirectory("keys-" + key);
			write(pack(), dir2);
			appendToStep(dir2, key + ": [thing]\n");

			ValidationResult result = validator.validate(dir2);

			assertThat(result.isValid()).as("'%s' must not refuse the pack", key).isTrue();
			List<ValidationIssue> issues = result.issuesWithCode("KEY_NOT_PRESERVED_ON_REWRITE");
			assertThat(issues).as("key %s", key).hasSize(1);
			assertThat(issues.get(0).message()).contains(key).contains("rewrite");
			assertThat(issues.get(0).severity()).isEqualTo(ValidationIssue.Severity.ADVISORY);
			// It is not an unknown key, so the refusal above must stay silent about it.
			assertThat(result.issuesWithCode("UNKNOWN_STEP_KEY")).isEmpty();
		}
	}

	@Test
	void givesTwoDifferentSentencesForAnInventedKeyAndAnUnpreservedOne(@TempDir Path dir) throws Exception {
		// The reports are different sentences, not one report with two labels: one names
		// a
		// key the format does not implement, the other names a key a rewrite will drop.
		write(pack(), dir);
		appendToStep(dir, "requires-approval: true\nllm: [pinned]\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issuesWithCode("UNKNOWN_STEP_KEY")).hasSize(1);
		assertThat(result.issuesWithCode("KEY_NOT_PRESERVED_ON_REWRITE")).hasSize(1);
		String invented = result.issuesWithCode("UNKNOWN_STEP_KEY").get(0).message();
		String unpreserved = result.issuesWithCode("KEY_NOT_PRESERVED_ON_REWRITE").get(0).message();
		assertThat(invented).contains("not a key the agent spec format implements").doesNotContain("rewrite");
		assertThat(unpreserved).contains("will not survive a rewrite").doesNotContain("not a key");
	}

	// --- fixtures ------------------------------------------------------------

	private static void write(AgentSpec pack, Path dir) throws IOException {
		new AgentSpecWriter().write(pack, dir);
	}

	private static void appendToStep(Path dir, String lines) throws IOException {
		Files.writeString(dir.resolve("steps/actOnIt.yml"), lines, java.nio.file.StandardOpenOption.APPEND);
	}

	private static AgentSpec pack() {
		GoalStepSpec goal = new GoalStepSpec("goal", "workDone", "The work is done",
				SampleAgentSpecs.BACKGROUND_MESSAGE, true);
		ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, "actOnIt", "Do the thing",
				List.of(SampleAgentSpecs.USER_INPUT), SampleAgentSpecs.BACKGROUND_MESSAGE,
				"Work on: {{userInput.content}}", null, null, null, null, null, null, null, List.of());
		return new AgentSpec(new AgentSpecManifest("keycheck", "1.0.0"), goal, List.of(step), List.of());
	}

}
