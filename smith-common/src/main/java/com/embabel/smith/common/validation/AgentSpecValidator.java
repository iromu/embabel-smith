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

package com.embabel.smith.common.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;

/**
 * Structural validation of the agent spec format (see the {@code agent spec-format}
 * spec).
 *
 * <p>
 * Checks, in order:
 * <ol>
 * <li><b>Layout</b> — {@code manifest.yml}, {@code goal.yml}, a non-empty {@code steps/}
 * and a {@code types/} directory are all present.</li>
 * <li><b>Exported goal</b> — exactly one goal, and it is exported
 * ({@code export: true}).</li>
 * <li><b>Prompt references resolve</b> — every {@code {{var}}}/{@code {{var.prop}}} in an
 * action prompt is bound by one of that action's declared input types.</li>
 * <li><b>Type references resolve</b> — every input/output type name used by the goal or
 * any step is either framework-provided or declared in {@code types/}.</li>
 * </ol>
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecValidator {

	/**
	 * Matches a Jinja2 template reference such as {@code {{userInput.content}}} or
	 * {@code {{brief}}}.
	 */
	private static final Pattern PROMPT_REF = Pattern
		.compile("\\{\\{\\s*([a-zA-Z0-9_]+)(?:\\.([a-zA-Z0-9_]+))?\\s*\\}\\}");

	private final AgentSpecReader reader = new AgentSpecReader();

	private final Set<String> frameworkTypes;

	public AgentSpecValidator() {
		this(defaultFrameworkTypes());
	}

	public AgentSpecValidator(Set<String> frameworkTypes) {
		this.frameworkTypes = Set.copyOf(frameworkTypes);
	}

	/**
	 * The built-in Embabel types that need not be declared in {@code types/}.
	 * @return the built-in framework type names
	 */
	public static Set<String> defaultFrameworkTypes() {
		return Set.of("UserInput", "com.embabel.agent.domain.io.UserInput", "SystemInput",
				"com.embabel.agent.domain.io.SystemInput", "BackgroundMessage",
				"com.embabel.agent.spec.model.BackgroundMessage", "AssistantMessage",
				"com.embabel.chat.AssistantMessage");
	}

	/**
	 * Validates a agent spec on disk: filesystem layout plus the semantic checks.
	 * @param dir the directory containing the agent spec
	 * @return the validation outcome
	 */
	public ValidationResult validate(Path dir) {
		List<ValidationIssue> issues = new ArrayList<>(checkLayout(dir));

		AgentSpec pack;
		try {
			pack = this.reader.read(dir);
		}
		catch (IOException | RuntimeException ex) {
			issues.add(ValidationIssue.of("PARSE_ERROR", "Failed to parse agent spec: " + ex.getMessage()));
			return new ValidationResult(issues);
		}
		issues.addAll(validate(pack).issues());
		return new ValidationResult(issues);
	}

	/**
	 * Semantic checks on an in-memory pack (assumes the on-disk layout is already
	 * well-formed).
	 * @param pack the in-memory agent spec to check
	 * @return the validation outcome
	 */
	public ValidationResult validate(AgentSpec pack) {
		List<ValidationIssue> issues = new ArrayList<>();
		checkExportedGoal(pack, issues);

		Set<String> declaredTypes = declaredTypeNames(pack);
		for (ActionStepSpec step : pack.steps()) {
			checkTypeReferences(step, declaredTypes, issues);
			checkPromptReferences(step, pack, issues);
		}
		if (pack.goal() != null) {
			checkGoalTypeReference(pack.goal(), declaredTypes, issues);
		}
		return new ValidationResult(issues);
	}

	// --- layout ---------------------------------------------------------------

	private List<ValidationIssue> checkLayout(Path dir) {
		List<ValidationIssue> issues = new ArrayList<>();
		if (!Files.isRegularFile(dir.resolve("manifest.yml"))) {
			issues.add(ValidationIssue.of("LAYOUT_MISSING_MANIFEST", "manifest.yml is missing"));
		}
		if (!Files.isRegularFile(dir.resolve("goal.yml"))) {
			issues.add(ValidationIssue.of("LAYOUT_MISSING_GOAL", "goal.yml is missing (incomplete agent spec)"));
		}
		Path stepsDir = dir.resolve("steps");
		if (!Files.isDirectory(stepsDir)) {
			issues.add(ValidationIssue.of("LAYOUT_MISSING_STEPS", "steps/ directory is missing"));
		}
		else if (countYaml(stepsDir) == 0) {
			issues.add(ValidationIssue.of("LAYOUT_EMPTY_STEPS", "steps/ contains no action-step files"));
		}
		if (!Files.isDirectory(dir.resolve("types"))) {
			issues.add(ValidationIssue.of("LAYOUT_MISSING_TYPES", "types/ directory is missing"));
		}
		return issues;
	}

	private long countYaml(Path dir) {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile).filter((f) -> {
				String n = f.getFileName().toString();
				return n.endsWith(".yml") || n.endsWith(".yaml");
			}).count();
		}
		catch (IOException ex) {
			return 0;
		}
	}

	// --- exported goal --------------------------------------------------------

	private void checkExportedGoal(AgentSpec pack, List<ValidationIssue> issues) {
		GoalStepSpec goal = pack.goal();
		if (goal == null) {
			issues.add(ValidationIssue.of("GOAL_MISSING", "No goal defined; a agent spec must expose a goal"));
			return;
		}
		if (!goal.isExported()) {
			issues.add(ValidationIssue.of("GOAL_NOT_EXPORTED",
					"goal.yml does not declare 'export: true'; no callable goal is exposed", "goal.yml"));
		}
	}

	// --- type references ------------------------------------------------------

	private void checkTypeReferences(ActionStepSpec step, Set<String> declared, List<ValidationIssue> issues) {
		String location = "steps/" + step.name() + ".yml";
		for (String input : step.inputTypeNames()) {
			if (!resolves(input, declared)) {
				issues.add(ValidationIssue.of("DANGLING_TYPE_REF",
						"input type '" + input + "' is neither framework-provided nor declared in types/", location));
			}
		}
		if (step.outputTypeName() != null && !resolves(step.outputTypeName(), declared)) {
			issues.add(ValidationIssue.of("DANGLING_TYPE_REF",
					"output type '" + step.outputTypeName() + "' is neither framework-provided nor declared in types/",
					location));
		}
	}

	private void checkGoalTypeReference(GoalStepSpec goal, Set<String> declared, List<ValidationIssue> issues) {
		if (goal.outputTypeName() != null && !resolves(goal.outputTypeName(), declared)) {
			issues.add(ValidationIssue.of("DANGLING_TYPE_REF", "goal output type '" + goal.outputTypeName()
					+ "' is neither framework-provided nor declared in types/", "goal.yml"));
		}
	}

	private boolean resolves(String typeName, Set<String> declared) {
		if (typeName == null) {
			return false;
		}
		return this.frameworkTypes.contains(typeName) || declared.contains(typeName)
				|| declared.contains(shortName(typeName));
	}

	private Set<String> declaredTypeNames(AgentSpec pack) {
		Set<String> names = new HashSet<>();
		for (DynamicTypeDef type : pack.types()) {
			names.add(type.name());
		}
		return names;
	}

	// --- prompt references ----------------------------------------------------

	private void checkPromptReferences(ActionStepSpec step, AgentSpec pack, List<ValidationIssue> issues) {
		if (step.prompt() == null || step.prompt().isBlank()) {
			issues.add(ValidationIssue.of("PROMPT_MISSING", "action has no prompt", "steps/" + step.name() + ".yml"));
			return;
		}

		// Map the decapitalized input-type short name to the declared type (if any).
		Map<String, String> varToType = new LinkedHashMap<>();
		for (String input : step.inputTypeNames()) {
			varToType.put(decapitalize(shortName(input)), input);
		}

		Matcher matcher = PROMPT_REF.matcher(step.prompt());
		Set<String> seen = new HashSet<>();
		while (matcher.find()) {
			String var = matcher.group(1);
			String prop = matcher.group(2);
			if (seen.contains(var + ((prop != null) ? "." + prop : ""))) {
				continue;
			}
			seen.add(var + ((prop != null) ? "." + prop : ""));

			if (!varToType.containsKey(var)) {
				issues.add(ValidationIssue.of("PROMPT_UNDECLARED_INPUT",
						"prompt references '{{" + var + ((prop != null) ? "." + prop : "")
								+ "}}' which is not bound by any declared input type",
						"steps/" + step.name() + ".yml"));
				continue;
			}
			if (prop != null) {
				DynamicTypeDef type = pack.type(shortName(varToType.get(var)));
				if (type != null && !type.hasProperty(prop)) {
					issues.add(ValidationIssue.of(
							"PROMPT_UNKNOWN_PROPERTY", "prompt references property '" + prop
									+ "' which is not declared on type '" + type.name() + "'",
							"steps/" + step.name() + ".yml"));
				}
			}
		}
	}

	// --- helpers --------------------------------------------------------------

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

}
