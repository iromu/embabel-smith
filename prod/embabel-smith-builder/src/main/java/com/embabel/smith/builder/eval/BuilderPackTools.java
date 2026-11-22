/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.smith.builder.tools.AgentSpecValidationTool;
import com.embabel.smith.common.validation.ValidationIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The builder's validator as a callable tool, so a step of a builder pack can call
 * {@link AgentSpecValidationTool} and re-draft against the findings (design D1).
 *
 * <p>
 * <b>How it reaches a step.</b> Through a group, not through a deploy.
 * {@code tools/ValidationToolGroup} publishes the tool made here as the {@code validate}
 * {@code ToolGroup} bean, which is the role the builder packs' steps already declare in
 * {@code toolGroups}, and the platform resolves that role at prompt time. The deploy's
 * own tool list is empty by design: {@code PromptedActionSpec.emit} selects from it by
 * the step's {@code tools:} name list, which {@code ActionStepSpec} cannot express, so a
 * tool handed over that way is unselectable by a pack. That was this class's route —
 * reach as the driver's property — and a live run measured it: the pack declared
 * {@code validate}, the platform warned it resolved nothing, and the tool loop started
 * empty.
 *
 * <p>
 * <b>Why an explicit {@code Tool.of} body and not {@code Tool.fromInstance}.</b>
 * {@code fromInstance} would find the annotated method: {@code MethodToolFactory} filters
 * {@code declaredMethods} by the {@code LlmTool} annotation and throws when none match,
 * so the adapter yields one tool. What it cannot do is name the argument.
 * {@code JavaMethodTool} takes the parameter name from
 * {@code java.lang.reflect.Parameter#getName()}, and neither the root nor the builder
 * {@code pom.xml} passes {@code -parameters}, so javac leaves {@code arg0} in the class
 * file: the schema the model is given would read {@code arg0}, and because the same name
 * is the key the call site reads back, an answer naming the argument anything else would
 * bind nothing and reach the validator with an empty argument. The schema below is
 * authored here instead, so the name the model sees, the name the call binds, and the
 * name in the adapter's signature are one string — {@link #SPEC_JSON} — and no build flag
 * decides it.
 *
 * <p>
 * <b>Why the argument is a spec and not a directory.</b> It used to be a path, and a live
 * run measured what that costs: a drafting step has no pack on disk to point at — the
 * thing it wants checked is its own pending answer — so every call came back with the
 * layout finding a directory without a manifest produces, and the tool-loop budget went
 * into guessing paths. The spec-taking overload of the validator is the one
 * {@code BuilderAgent.validateDraft} runs for the Java flow, so passing the text is what
 * makes the same checks reachable from a pack.
 *
 * <p>
 * <b>What a finding is worth.</b> The text is split in two: first the findings the build
 * cannot fix, then the ones its own {@code AgentSpecNormalizer} removes before validation
 * — a goal the draft left unexported, a prompt variable spelled in another case —
 * labelled as the build's work. Reporting the raw draft's findings instead, which this
 * did, sends a model to hand-edit what the java path patches by itself, and the tool loop
 * spends calls on it: one measured {@code --flow pack} input took 19 calls and never got
 * an answer.
 *
 * @author Ivan Rodriguez
 */
public final class BuilderPackTools {

	/**
	 * The tool name a model may call, kept equal to the annotated method it wraps
	 * ({@code AgentSpecValidationTool.validatePack}).
	 */
	public static final String VALIDATE_PACK = "validatePack";

	/**
	 * The single argument's name in the schema the model is given, and the key the call
	 * reads back: the spec JSON the step is about to return.
	 */
	public static final String SPEC_JSON = "specJson";

	/**
	 * The tool description the model reads, mirroring the adapter's own {@code @LlmTool}.
	 */
	private static final String DESCRIPTION = "Validate an agent spec and report its findings (exported goal, "
			+ "unresolved type references, prompt variables that no declared input binds). The argument is the "
			+ "spec JSON you are about to return, not a path to a pack on disk. Returns nothing when the spec "
			+ "is well-formed.";

	/** The parameter description the model reads for {@link #SPEC_JSON}. */
	private static final String SPEC_JSON_DESCRIPTION = "The agent spec this step is about to return, as its JSON "
			+ "document (manifest, goal, steps, types)";

	private static final ObjectMapper JSON = new ObjectMapper();

	private BuilderPackTools() {
	}

	/**
	 * The validator as a callable tool: {@link #VALIDATE_PACK} with one named string
	 * argument, whose body calls {@link AgentSpecValidationTool} and reports what the
	 * validator found, split by what the build can do about each finding.
	 * @return the tool the {@code validate} group carries
	 */
	public static Tool validationTool() {
		return Tool.Companion.of(VALIDATE_PACK, DESCRIPTION,
				Tool.InputSchema.of(Tool.Parameter.string(SPEC_JSON, SPEC_JSON_DESCRIPTION, true)),
				Tool.Metadata.DEFAULT, (Tool.Function) (arguments) -> Tool.Result.text(findings(arguments)));
	}

	/**
	 * The tool's body: the validator's findings for the spec the model sent, rendered as
	 * text, split by what the build can do about each. The findings are the validator's
	 * own — no threshold, verdict or decision is added — taken against the spec the build
	 * would end up with, and anything its normalizer patches is named as the build's work
	 * rather than left out or passed off as the model's.
	 * @param arguments the tool-call arguments as the model sent them
	 * @return the findings the model has to fix, then the ones the build fixes itself, or
	 * one line when there are neither
	 */
	static String findings(String arguments) {
		String spec = specJson(arguments);
		if (spec.isBlank()) {
			return "validatePack needs the '" + SPEC_JSON + "' argument: the agent spec JSON this step is about "
					+ "to return.";
		}
		AgentSpecValidationTool.ValidationReport report = new AgentSpecValidationTool().validatePack(spec);
		if (report.issues().isEmpty() && report.patchedByBuild().isEmpty()) {
			return "No findings: the agent spec is well-formed.";
		}
		List<String> lines = new ArrayList<>();
		if (report.issues().isEmpty()) {
			lines.add("No findings you need to fix.");
		}
		else {
			report.issues().forEach((issue) -> lines.add(describe(issue)));
		}
		if (!report.patchedByBuild().isEmpty()) {
			lines.add("The build's normalizer fixes these itself, so no edit is needed for them:");
			report.patchedByBuild().forEach((issue) -> lines.add("  " + describe(issue)));
		}
		return String.join("\n", lines);
	}

	/**
	 * The spec the model meant. The platform hands a tool the argument string as it
	 * arrived, so a schema-conforming call is the JSON object {@code {"specJson":
	 * "..."}}; a call that named nothing but carries a spec anyway — the whole argument
	 * list is the spec, or the argument is the spec object rather than a string quoting
	 * it — is read from the raw text, because a model that has the spec in front of it
	 * usually sends it rather than naming it.
	 * @param arguments the raw tool-call arguments, possibly null
	 * @return the spec JSON, blank when the call carried none
	 */
	static String specJson(String arguments) {
		String raw = (arguments != null) ? arguments.trim() : "";
		if (raw.startsWith("{")) {
			try {
				JsonNode named = JSON.readTree(raw).get(SPEC_JSON);
				if (named != null && named.isTextual()) {
					return named.asText().trim();
				}
				return looksLikeSpec(raw) ? raw : "";
			}
			catch (Exception ignored) {
				// Brace-led but not JSON: there is no spec to read here, so the call
				// carried no argument rather than one written with a brace.
				return "";
			}
		}
		return raw.replaceAll("^\"|\"$", "");
	}

	/**
	 * Whether a JSON object with no {@link #SPEC_JSON} key on it is nonetheless a spec.
	 * The two keys a drafting answer always carries, so a stray object — a path, a bundle
	 * of unrelated arguments — is not mistaken for one.
	 * @param raw the raw tool-call arguments
	 * @return whether the text reads as a spec document
	 */
	private static boolean looksLikeSpec(String raw) {
		return raw.contains("\"steps\"") || raw.contains("\"goal\"");
	}

	private static String describe(ValidationIssue issue) {
		return issue.code() + " — " + issue.message()
				+ ((issue.location() == null) ? "" : " [" + issue.location() + "]");
	}

}
