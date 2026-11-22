/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.util.List;

import com.embabel.smith.builder.tools.AgentSpecValidationTool.ValidationReport;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationIssue;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the validator tool tells a drafting model about a draft the build would fix by
 * itself.
 *
 * <p>
 * The build does not validate the model's raw answer:
 * {@code eval.PackDrivenAgentSpecGenerator} (and the single-shot generator beside it) run
 * {@code AgentSpecNormalizer.normalize} on the answer — a goal present but not exported
 * is exported, prompt variables are respelled the way the runtime binds them, a missing
 * manifest is filled — and only then does {@code BuilderService.java:141} hand the draft
 * to {@code BuilderAgent.validateDraft}. The tool used to validate the un-normalized
 * text, so it sent the model off to hand-edit findings the java path patches on its own;
 * a measured {@code --flow pack} run burned 19 calls on an input that never got an
 * answer.
 *
 * <p>
 * Each case therefore states both halves: the control asserts that the <em>raw</em> draft
 * really does carry the finding (so a case cannot pass because the spec was sound to
 * begin with), and the tool's report says which side of the normalizer it lands on. The
 * validator is the real {@code AgentSpecValidator} — the thing under test is the advice
 * wrapped around it, so nothing here mocks it.
 *
 * @author Ivan Rodriguez
 */
class ValidationToolNormalizationTest {

	private final AgentSpecValidationTool tool = new AgentSpecValidationTool();

	private final AgentSpecValidator validator = new AgentSpecValidator();

	/**
	 * The named over-report, half one: a goal the draft left {@code export: false}. The
	 * build exports it ({@code AgentSpecNormalizer.java:93}), so it is not the model's
	 * homework — but it is still named, in the half of the report that says so.
	 */
	@Test
	void anUnexportedGoalIsReportedAsSomethingTheBuildFixesItself() {
		String draft = spec(false, "Repeat: {{userInput.content}}", "com.embabel.chat.AssistantMessage");
		assertThat(codes(rawFindings(draft))).as("control: the raw draft does fail on the goal")
			.contains("GOAL_NOT_EXPORTED");

		ValidationReport report = this.tool.validatePack(draft);

		assertThat(report.issues()).as("nothing the model has to edit").isEmpty();
		assertThat(report.isValid()).as("the spec the build would end up with is sound").isTrue();
		assertThat(codes(report.patchedByBuild())).as("the goal finding is named as the build's own fix")
			.containsExactly("GOAL_NOT_EXPORTED");
	}

	/**
	 * The other named over-report: a prompt variable spelled {@code user_input} where the
	 * declared input is {@code UserInput}. The validator calls that unbound and ignored;
	 * the normalizer respells it ({@code AgentSpecNormalizer.java:95}), so both findings
	 * are the build's, and the report must not present either as a defect in the draft.
	 */
	@Test
	void aPromptVarSpelledInAnotherCaseIsReportedAsSomethingTheBuildFixesItself() {
		String draft = spec(true, "Repeat: {{user_input.content}}", "com.embabel.chat.AssistantMessage");
		assertThat(codes(rawFindings(draft))).as("control: the raw draft fails on the variable")
			.contains("PROMPT_UNDECLARED_INPUT", "PROMPT_IGNORES_DECLARED_INPUT");

		ValidationReport report = this.tool.validatePack(draft);

		assertThat(report.issues()).as("nothing the model has to edit").isEmpty();
		assertThat(codes(report.patchedByBuild()))
			.as("the casing findings are named as the build's own fix, not as the draft's defects")
			.contains("PROMPT_UNDECLARED_INPUT", "PROMPT_IGNORES_DECLARED_INPUT");
	}

	/**
	 * The report must not become a swallow. A dangling output type is a finding no
	 * normalizer touches, so it stays in {@code issues} even when the same draft also
	 * carries a goal the build exports — and it must not leak into the patched half.
	 */
	@Test
	void aDanglingTypeRefStaysTheModelsHomeworkBesideAGoalTheBuildExports() {
		String draft = spec(false, "Repeat: {{userInput.content}}", "NoSuchType");

		ValidationReport report = this.tool.validatePack(draft);

		assertThat(report.isValid()).as("the build would still reject this draft").isFalse();
		assertThat(codes(report.issues())).as("the type finding is the model's to fix")
			.containsExactly("DANGLING_TYPE_REF");
		assertThat(report.issues().get(0).location()).as("with the step file the validator named").isNotNull();
		assertThat(codes(report.patchedByBuild())).as("only the goal is the build's")
			.containsExactly("GOAL_NOT_EXPORTED");
	}

	/**
	 * And the casing family's counterpart: a variable no declared input binds is not a
	 * spelling the normalizer can recognise, so it stays homework. Without this case the
	 * previous two would pass with a tool that simply assumed every prompt finding was
	 * the build's.
	 */
	@Test
	void aVariableNoDeclaredInputBindsStaysTheModelsHomework() {
		String draft = spec(true, "Repeat: {{bogus.content}}", "com.embabel.chat.AssistantMessage");

		ValidationReport report = this.tool.validatePack(draft);

		assertThat(codes(report.issues())).as("an unbound variable is neither patched nor excused by the build")
			.contains("PROMPT_UNDECLARED_INPUT", "PROMPT_IGNORES_DECLARED_INPUT");
		assertThat(report.patchedByBuild()).as("nothing was patched here").isEmpty();
	}

	/**
	 * The branch that carries no spec at all keeps its single finding and gains an empty
	 * patched half, so a caller rendering both halves says nothing about normalization it
	 * cannot support.
	 */
	@Test
	void anAnswerThatIsNotASpecIsStillJustAParseError() {
		ValidationReport report = this.tool.validatePack("\"builder-agent-specs/builder-single-spec\"");

		assertThat(codes(report.issues())).containsExactly("PARSE_ERROR");
		assertThat(report.patchedByBuild()).isEmpty();
	}

	private List<ValidationIssue> rawFindings(String draft) {
		try {
			ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
					false);
			return this.validator.validate(mapper.readValue(draft, AgentSpec.class)).issues();
		}
		catch (Exception ex) {
			throw new IllegalStateException("the fixture is not a readable agent spec", ex);
		}
	}

	/**
	 * A one-step draft in the shape a drafting step returns: a goal that may or may not
	 * export, a step interpolating one variable of its declared {@code UserInput}, and an
	 * output type.
	 * @param exported the goal's {@code export} flag
	 * @param prompt the step's prompt text
	 * @param stepOutputType the step's {@code outputTypeName}
	 * @return the draft as JSON text
	 */
	private static String spec(boolean exported, String prompt, String stepOutputType) {
		return """
				{"manifest": {"name": "probe", "version": "1.0.0"},
				 "goal": {"stepType": "goal", "name": "done", "description": "A drafted spec",
				          "outputTypeName": "com.embabel.chat.AssistantMessage", "export": %s},
				 "steps": [{"stepType": "action", "name": "say", "description": "Say the input back",
				             "inputTypeNames": ["com.embabel.agent.domain.io.UserInput"],
				             "outputTypeName": "%s",
				             "prompt": "%s"}]}
				""".formatted(exported, stepOutputType, prompt);
	}

	private static List<String> codes(List<ValidationIssue> issues) {
		return issues.stream().map(ValidationIssue::code).toList();
	}

}
