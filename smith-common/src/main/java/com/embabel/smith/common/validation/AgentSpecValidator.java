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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
 * the pack (or the folder sharing its {@code skills/}) carries, or a reference the pack's
 * {@code references.yml} declares. A reference declaration is checked for its kind, for
 * the path behind it, and for a name pair that sanitizes to one tool prefix.</li>
 * <li><b>Step keys are keys the format has</b> — a step file is also read as written, so
 * a key the format does not implement is named rather than dropped by a reader that
 * ignores unknown properties; a key the platform honors but the writer never emits is
 * named as one that will not survive a rewrite.</li>
 * <li><b>A references file outlives a deploy, not a rewrite</b> — a pack carrying
 * {@code references.yml} is told it will not survive the writer, which emits
 * {@code manifest.yml}, {@code goal.yml}, {@code steps/} and {@code types/} only.</li>
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

	/** The file a pack declares its non-skill references in; optional. */
	private static final String REFERENCES_FILE = "references.yml";

	/**
	 * The reference kinds the runtime can construct from pack files — a skills folder, a
	 * directory, a document, a short literal brief. Anything else is refused here rather
	 * than at run time, because a reference is built by code through the platform's own
	 * reference types and no recipe exists for a fourth kind.
	 */
	private static final Set<String> REFERENCE_KINDS = Set.of("skills", "directory", "document", "brief");

	/**
	 * Kinds that name a path that has to exist. A {@code brief} is literal text and needs
	 * no backing file, which is the whole point of that kind.
	 */
	private static final Set<String> PATH_BACKED_KINDS = Set.of("skills", "directory", "document");

	/**
	 * Kinds that end up listing tools to a step. A {@code skills} folder lists its
	 * activation tools and a {@code directory} its read tools; a {@code document}
	 * contributes its content and a {@code brief} its text, and neither hands out a tool
	 * for a facade to stand in place of. This is a different question from
	 * {@link #PATH_BACKED_KINDS}, which asks only whether a path is needed — a document
	 * needs one and lists nothing — so the two sets are not one set with a second name.
	 */
	private static final Set<String> TOOL_LISTING_KINDS = Set.of("skills", "directory");

	/**
	 * The keys an action-step file may carry: the components of {@link ActionStepSpec},
	 * which is the model the writer emits.
	 */
	private static final Set<String> DECLARED_ACTION_STEP_KEYS = Set.of("stepType", "name", "description",
			"inputTypeNames", "outputTypeName", "prompt", "pre", "post", "cost", "value", "canRerun", "nullable",
			"toolGroups", "references");

	/**
	 * The keys upstream's own action-step model carries, read off the 1.5.4
	 * {@code PromptedActionSpec} fields: the declared set plus {@code llm} (a per-step
	 * model pin), {@code tools} (a per-step tool list) and {@code export}. The runtime
	 * stages step YAML verbatim, so all three reach the upstream reader and are honored
	 * at deploy — but the writer serializes {@link ActionStepSpec}, so a save drops them.
	 * That is a different sentence from "not implemented", which is why it is its own
	 * check.
	 */
	private static final Set<String> UPSTREAM_ACTION_STEP_KEYS;
	static {
		Set<String> keys = new HashSet<>(DECLARED_ACTION_STEP_KEYS);
		keys.addAll(Set.of("llm", "tools", "export"));
		UPSTREAM_ACTION_STEP_KEYS = Set.copyOf(keys);
	}

	/** The keys a goal file may carry — the components of {@link GoalStepSpec}. */
	private static final Set<String> DECLARED_GOAL_KEYS = Set.of("stepType", "name", "description", "outputTypeName",
			"export");

	/** Upstream's {@code GoalSpec} carries no more fields than the declared goal set. */
	private static final Set<String> UPSTREAM_GOAL_KEYS = DECLARED_GOAL_KEYS;

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
		Map<String, String> declaredReferences = new LinkedHashMap<>();
		issues.addAll(checkPackReferences(dir, carriedSkills, declaredReferences));
		issues.addAll(checkReferencesFilePreserved(dir));
		issues.addAll(checkStepFileKeys(dir));

		AgentSpec pack;
		try {
			pack = this.reader.read(dir);
		}
		catch (IOException | RuntimeException ex) {
			issues.add(ValidationIssue.of("PARSE_ERROR", "Failed to parse agent spec: " + ex.getMessage()));
			return new ValidationResult(issues);
		}
		issues.addAll(validate(pack).issues());
		issues.addAll(checkStepReferences(pack, carriedSkills, declaredReferences.keySet()));
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

	// --- pack-carried references (references.yml) -----------------------------

	/**
	 * Reads the pack's optional {@code references.yml} — the references it carries that
	 * are not skills — and checks what it declares. Each entry is a name, a kind and a
	 * path, and the kinds are limited to the ones the runtime can construct through the
	 * platform's own reference types, so neither a property nor this file can nominate a
	 * class. An entry may add two switches, {@code scripts: true} and
	 * {@code unfolding: true}, each accepted because it names nothing beyond what the
	 * entry already gave the runtime — and each refused on a kind that cannot honour it,
	 * since a declaration the runtime would quietly ignore is worse than one it rejects.
	 * @param packDir the pack's directory
	 * @param carriedSkills the skill names the pack already answers to, which count
	 * towards a name being taken
	 * @param declaredNames filled with each declared name mapped to its kind, so the
	 * dangling check can tell a declared reference from a name nothing carries
	 * @return the issues found
	 */
	private List<ValidationIssue> checkPackReferences(Path packDir, Map<String, Path> carriedSkills,
			Map<String, String> declaredNames) {
		List<ValidationIssue> issues = new ArrayList<>();
		Path file = packDir.resolve(REFERENCES_FILE);
		if (!Files.isRegularFile(file)) {
			return issues;
		}
		List<Map<String, Object>> entries = new ArrayList<>();
		try {
			Map<?, ?> root = Yaml.mapper().readValue(Files.readString(file), Map.class);
			Object references = (root != null) ? root.get("references") : null;
			if (!(references instanceof List)) {
				return issues;
			}
			for (Object entry : (List<?>) references) {
				if (entry instanceof Map) {
					entries.add(asStringMap((Map<?, ?>) entry));
				}
			}
		}
		catch (IOException | RuntimeException ex) {
			issues.add(ValidationIssue.of("PARSE_ERROR",
					REFERENCES_FILE + " could not be read as a list of references: " + ex.getMessage(),
					REFERENCES_FILE));
			return issues;
		}

		Map<String, String> prefixOwners = new LinkedHashMap<>();
		carriedSkills.keySet().forEach((skill) -> prefixOwners.put(toolPrefix(skill), "skill '" + skill + "'"));
		for (Map<String, Object> entry : entries) {
			String name = stringValue(entry.get("name"));
			if (name == null || name.isBlank()) {
				issues.add(ValidationIssue.of("REFERENCE_UNNAMED",
						"a reference declared in " + REFERENCES_FILE + " carries no name, so no step can ask for it",
						REFERENCES_FILE));
				continue;
			}
			String kind = stringValue(entry.get("kind"));
			if (kind == null || !REFERENCE_KINDS.contains(kind)) {
				issues.add(ValidationIssue.of("REFERENCE_UNKNOWN_KIND",
						"reference '" + name + "' declares kind '" + kind
								+ "', which the runtime cannot construct. The kinds it can are " + REFERENCE_KINDS,
						REFERENCES_FILE));
			}
			if (kind != null && PATH_BACKED_KINDS.contains(kind)) {
				String path = stringValue(entry.get("path"));
				if (path == null || path.isBlank()) {
					issues.add(ValidationIssue.of("REFERENCE_MISSING_PATH",
							"reference '" + name + "' of kind '" + kind + "' declares no path to be constructed from",
							REFERENCES_FILE));
				}
				else if (!backingPathExists(packDir, path, kind)) {
					issues.add(ValidationIssue.of("REFERENCE_MISSING_PATH", "reference '" + name + "' of kind '" + kind
							+ "' points at '" + path + "', which the pack does not carry", REFERENCES_FILE));
				}
			}
			if (Boolean.TRUE.equals(entry.get("scripts")) && !"skills".equals(kind)) {
				issues.add(ValidationIssue.of(
						"REFERENCE_SCRIPTS_UNSUPPORTED", "reference '" + name + "' asks for scripts, which kind '"
								+ kind + "' does not offer; only a skills folder hands out script tools",
						REFERENCES_FILE));
			}
			if (Boolean.TRUE.equals(entry.get("unfolding")) && !TOOL_LISTING_KINDS.contains(kind)) {
				issues.add(ValidationIssue.of("REFERENCE_UNFOLDING_UNSUPPORTED", "reference '" + name
						+ "' asks for unfolding, which kind '" + kind
						+ "' does not offer; a facade lists one tool in place of the tools a reference would list, and "
						+ "this kind lists none", REFERENCES_FILE));
			}

			String prefix = toolPrefix(name);
			if (prefix.isBlank() || prefix.chars().allMatch((c) -> c == '_')) {
				issues.add(ValidationIssue.of("REFERENCE_UNUSABLE_NAME", "reference name '" + name + "' sanitizes to '"
						+ prefix + "', which cannot become a tool prefix", REFERENCES_FILE));
			}
			String owner = prefixOwners.get(prefix);
			if (owner != null) {
				issues
					.add(ValidationIssue.of("REFERENCE_PREFIX_COLLISION",
							"reference '" + name + "' and " + owner + " both sanitize to the tool prefix '" + prefix
									+ "', so one reference's tools would silently overwrite the other's",
							REFERENCES_FILE));
			}
			prefixOwners.put(prefix, "reference '" + name + "'");
			declaredNames.put(name, (kind != null) ? kind : "");
		}
		return issues;
	}

	/**
	 * Whether the path a path-backed reference names exists in the shape that kind needs:
	 * a directory for a skills folder or a directory, a file for a document.
	 * @param packDir the pack's directory, which the path is resolved against
	 * @param path the declared path
	 * @param kind the declared kind
	 * @return true when the backing path is there in the right shape
	 */
	private boolean backingPathExists(Path packDir, String path, String kind) {
		Path target = packDir.resolve(path).normalize();
		if ("document".equals(kind)) {
			return Files.isRegularFile(target);
		}
		return Files.isDirectory(target);
	}

	private static String stringValue(Object value) {
		return (value instanceof String text) ? text : null;
	}

	private static Map<String, Object> asStringMap(Map<?, ?> entry) {
		Map<String, Object> out = new LinkedHashMap<>();
		entry.forEach((k, v) -> out.put(String.valueOf(k), v));
		return out;
	}

	/**
	 * The tool prefix the platform derives from a reference name: every non-alphanumeric
	 * character becomes an underscore and the result is lowercased, so {@code repo-notes}
	 * and {@code repo.notes} are one prefix to the platform — which is how one
	 * reference's tools silently overwrite another's.
	 * @param referenceName the name as declared
	 * @return the prefix that name becomes on the wire
	 */
	static String toolPrefix(String referenceName) {
		return referenceName.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase(Locale.ROOT);
	}

	/**
	 * Reports that a pack carrying {@code references.yml} would lose it on a rewrite.
	 * <p>
	 * The choice here is to report the loss rather than prevent it. The model has no
	 * references-file component — the reader of this file is the runtime's, not the
	 * model's — so a writer passthrough would mean teaching {@code AgentSpecWriter} a
	 * file it cannot represent, which is the round-trip the format's file map rules out.
	 * So the file survives a deploy and dies at the next save: {@code AgentSpecWriter}
	 * writes {@code manifest.yml}, {@code goal.yml}, {@code steps/} and {@code types/}
	 * and nothing else, so a studio save or a builder repair of a pack that references
	 * its own documents ships a pack with those references gone. That is why this is a
	 * note and not a refusal — the pack deploys, and the loss belongs to whoever rewrites
	 * it, so it has to be visible before they do.
	 * <p>
	 * It fires on the file being there rather than on a step declaring one of its
	 * entries. That is the choice, not the oversight: a rewrite drops the file whether or
	 * not anything names it, so a rule that waited for a declaration would under-report —
	 * the entries nobody declares yet still vanish, and whoever adds the declaration
	 * later inherits a loss no one flagged. Saying the file exists is the honest floor;
	 * saying it matters only once it is used would read as a licence to drop it until
	 * then.
	 * @param packDir the pack's directory
	 * @return one note when the pack carries the file, otherwise nothing
	 */
	private List<ValidationIssue> checkReferencesFilePreserved(Path packDir) {
		if (!Files.isRegularFile(packDir.resolve(REFERENCES_FILE))) {
			return List.of();
		}
		return List.of(ValidationIssue.advisory("REFERENCES_FILE_NOT_PRESERVED_ON_REWRITE",
				REFERENCES_FILE + " has no representation in the model the writer emits, so a rewrite of this pack"
						+ " (a studio save, a builder repair) drops it and the references it declares with it",
				REFERENCES_FILE));
	}

	// --- step file keys -------------------------------------------------------

	/**
	 * Reads the step files as written rather than as the model reads them, because the
	 * reader ignores a key it does not know: the runtime stages step YAML verbatim, so an
	 * invented key reaches the platform, is dropped there, and then reads as a control
	 * that was configured. A safety wish written as a step key is the dangerous case of
	 * this — a {@code requires-approval} that does nothing is worse than one that is
	 * absent.
	 * <p>
	 * Two different sentences come out of this pass. A key nothing in the format
	 * implements is reported as unrecognized and refuses the pack. A key the upstream
	 * model implements but the writer never emits ({@code llm}, {@code tools},
	 * {@code export}) is reported as a key that will not survive a rewrite: it works at
	 * deploy, so the pack stays deployable, but a studio save or a builder repair loses
	 * it.
	 * @param dir the pack's directory
	 * @return the issues found
	 */
	private List<ValidationIssue> checkStepFileKeys(Path dir) {
		List<ValidationIssue> issues = new ArrayList<>();
		issues.addAll(checkFileKeys(dir.resolve("goal.yml"), DECLARED_GOAL_KEYS, UPSTREAM_GOAL_KEYS, "goal.yml", true));
		Path stepsDir = dir.resolve("steps");
		if (!Files.isDirectory(stepsDir)) {
			return issues;
		}
		List<Path> stepFiles;
		try (Stream<Path> files = Files.list(stepsDir)) {
			stepFiles = files.filter(Files::isRegularFile)
				.filter(this::isYamlFile)
				.sorted(Comparator.comparing(Path::toString))
				.toList();
		}
		catch (IOException ex) {
			return issues;
		}
		for (Path stepFile : stepFiles) {
			issues.addAll(checkFileKeys(stepFile, DECLARED_ACTION_STEP_KEYS, UPSTREAM_ACTION_STEP_KEYS,
					"steps/" + stepFile.getFileName(), false));
		}
		return issues;
	}

	/**
	 * Compares the keys of one step file against what the format implements.
	 * @param file the file as written
	 * @param declaredKeys the keys the writer emits for this kind of step
	 * @param upstreamKeys the keys the upstream reader accepts for this kind of step
	 * @param location the location the issues carry
	 * @param goalFile whether this is the goal file, whose extra keys are inert rather
	 * than honored
	 * @return the issues found
	 */
	private List<ValidationIssue> checkFileKeys(Path file, Set<String> declaredKeys, Set<String> upstreamKeys,
			String location, boolean goalFile) {
		List<ValidationIssue> issues = new ArrayList<>();
		if (!Files.isRegularFile(file)) {
			return issues;
		}
		Map<String, Object> written;
		try {
			written = Yaml.mapper().readValue(Files.readString(file), Map.class);
		}
		catch (IOException | RuntimeException ex) {
			// Unparseable, so keyless: the parse failure is the finding, not this one.
			return issues;
		}
		if (written == null) {
			return issues;
		}
		for (String key : new TreeSet<>(written.keySet())) {
			if (declaredKeys.contains(key)) {
				continue;
			}
			if (goalFile && DECLARED_ACTION_STEP_KEYS.contains(key)) {
				// A goal contributes no model call of its own, so an action key written
				// here
				// is read by nothing. Upstream's GoalSpec has no references field at all,
				// which makes a references declaration on a goal inert rather than
				// invalid:
				// the pack still deploys, and the point of saying it is that the author
				// does
				// not have to discover the silence at run time.
				issues.add(ValidationIssue.advisory(
						("references".equals(key)) ? "REFERENCES_INERT_ON_GOAL" : "KEY_INERT_ON_GOAL",
						"goal.yml declares '" + key
								+ "', which only an action step honors; a goal contributes no model call of its"
								+ " own, so the declaration is ignored",
						location));
				continue;
			}
			if (upstreamKeys.contains(key)) {
				issues.add(ValidationIssue.advisory("KEY_NOT_PRESERVED_ON_REWRITE", "'" + key
						+ "' is honored by the upstream reader through the runtime's verbatim staging but is not part of"
						+ " the model the writer emits, so it will not survive a rewrite of this pack", location));
				continue;
			}
			issues.add(ValidationIssue.of("UNKNOWN_STEP_KEY", "'" + key
					+ "' is not a key the agent spec format implements; the reader ignores it, so it reads as configured"
					+ " while doing nothing", location));
		}
		return issues;
	}

	private boolean isYamlFile(Path file) {
		String name = file.getFileName().toString();
		return name.endsWith(".yml") || name.endsWith(".yaml");
	}

	// --- declared references --------------------------------------------------

	/**
	 * Refuses a declared reference that names nothing the pack carries: the reference
	 * counterpart of the rule that every type reference resolves. What the pack carries
	 * is its own skills, the immediate parent's skills, and the names declared in
	 * {@value #REFERENCES_FILE}. Upstream resolves an unmatched name with a
	 * {@code map.get} that yields {@code null} and skips it, so without this check the
	 * name is offered to no model and the run answers as though the capability never
	 * existed. One issue per declaring step, so a name misspelled in one step reports
	 * that step alone.
	 * @param pack the pack whose steps declare references
	 * @param carriedSkills the skills the pack can serve, from {@link #checkSkillFolders}
	 * @param declaredReferences the names the pack's {@value #REFERENCES_FILE} declares
	 * @return the issues found
	 */
	private List<ValidationIssue> checkStepReferences(AgentSpec pack, Map<String, Path> carriedSkills,
			Set<String> declaredReferences) {
		List<ValidationIssue> issues = new ArrayList<>();
		String carried = carriedSkills.keySet().stream().map((n) -> "'" + n + "'").collect(Collectors.joining(", "));
		String declared = declaredReferences.stream().map((n) -> "'" + n + "'").collect(Collectors.joining(", "));
		String whatItCarries = carried + ((carried.isEmpty() || declared.isEmpty()) ? "" : ", ") + declared;
		for (ActionStepSpec step : pack.steps()) {
			for (String reference : step.references()) {
				if (carriedSkills.containsKey(reference) || declaredReferences.contains(reference)) {
					continue;
				}
				issues.add(ValidationIssue.of("DANGLING_REFERENCE",
						"action '" + step.name() + "' declares reference '" + reference + "' which is "
								+ (whatItCarries.isEmpty() ? "not something the pack carries (it carries none)"
										: "not one of the references it carries (" + whatItCarries + ")"),
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
