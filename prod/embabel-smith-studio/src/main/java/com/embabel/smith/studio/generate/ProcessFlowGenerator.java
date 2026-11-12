/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.generate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The generation core: a business-process blueprint in, a {@link AgentSpec} (goal +
 * action steps + intermediate types) out. It runs the {@code process_to_flow} prompt
 * against the model and parses the answer into the shared pack format, so the result is
 * exactly what the runtime ingests and the Compose editor renders. The model call is
 * behind the {@link FlowPromptCaller} seam; parsing and normalisation are pure and
 * offline-testable.
 *
 * @author Ivan Rodriguez
 */
public class ProcessFlowGenerator {

	/**
	 * Matches a Jinja2 template reference such as {@code {{userInput}}} or
	 * {@code {{userInput.content}}}.
	 */
	private static final Pattern PROMPT_REF = Pattern
		.compile("\\{\\{\\s*([a-zA-Z0-9_]+)(?:\\.([a-zA-Z0-9_]+))?\\s*\\}\\}");

	private final FlowPromptCaller caller;

	private final String template;

	private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
			false);

	public ProcessFlowGenerator(FlowPromptCaller caller, String template) {
		this.caller = caller;
		this.template = template;
	}

	/**
	 * Generates the agent spec for the given blueprint. Throws if the model answer is not
	 * parseable.
	 * @param blueprint the business-process description to generate from.
	 * @return the generated agent spec.
	 */
	public AgentSpec generate(String blueprint) {
		String user = "Below is a business-process description. Produce the agent spec JSON for the "
				+ "core end-to-end process.\n\n" + blueprint;
		String raw = this.caller.call(this.template, user);
		return parse(raw);
	}

	private AgentSpec parse(String raw) {
		String packed = extractJson(raw);
		AgentSpec pack;
		try {
			pack = this.json.readValue(packed, AgentSpec.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException(
					"The model did not return a valid agent spec JSON: " + ex.getMessage(), ex);
		}
		return normalize(pack);
	}

	/**
	 * Strips markdown fences and any surrounding prose, returning the outermost JSON
	 * object. The prompt asks for JSON only, but models occasionally wrap it; this makes
	 * parsing robust.
	 * @param raw the raw model answer.
	 * @return the outermost JSON object found in the answer.
	 */
	private String extractJson(String raw) {
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
	 * @param pack the parsed agent spec.
	 * @return the normalised agent spec.
	 */
	private AgentSpec normalize(AgentSpec pack) {
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
		List<ActionStepSpec> steps = pack.steps().stream().map(this::canonicalizePromptVars).toList();
		return new AgentSpec(manifest, goal, steps, pack.types());
	}

	/**
	 * Rewrites each {@code {{var}}} in a step's prompt to the canonical variable name the
	 * runtime binds — the decapitalized short-name of a declared input type (input type
	 * {@code TechnicalReport} is referenced as {@code {{technicalReport}}}). Models often
	 * emit snake_case ({@code {{technical_report}}}); this normalises any
	 * casing/underscore form of a declared input name to that canonical form so the pack
	 * passes structural validation. Unrecognised variables are left untouched.
	 * @param step the action step whose prompt is normalised.
	 * @return the step with canonical prompt vars, or the input when unchanged.
	 */
	private ActionStepSpec canonicalizePromptVars(ActionStepSpec step) {
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
	 * @param name the name to strip.
	 * @return the lower-cased, alphanumeric-only form of the name.
	 */
	private static String strip(String name) {
		return name.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	private static String slug(String name) {
		String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return s.isEmpty() ? "generated-process" : s;
	}

}
