/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.embabel.smith.common.io.ProcessSpecReader;
import com.embabel.smith.common.io.ProcessSpecWriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ProcessValidator;
import com.embabel.smith.common.validation.ValidationIssue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapters on {@link AgentSpecValidationTool} and {@link ProcessValidationTool} are
 * pass-throughs, so the property under test is that <em>nothing was added or
 * interpreted</em> — not that validation works, which is the services' own tests'
 * business. Each case therefore feeds the same directory to the tool and to the service
 * and compares the two results, then pins one finding so an empty-equals-empty comparison
 * could not pass by accident.
 *
 * <p>
 * The packs are built here rather than taken from the builder's own packs, and reach the
 * tool as the JSON the tool now takes, so this stays offline and touches no endpoint. The
 * service is handed the same in-memory spec, which is the check the tool's spec-taking
 * entry point mirrors.
 *
 * @author Ivan Rodriguez
 */
class ValidationToolPassthroughTest {

	private final AgentSpecValidationTool packTool = new AgentSpecValidationTool();

	private final ProcessValidationTool processTool = new ProcessValidationTool();

	private final AgentSpecValidator validator = new AgentSpecValidator();

	private final ProcessValidator processValidator = new ProcessValidator();

	private final ProcessSpecWriter processWriter = new ProcessSpecWriter();

	@TempDir
	Path workDir;

	@Test
	void aWellFormedPackIsReportedAsCarryingNoFindings() throws JsonProcessingException {
		assertThat(this.packTool.validatePack(specJson(SampleAgentSpecs.singleStep())).issues()).as("tool findings")
			.isEmpty();
	}

	/**
	 * The load-bearing case: a spec the validator rejects. The tool's findings are the
	 * validator's, field for field — codes, messages and the offending step file — so the
	 * agent that reads them sees the service's verdict and not a paraphrase of it.
	 */
	@Test
	void aRejectedPackIsReportedWithTheValidatorFindingsUnaltered() throws JsonProcessingException {
		AgentSpec suspect = packWithDanglingOutputType();

		List<ValidationIssue> throughTool = this.packTool.validatePack(specJson(suspect)).issues();
		List<ValidationIssue> fromService = this.validator.validate(suspect).issues();

		assertThat(codes(throughTool)).as("codes reported through the tool").isEqualTo(codes(fromService));
		assertThat(throughTool).as("findings reported through the tool").isEqualTo(fromService);
		assertThat(throughTool).as("the pack is not sound, so the comparison is not vacuous")
			.anyMatch((issue) -> "DANGLING_TYPE_REF".equals(issue.code()) && "steps/say.yml".equals(issue.location()));
		assertThat(this.packTool.validatePack(specJson(suspect)).isValid()).isFalse();
	}

	@Test
	void aSoundProcessIsReportedAsCarryingNoFindings() throws IOException {
		Path dir = this.workDir.resolve("sound-process");
		this.processWriter.write(ProcessSpec.single("solo", SampleAgentSpecs.explainer()), dir);

		assertThat(this.processTool.validateProcess(dir.toString()).issues()).as("tool findings").isEmpty();
	}

	/**
	 * Two agents exporting one goal name — the collision the runtime rejects. Same
	 * finding text through the tool as from the service, and the cycle and connectivity
	 * fields are the service's rather than a restatement of them.
	 */
	@Test
	void aProcessTheValidatorRejectsIsReportedWithItsFindingsUnaltered() throws IOException {
		AgentSpec first = SampleAgentSpecs.explainer();
		AgentSpec second = new AgentSpec(new AgentSpecManifest("repeater", "1.0.0"), first.goal(), first.steps(),
				first.types());
		Path dir = this.workDir.resolve("colliding-process");
		this.processWriter.write(new ProcessSpec("colliding", List.of(first, second)), dir);

		var throughTool = this.processTool.validateProcess(dir.toString());
		var fromService = this.processValidator.validate(new ProcessSpecReader().read(dir));

		assertThat(throughTool.issues()).isEqualTo(fromService.issues());
		assertThat(throughTool.issues()).as("the collision is reported, not swallowed")
			.anyMatch((issue) -> issue.contains("Goal name") && issue.contains("repeater"));
		assertThat(throughTool.sharedTypes()).isEqualTo(fromService.sharedTypes());
		assertThat(throughTool.connected()).isEqualTo(fromService.connected());
		assertThat(throughTool.hasCycle()).isEqualTo(fromService.hasCycle());
	}

	/**
	 * The {@code singleStep} shape with an output type nothing declares — a
	 * {@code DANGLING_TYPE_REF} the validator raises on the on-disk pack.
	 * @return a pack that fails validation
	 */
	private static AgentSpec packWithDanglingOutputType() {
		GoalStepSpec goal = new GoalStepSpec("goal", "done", "A single-step agent spec",
				SampleAgentSpecs.BACKGROUND_MESSAGE, true);
		ActionStepSpec say = ActionStepSpec.action("say", "Say the input back", List.of(SampleAgentSpecs.USER_INPUT),
				"NoSuchType", "Repeat: {{userInput.content}}");
		return new AgentSpec(new AgentSpecManifest("dangling", "0.1.0"), goal, List.of(say), List.of());
	}

	/**
	 * The spec in the form the pack tool now takes: its own JSON, the same document the
	 * studio stores a spec as.
	 * @param spec the spec to hand to the tool
	 * @return the spec as JSON text
	 */
	private static String specJson(AgentSpec spec) throws JsonProcessingException {
		return new ObjectMapper().writeValueAsString(spec);
	}

	private static List<String> codes(List<ValidationIssue> issues) {
		return issues.stream().map(ValidationIssue::code).toList();
	}

}
