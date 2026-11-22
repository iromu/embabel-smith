/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.tool.Tool;
import com.embabel.smith.builder.tools.AgentSpecValidationTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool list {@link AgentSpecInvokerFactory} hands every deploy (design D7's route B),
 * checked offline at the only surface that matters: what the model is told it may call,
 * and what the call does when it arrives.
 *
 * <p>
 * <b>Where the tool is offered.</b> Through the {@code validate} tool group that
 * {@code tools/ValidationToolGroup} registers, not through a deploy: the object asserted
 * here is the same one that group carries, and {@code tools/ValidationToolGroupTest} is
 * what pins the group's role and its one-member membership. The deploys carry no tool
 * list at all, because a step selects from such a list by a {@code tools:} name
 * {@code ActionStepSpec} cannot express — and such a list is not readable back off a
 * deployed {@code AgentScope} anyway (neither {@code AgentScope} nor {@code Action}
 * publishes it), so there is no size left here to assert about it.
 *
 * <p>
 * <b>Why the body is written and not reflected.</b> The control test below runs the route
 * this class did not take: {@code Tool.fromInstance} does find the annotated method —
 * {@code MethodToolFactory} filters {@code declaredMethods} by {@code LlmTool}, so the
 * annotation is not inert outside an {@code @Agent} class — but {@code JavaMethodTool}
 * takes the parameter name from reflection, and this module compiles without
 * {@code -parameters}, so the name it publishes is javac's placeholder rather than the
 * one in the adapter's signature. Since the same name is the key the call reads back, a
 * schema reading {@code arg0} is a tool that binds only when the model guesses the
 * placeholder. That is why {@link BuilderPackTools} authors the schema instead, and the
 * control is what keeps the reason on file rather than in a comment.
 *
 * @author Ivan Rodriguez
 */
class BuilderPackToolsTest {

	/**
	 * The argument's wire name, spelled here rather than read from
	 * {@code BuilderPackTools.SPEC_JSON}: an assertion written against the constant holds
	 * whatever the constant says, and the string the model is offered is the fact worth
	 * pinning.
	 */
	private static final String SPEC_JSON = "specJson";

	/**
	 * The finding the cases below read: {@code AgentSpecValidator.checkTypeReferences}
	 * raises it for an output type that is neither framework-provided nor declared in
	 * {@code types/}, and it is a finding about the spec, so no directory on disk can
	 * produce it.
	 */
	private static final String DANGLING = "DANGLING_TYPE_REF";

	@Test
	void theSchemaNamesTheArgumentAfterTheAdaptersSignatureRatherThanJavacsPlaceholder() {
		Tool tool = BuilderPackTools.validationTool();
		List<Tool.Parameter> parameters = tool.getDefinition().getInputSchema().getParameters();

		assertThat(parameters).as("the validator takes one argument").hasSize(1);
		assertThat(parameters.get(0).getName()).as("the name the model is given, which is also the key the call binds")
			.isEqualTo(SPEC_JSON);
		assertThat(tool.getDefinition().getInputSchema().toJsonSchema()).contains(SPEC_JSON).doesNotContain("arg0");
	}

	@Test
	void theToolBodyCallsTheValidatorAndReportsExactlyWhatItFound() throws JsonProcessingException {
		String wellFormed = BuilderPackTools.findings(wrapped(specJson("com.embabel.chat.AssistantMessage")));
		String broken = BuilderPackTools.findings(wrapped(specJson("NoSuchType")));

		// The pass is the validator's silence, the failure is its finding verbatim —
		// code,
		// message and the step file it names — so nothing was added between the tool and
		// the service.
		assertThat(wellFormed).as("findings for a sound spec").startsWith("No findings");
		assertThat(broken).as("findings for a draft with an undeclared output type")
			.contains("neither framework-provided nor declared")
			.contains("steps/say.yml");
	}

	/**
	 * The fix biting where the measurement said it did not: the argument is the draft
	 * itself, so the validator's semantic checks run on the text in front of the model.
	 * Before this change the same call had no pack on disk to point at, and its answer
	 * was the layout finding a directory without a {@code manifest.yml} produces — 92 of
	 * the 93 calls of one measured build were that one string.
	 */
	@Test
	void aSpecCarriedAsTheArgumentIsCheckedAsASpecRatherThanAsADirectoryOnDisk() throws JsonProcessingException {
		String findings = BuilderPackTools.findings(wrapped(specJson("NoSuchType")));

		assertThat(findings).as("findings for a draft whose step outputs an undeclared type")
			.contains(DANGLING)
			.doesNotContain("LAYOUT_MISSING_MANIFEST");
	}

	/**
	 * The same spec with no wrapper key naming it, which is what a model sends when it
	 * treats the argument list as the answer. The raw text is a spec, so it is passed
	 * through rather than discarded as a call that carried no argument.
	 */
	@Test
	void aCallThatSentTheSpecAsTheWholeArgumentIsCheckedToo() {
		assertThat(BuilderPackTools.findings(specJson("NoSuchType"))).contains(DANGLING)
			.doesNotContain("LAYOUT_MISSING_MANIFEST");
	}

	private static String wrapped(String spec) throws JsonProcessingException {
		return new ObjectMapper().writeValueAsString(Map.of(SPEC_JSON, spec));
	}

	/**
	 * A one-step spec in the shape a drafting step returns, with the step's output type
	 * spelled {@code stepOutputType}. Everything else is sound — the goal exports and
	 * names a framework type, the step interpolates its one declared input — so the
	 * step's output type is the only finding the validator has to report.
	 * @param stepOutputType the {@code outputTypeName} to write on the step
	 * @return the spec as JSON text
	 */
	private static String specJson(String stepOutputType) {
		return specJson(stepOutputType, true);
	}

	private static String specJson(String stepOutputType, boolean exported) {
		return """
				{"manifest": {"name": "probe", "version": "1.0.0"},
				 "goal": {"stepType": "goal", "name": "done", "description": "A drafted spec",
				          "outputTypeName": "com.embabel.chat.AssistantMessage", "export": %s},
				 "steps": [{"stepType": "action", "name": "say", "description": "Say the input back",
				             "inputTypeNames": ["com.embabel.agent.domain.io.UserInput"],
				             "outputTypeName": "%s",
				             "prompt": "Repeat: {{userInput.content}}"}]}
				""".formatted(exported, stepOutputType);
	}

	/**
	 * What the model reads for a draft whose only defect the build patches on its own:
	 * the goal's missing export is named, and named as the build's work, rather than as
	 * homework — and it is still named, so the report cannot quietly decide the draft was
	 * flawless.
	 */
	@Test
	void aDraftWhoseOnlyDefectTheBuildPatchesIsNotHandedBackAsHomework() throws JsonProcessingException {
		String findings = BuilderPackTools.findings(wrapped(specJson("com.embabel.chat.AssistantMessage", false)));

		assertThat(findings).as("nothing to edit").contains("No findings you need to fix");
		assertThat(findings).as("the goal finding is still named, as the build's own fix")
			.contains("GOAL_NOT_EXPORTED")
			.contains("normalizer");
	}

	/**
	 * The same draft with a defect no normalizer touches. The type finding keeps its line
	 * and its place ahead of the build-patched note, so the second half cannot excuse the
	 * first.
	 */
	@Test
	void aFindingTheBuildCannotFixKeepsItsLineBesideTheNoteAboutTheOnesItCan() throws JsonProcessingException {
		String findings = BuilderPackTools.findings(wrapped(specJson("NoSuchType", false)));

		assertThat(findings).contains(DANGLING).contains("GOAL_NOT_EXPORTED").contains("normalizer");
		assertThat(findings.indexOf(DANGLING)).as("the finding the model must fix comes first")
			.isLessThan(findings.indexOf("normalizer"));
	}

	@Test
	void aCallThatNamedNoSpecIsAnsweredRatherThanThrown() {
		assertThat(BuilderPackTools.findings("{}")).contains(SPEC_JSON);
		assertThat(BuilderPackTools.findings(null)).contains(SPEC_JSON);

		// The call the old tool answered with a layout finding. A path is not a spec, so
		// it is now the validator's parse finding instead — still a finding, and one that
		// says what is wrong with the argument rather than with a directory.
		assertThat(BuilderPackTools.findings("\"builder-agent-specs/builder-single-spec\"")).contains("PARSE_ERROR");
	}

	/**
	 * The tool and the adapter describe the same service: the name is the annotated
	 * method's, the description is the one {@code @LlmTool} carries. Read reflectively so
	 * the pair cannot drift from the class whose behaviour the tool has.
	 * @throws ReflectiveOperationException when the adapter no longer has the method the
	 * tool wraps
	 */
	@Test
	void theToolDescribesTheAdapterAsItsAnnotationDoes() throws ReflectiveOperationException {
		Method method = AgentSpecValidationTool.class.getDeclaredMethod("validatePack", String.class);
		LlmTool annotation = method.getAnnotation(LlmTool.class);

		assertThat(annotation).as("the adapter still carries @LlmTool").isNotNull();
		assertThat(BuilderPackTools.VALIDATE_PACK).as("a blank @LlmTool name means the method's own name")
			.isEqualTo(annotation.name().isEmpty() ? method.getName() : annotation.name());
		assertThat(BuilderPackTools.validationTool().getDefinition().getDescription())
			.as("description the model reads for %s", BuilderPackTools.VALIDATE_PACK)
			.isEqualTo(annotation.description());
	}

	/**
	 * The route not taken, kept as an executable fact. {@code fromInstance} does yield
	 * the annotated method — so {@code @LlmTool} is not inert on a plain Java class,
	 * which is what task 1.1 warned might be the case — and the argument it publishes is
	 * named by reflection, which in this module is javac's placeholder because no pom
	 * passes {@code -parameters}. Both halves matter: the first says the capability
	 * exists, the second says why it is not good enough to hand over as is.
	 */
	@Test
	void fromTheAnnotationYieldsAToolWhoseArgumentReflectionNamesWithoutParameters() {
		List<Tool> reflected = Tool.fromInstance(new AgentSpecValidationTool());

		assertThat(reflected).as("fromInstance found the @LlmTool method").hasSize(1);
		List<Tool.Parameter> parameters = reflected.get(0).getDefinition().getInputSchema().getParameters();
		assertThat(parameters).as("one argument, as the adapter declares").hasSize(1);
		assertThat(parameters.get(0).getName())
			.as("the name javac emitted: no pom in this repository passes -parameters")
			.isNotEqualTo(SPEC_JSON);
	}

}
