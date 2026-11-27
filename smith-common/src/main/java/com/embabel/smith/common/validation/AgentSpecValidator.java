/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.Yaml;
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
 * <li><b>Skill folders</b> — an optional {@code skills/} directory, each of whose entries
 * is a skill folder carrying a {@code SKILL.md}; a folder without one is reported by name
 * as a skill the pack does not carry.</li>
 * <li><b>Exported goal</b> — exactly one goal, and it is exported
 * ({@code export: true}).</li>
 * <li><b>Prompt references resolve</b> — every {@code {{var}}}/{@code {{var.prop}}} in an
 * action prompt is bound by one of that action's declared input types.</li>
 * <li><b>Type references resolve</b> — every input/output type name used by the goal or
 * any step is either framework-provided or declared in {@code types/}.</li>
 * <li><b>Declared references resolve</b> — a step's {@code references} each name a skill
 * the pack (or the folder sharing its {@code skills/}) carries.</li>
 * <li><b>Declared inputs reach the model</b> — an action that declares input types must
 * interpolate at least one of them in its prompt, because a prompt is rendered as a
 * literal template and a variable it never names is never sent.</li>
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

	/** The directory an agent spec may carry its Agent Skills in; optional. */
	private static final String SKILLS_DIR = "skills";

	/**
	 * The file that makes a folder under {@code skills/} a skill rather than a directory.
	 */
	private static final String SKILL_FILE = "SKILL.md";

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
		Map<String, Path> carriedSkills = new LinkedHashMap<>();
		issues.addAll(checkSkillFolders(dir, carriedSkills));

		AgentSpec pack;
		try {
			pack = this.reader.read(dir);
		}
		catch (IOException | RuntimeException ex) {
			issues.add(ValidationIssue.of("PARSE_ERROR", "Failed to parse agent spec: " + ex.getMessage()));
			return new ValidationResult(issues);
		}
		issues.addAll(validate(pack).issues());
		issues.addAll(checkStepReferences(pack, carriedSkills));
		return new ValidationResult(issues);
	}

	/**
	 * The skills a pack can answer a {@code references} declaration with, as a map from
	 * the name each skill declares to the folder that carries it — so a caller can say
	 * which folder answered a reference.
	 * <p>
	 * The set is the pack's own {@code skills/} plus the {@code skills/} of its
	 * <em>immediate parent</em> — the directory several agent definitions share when they
	 * sit side by side. An ancestor further up is never consulted: the parent is also the
	 * source root when a source points at a folder of packs, so consulting higher
	 * directories would make a stray {@code skills/} silently global to every pack under
	 * it. Where both carry a name, the pack's own copy wins.
	 * @param packDir the pack's directory
	 * @return the carried skill names, each mapped to the folder serving it
	 */
	public Map<String, Path> carriedSkills(Path packDir) {
		Map<String, Path> carried = new LinkedHashMap<>();
		checkSkillFolders(packDir, carried);
		return carried;
	}

	/**
	 * Semantic checks on an in-memory pack (assumes the on-disk layout is already
	 * well-formed). A skill is a directory rather than a field, so the skill-folder and
	 * declared-reference checks are not part of this form — they run from
	 * {@link #validate(Path)}, which is the form that can see what the pack carries.
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
			checkDeclaredInputsReachPrompt(step, issues);
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

	// --- skill folders --------------------------------------------------------

	/**
	 * Checks the optional {@code skills/} directories and collects the names they carry.
	 * A pack with no such directory is unchanged: the directory is optional, and nothing
	 * here fires for it.
	 * @param packDir the pack's directory
	 * @param carried filled with each skill name the pack can answer, mapped to the
	 * folder serving it (the pack's own copy shadowing a shared one)
	 * @return the issues found, one per folder that is not a skill
	 */
	private List<ValidationIssue> checkSkillFolders(Path packDir, Map<String, Path> carried) {
		List<ValidationIssue> issues = new ArrayList<>();
		Map<String, Path> own = new LinkedHashMap<>();
		issues.addAll(scanSkills(packDir.resolve(SKILLS_DIR), SKILLS_DIR, own));
		Map<String, Path> shared = new LinkedHashMap<>();
		Path parent = packDir.getParent();
		if (parent != null) {
			issues.addAll(scanSkills(parent.resolve(SKILLS_DIR), "../" + SKILLS_DIR, shared));
		}
		carried.putAll(own);
		shared.forEach(carried::putIfAbsent);
		return issues;
	}

	/**
	 * Reads one {@code skills/} directory: every folder beneath it that carries a
	 * {@code SKILL.md} is a skill, and one that does not is reported by name rather than
	 * skipped. Upstream's {@code emit} drops an unmatched name with a silent
	 * {@code map.get}/null skip, so a folder this check did not report would surface as a
	 * capability that simply never existed.
	 * @param skillsDir the directory to read, which need not exist
	 * @param locationPrefix the location prefix the issues carry, so a reader can tell
	 * the pack's own skills from the shared folder's
	 * @param into filled with each skill's declared name mapped to its folder
	 * @return the issues found
	 */
	private List<ValidationIssue> scanSkills(Path skillsDir, String locationPrefix, Map<String, Path> into) {
		List<ValidationIssue> issues = new ArrayList<>();
		if (!Files.isDirectory(skillsDir)) {
			return issues;
		}
		List<Path> folders;
		try (Stream<Path> entries = Files.list(skillsDir)) {
			folders = entries.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList();
		}
		catch (IOException ex) {
			return issues;
		}
		for (Path folder : folders) {
			String folderName = folder.getFileName().toString();
			Path definition = folder.resolve(SKILL_FILE);
			if (!Files.isRegularFile(definition)) {
				issues.add(
						ValidationIssue.of("SKILL_MISSING_DEFINITION",
								"skills folder '" + folderName + "' carries no " + SKILL_FILE
										+ ", so the pack does not carry it as a skill",
								locationPrefix + "/" + folderName));
				continue;
			}
			into.put(declaredSkillName(definition, folderName), folder);
		}
		return issues;
	}

	/**
	 * The name a skill declares in its front matter, which is what a step's
	 * {@code references} entry names. The folder name answers when the file carries no
	 * front matter or no {@code name} key, so a skill is never unresolvable because of a
	 * formatting detail of its own document.
	 * @param skillMd the {@code SKILL.md} file
	 * @param folderName the folder's name, used as the fallback
	 * @return the name the skill answers to
	 */
	private String declaredSkillName(Path skillMd, String folderName) {
		try {
			String frontMatter = frontMatter(Files.readString(skillMd));
			if (frontMatter == null) {
				return folderName;
			}
			Map<?, ?> meta = Yaml.mapper().readValue(frontMatter, Map.class);
			Object name = meta.get("name");
			return (name instanceof String declared && !declared.isBlank()) ? declared.trim() : folderName;
		}
		catch (IOException | RuntimeException ex) {
			return folderName;
		}
	}

	/**
	 * The YAML block delimited by a leading pair of {@code ---} lines, or {@code null}
	 * when the document does not open with one.
	 * @param content the document's text
	 * @return the front matter, or {@code null}
	 */
	private static String frontMatter(String content) {
		String[] lines = content.split("\\R");
		if (lines.length < 2 || !lines[0].strip().equals("---")) {
			return null;
		}
		StringBuilder body = new StringBuilder();
		for (int i = 1; i < lines.length; i++) {
			if (lines[i].strip().equals("---")) {
				return body.toString();
			}
			body.append(lines[i]).append('\n');
		}
		return null;
	}

	// --- declared references --------------------------------------------------

	/**
	 * Refuses a declared reference that names no skill the pack carries: the reference
	 * counterpart of the rule that every type reference resolves. Upstream resolves an
	 * unmatched name with a {@code map.get} that yields {@code null} and skips it, so
	 * without this check the name is offered to no model and the run answers as though
	 * the capability never existed. One issue per declaring step, so a name misspelled in
	 * one step reports that step alone.
	 * @param pack the pack whose steps declare references
	 * @param carriedSkills the skills the pack can serve, from {@link #checkSkillFolders}
	 * @return the issues found
	 */
	private List<ValidationIssue> checkStepReferences(AgentSpec pack, Map<String, Path> carriedSkills) {
		List<ValidationIssue> issues = new ArrayList<>();
		String carried = carriedSkills.keySet().stream().map((n) -> "'" + n + "'").collect(Collectors.joining(", "));
		for (ActionStepSpec step : pack.steps()) {
			for (String reference : step.references()) {
				if (carriedSkills.containsKey(reference)) {
					continue;
				}
				issues.add(ValidationIssue.of("DANGLING_REFERENCE",
						"action '" + step.name() + "' declares reference '" + reference + "' which is "
								+ (carried.isEmpty() ? "not a skill the pack carries (it carries none)"
										: "not one of the skills it carries (" + carried + ")"),
						"steps/" + step.name() + ".yml"));
			}
		}
		return issues;
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

	/**
	 * Rejects a step that cannot see its own input. A prompt is rendered as a literal
	 * template, so an input type whose variable the prompt never names is only a
	 * blackboard binding — it is never sent to the model, and the step answers every
	 * distinct request with the same call. The check fires only when <em>none</em> of the
	 * declared inputs is named, so a step that ignores one of several inputs, or declares
	 * none at all, still validates.
	 * @param step the step to inspect
	 * @param issues the issues to add to when the step's prompt names none of its inputs
	 */
	private void checkDeclaredInputsReachPrompt(ActionStepSpec step, List<ValidationIssue> issues) {
		if (step.inputTypeNames().isEmpty() || step.prompt() == null || step.prompt().isBlank()) {
			// A step with no prompt is already reported by checkPromptReferences.
			return;
		}
		Set<String> namedVars = new HashSet<>();
		Matcher matcher = PROMPT_REF.matcher(step.prompt());
		while (matcher.find()) {
			namedVars.add(matcher.group(1));
		}
		for (String input : step.inputTypeNames()) {
			if (namedVars.contains(decapitalize(shortName(input)))) {
				return;
			}
		}
		issues.add(ValidationIssue.of("PROMPT_IGNORES_DECLARED_INPUT",
				"action '" + step.name() + "' declares input type(s) " + step.inputTypeNames()
						+ " but its prompt interpolates none of them, so the input never reaches the model",
				"steps/" + step.name() + ".yml"));
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
