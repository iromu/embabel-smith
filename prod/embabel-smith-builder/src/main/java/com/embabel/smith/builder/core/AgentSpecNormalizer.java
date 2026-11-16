/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;

/**
 * The format invariants a model's answer has to be brought into before it is an agent
 * spec: the JSON lifted out of whatever prose the model wrapped it in, a manifest when
 * the model omitted one, an exported goal, and prompt variables spelled the way the
 * runtime binds them.
 *
 * <p>
 * Shared by the single-shot generators — the one that emits a pack and the one that emits
 * a process — so a pack is normalised identically whichever candidate it came from.
 *
 * @author Ivan Rodriguez
 */
final class AgentSpecNormalizer {

	/**
	 * Matches a Jinja2 template reference such as {@code {{userInput}}} or
	 * {@code {{userInput.content}}}.
	 */
	private static final Pattern PROMPT_REF = Pattern
		.compile("\\{\\{\\s*([a-zA-Z0-9_]+)(?:\\.([a-zA-Z0-9_]+))?\\s*\\}\\}");

	private AgentSpecNormalizer() {
	}

	/**
	 * Strips markdown fences and any surrounding prose, returning the outermost JSON
	 * object. The prompt asks for JSON only, but models occasionally wrap it; this makes
	 * parsing robust.
	 * @param raw the raw model answer
	 * @return the outermost JSON object found in the answer
	 * @throws IllegalStateException when the answer carries no JSON object
	 */
	static String extractJson(String raw) {
		String s = (raw != null) ? raw.trim() : "";
		int fence = s.indexOf("```");
		if (fence >= 0) {
			String after = s.substring(fence + 3);
			int nl = after.indexOf('\n');
			if (nl >= 0) {
				after = after.substring(nl + 1);
			}
			int close = after.lastIndexOf("```");
			if (close >= 0) {
				after = after.substring(0, close);
			}
			s = after.trim();
		}
		int start = s.indexOf('{');
		int end = s.lastIndexOf('}');
		if (start < 0 || end < 0 || end < start) {
			throw new IllegalStateException("No JSON object found in the model answer");
		}
		return s.substring(start, end + 1);
	}

	/**
	 * Fills in the format invariants the model may omit: a manifest, an exported goal,
	 * and canonical prompt vars.
	 * @param pack the parsed agent spec
	 * @return the normalised agent spec
	 */
	static AgentSpec normalize(AgentSpec pack) {
		AgentSpecManifest manifest = pack.manifest();
		if (manifest == null || manifest.name() == null || manifest.name().isBlank()) {
			String name = (pack.goal() != null && pack.goal().name() != null) ? slug(pack.goal().name())
					: "generated-process";
			manifest = new AgentSpecManifest(name, "1.0.0");
		}
		GoalStepSpec goal = pack.goal();
		if (goal != null && !goal.isExported()) {
			goal = GoalStepSpec.exportedGoal(goal.name(), goal.description(), goal.outputTypeName());
		}
		List<ActionStepSpec> steps = pack.steps().stream().map(AgentSpecNormalizer::canonicalizePromptVars).toList();
		return new AgentSpec(manifest, goal, steps, pack.types());
	}

	/**
	 * Rewrites each {@code {{var}}} in a step's prompt to the canonical variable name the
	 * runtime binds — the decapitalized short-name of a declared input type (input type
	 * {@code TechnicalReport} is referenced as {@code {{technicalReport}}}). Models often
	 * emit snake_case ({@code {{technical_report}}}); this normalises any
	 * casing/underscore form of a declared input name to that canonical form so the pack
	 * passes structural validation. Unrecognised variables are left untouched.
	 * @param step the action step whose prompt is normalised
	 * @return the step with canonical prompt vars, or the input when unchanged
	 */
	private static ActionStepSpec canonicalizePromptVars(ActionStepSpec step) {
		if (step.prompt() == null || step.prompt().isBlank() || step.inputTypeNames().isEmpty()) {
			return step;
		}
		Map<String, String> canonical = new LinkedHashMap<>();
		for (String input : step.inputTypeNames()) {
			String shortName = shortName(input);
			canonical.put(strip(shortName), decapitalize(shortName));
		}
		Matcher matcher = PROMPT_REF.matcher(step.prompt());
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String var = matcher.group(1);
			String prop = matcher.group(2);
			String c = canonical.get(strip(var));
			String replacement = "{{" + ((c != null) ? c : var) + ((prop != null) ? "." + prop : "") + "}}";
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		String prompt = sb.toString();
		if (prompt.equals(step.prompt())) {
			return step;
		}
		return new ActionStepSpec(step.stepType(), step.name(), step.description(), step.inputTypeNames(),
				step.outputTypeName(), prompt, step.pre(), step.post(), step.cost(), step.value(), step.canRerun(),
				step.nullable(), step.toolGroups());
	}

	/**
	 * A file- and directory-safe form of a name.
	 * @param name the name to slug
	 * @return the lower-cased, hyphen-separated name
	 */
	static String slug(String name) {
		String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return s.isEmpty() ? "generated-process" : s;
	}

	private static String shortName(String typeName) {
		int idx = typeName.lastIndexOf('.');
		return (idx >= 0) ? typeName.substring(idx + 1) : typeName;
	}

	private static String decapitalize(String name) {
		if (name == null || name.isEmpty()) {
			return name;
		}
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

	/**
	 * Lower-cases and strips non-alphanumerics so {@code user_input} and
	 * {@code userInput} collide.
	 * @param name the name to strip
	 * @return the lower-cased, alphanumeric-only form of the name
	 */
	private static String strip(String name) {
		return name.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

}
