/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.smith.builder.core.AgentSpecNormalizer;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationIssue;
import com.embabel.smith.common.validation.ValidationResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link AgentSpecValidator} as a tool a pack's own agent can call, so a drafting step
 * can read the findings against its draft and re-draft, instead of relying on the
 * numbered repair stages {@code BuilderService} runs today.
 *
 * <p>
 * It adds nothing to the service. Every finding it names is one
 * {@link AgentSpecValidator} raised, with the validator's own code, message and offending
 * file, so no threshold, verdict, policy or publish decision lives in this class.
 * Deciding whether a finding blocks anything is the job of the gates that already exist
 * ({@code GateChain}, {@code AgentSpecSecurityScanner}).
 *
 * <p>
 * <b>What it validates is the spec the build would end up with.</b> A build does not
 * check the model's raw answer: {@code eval.PackDrivenAgentSpecGenerator} and
 * {@code core.SingleShotAgentSpecGenerator} run {@link AgentSpecNormalizer#normalize} on
 * the answer, and {@code BuilderService} then validates the result through
 * {@code BuilderAgent.validateDraft}. Validating the un-normalized text instead tells the
 * model to hand-edit findings the java path patches by itself — a goal the draft left
 * unexported, a prompt variable spelled {@code user_input} for an input declared
 * {@code UserInput} — which is how one measured {@code --flow pack} run spent 19 calls on
 * an input that never got an answer. So the findings come from the normalized spec, and
 * {@link ValidationReport#patchedByBuild()} carries the findings the draft had and the
 * build removes: named rather than silently dropped, so the model learns the build fixes
 * them and is not handed homework it does not have.
 *
 * <p>
 * <b>How these methods reach a model.</b> The {@code LlmTool} annotation does not place a
 * method in a named tool group in Embabel 1.5.4: it carries only {@code description},
 * {@code name}, {@code returnDirect}, {@code category} and {@code metadata} — no group —
 * and the platform's {@code ToolGroup} annotation is referenced by no other class in the
 * 1.5.4 jars, so nothing maps it to a role. A step's {@code toolGroups} entry is resolved
 * only against {@code RegistryToolGroupResolver}, whose registry is the set of
 * {@code ToolGroup} beans Spring can see — so this validator reaches a step because
 * {@link ValidationToolGroup} publishes one whose role is {@code validate}, the name the
 * builder packs already declare. Handing an instance to {@code Tool.fromInstance(Object)}
 * and passing the list down the {@code tools} channel of
 * {@code StepSpecAgentScopeBuilder} is not that route: a step selects from that list by a
 * {@code tools:} name {@code ActionStepSpec} cannot express, so a tool wired there is
 * offered to no model.
 *
 * <p>
 * A {@code ToolGroup} bean <em>can</em> be authored in Java, which an earlier version of
 * this paragraph denied. {@code ToolGroupMetadata}'s factories are Kotlin companions
 * whose JVM names contain a hyphen ({@code invoke-bYD-3ao}) and its {@code getVersion}
 * accessor is mangled the same way, so {@code javac} cannot <em>name</em> them — but
 * names are not the only handle reflection has, and {@link ValidationToolGroup} finds the
 * companion's five-argument factory by <em>signature</em> through {@code getMethods()}
 * before handing the result to {@code ToolGroup.Companion.ofTools}. POC 7's
 * {@code ShellToolGroupFactory} is this repository's counterexample to the denial, and it
 * resolved at runtime.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecValidationTool {

	private static final ObjectMapper JSON = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private final AgentSpecValidator validator = new AgentSpecValidator();

	/**
	 * Validates the agent spec a drafting step is about to return, reporting what the
	 * build that would consume it cannot fix. The argument is the spec rather than a
	 * location because that is the only form a draft has while it is still the model's
	 * pending answer — there is no directory to point at yet — and it costs the one check
	 * a directory is the subject of: filesystem layout. Everything else the validator
	 * reports is a property of the spec text, which is what
	 * {@code BuilderAgent.validateDraft} checks for the Java flow — on the normalized
	 * spec, as the build does.
	 * @param specJson the spec as JSON text; markdown fences and prose around it are
	 * tolerated, as every generator's parse tolerates them
	 * @return the findings the build leaves in place, and the findings its normalizer
	 * removes
	 */
	@LlmTool(description = "Validate an agent spec and report its findings (exported goal, "
			+ "unresolved type references, prompt variables that no declared input binds). The argument is the "
			+ "spec JSON you are about to return, not a path to a pack on disk. Returns nothing when the spec "
			+ "is well-formed.")
	public ValidationReport validatePack(String specJson) {
		AgentSpec draft;
		try {
			draft = JSON.readValue(AgentSpecNormalizer.extractJson(specJson), AgentSpec.class);
		}
		catch (IOException | RuntimeException ex) {
			// The finding the directory-taking entry point reports for the same
			// situation, worded the same way, so a malformed answer reads alike
			// whichever way it arrived. Nothing of it reaches a normalizer, so nothing is
			// patched.
			return new ValidationReport(
					List.of(ValidationIssue.of("PARSE_ERROR", "Failed to parse agent spec: " + ex.getMessage())),
					List.of());
		}
		ValidationResult asDrafted = this.validator.validate(draft);
		ValidationResult asBuilt = this.validator.validate(AgentSpecNormalizer.normalize(draft));
		return new ValidationReport(asBuilt.issues(), patchedByBuild(asDrafted, asBuilt));
	}

	/**
	 * The findings the build removes: raised against the draft, gone against the spec its
	 * normalizer produces. Matched on code, message and file rather than on code alone,
	 * so a step carrying both a variable the normalizer respells and one it cannot
	 * recognise does not have the second excused by the first.
	 * @param asDrafted the validator's findings against the text the model wrote
	 * @param asBuilt the validator's findings against the normalized spec the build would
	 * ship
	 * @return the findings present only in the draft
	 */
	private static List<ValidationIssue> patchedByBuild(ValidationResult asDrafted, ValidationResult asBuilt) {
		Set<String> stillThere = asBuilt.issues()
			.stream()
			.map(AgentSpecValidationTool::key)
			.collect(Collectors.toSet());
		return asDrafted.issues().stream().filter((issue) -> !stillThere.contains(key(issue))).toList();
	}

	private static String key(ValidationIssue issue) {
		return issue.code() + " " + issue.message() + " " + issue.location();
	}

	/**
	 * What a draft looks like to the build that would consume it.
	 *
	 * @param issues the findings that survive the build's own normalization — the draft
	 * as it would ship, which is the set {@code BuilderAgent.validateDraft} would reject
	 * it for
	 * @param patchedByBuild the findings the draft carries but
	 * {@code AgentSpecNormalizer} removes before the build validates it, so the tool can
	 * name them as the build's work rather than the model's
	 */
	public record ValidationReport(List<ValidationIssue> issues, List<ValidationIssue> patchedByBuild) {

		public boolean isValid() {
			return this.issues.isEmpty();
		}
	}

}
