/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.SuccessDetail;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;
import org.iromu.embabel.smith.skillevaluator.util.ScanPaths;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

/**
 * Validates skill schema and repository structure per the SkillEvaluator spec.
 *
 * <p>
 * Checks: frontmatter schema, folder hierarchy, naming conventions, directory-name
 * consistency, required/forbidden fields.
 *
 * <p>
 * Port of {@code validators/schema.py}. Author attribution and {@code metadata.id} are
 * policy-driven: the active {@link SchemaPolicy} decides whether a missing author, a name
 * without an email, or a missing ID blocks, and may restrict the author email domain.
 * Malformed or unsafe author values and invalid IDs are always reported.
 */
public class SchemaValidator extends ValidatorBase {

	/** Same shape as the Python FRONTMATTER_PATTERN (DOTALL). */
	private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
			"^\\ufeff?---[^\\S\\r\\n]*\\r?\\n(.*?)\\r?\\n---[^\\S\\r\\n]*(?=\\r?\\n|\\z)(?:\\r?\\n)?(.*)",
			Pattern.DOTALL);

	private static final Pattern TRIGGER_PATTERN = Pattern.compile(Constants.DESCRIPTION_TRIGGER.pattern(),
			Pattern.CASE_INSENSITIVE);

	private static final Pattern BODY_HEADING = Pattern.compile("^# .+", Pattern.MULTILINE);

	/** Longest accepted {@code metadata.author} value. */
	private static final int AUTHOR_MAX_LENGTH = SchemaAuthor.AUTHOR_MAX_LENGTH;

	/**
	 * Directories whose contents are not shipped with the skill, so never treated as
	 * nested manifests.
	 */
	private static final Set<String> SKILL_NOT_SHIPPED_DIRS = Set.of("__pycache__", ".git", ".venv", "node_modules");

	/** Environment variable extending the allowed skill-root subdirectories. */
	private static final String SCHEMA_ALLOWED_DIRS_ENV = "SKILLEVALUATOR_SCHEMA_ALLOWED_DIRS";

	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private static final String CATEGORY = "SCHEMA";

	private final SchemaPolicy policy;

	public SchemaValidator() {
		this(SchemaPolicy.defaultPolicy());
	}

	SchemaValidator(SchemaPolicy policy) {
		this.policy = policy;
	}

	@Override
	public String getName() {
		return "Schema & Repository Governance";
	}

	@Override
	public String getDescription() {
		return "Validate SKILL.md frontmatter and repository structure";
	}

	@Override
	public ValidationResult validate(Path skillPath) {
		if (isSkillDirectory(skillPath)) {
			return validateSingleSkill(skillPath);
		}

		ValidationResult result = new ValidationResult();
		List<Path> skillDirs = findAllSkills(skillPath);
		if (skillDirs.isEmpty()) {
			result.addFinding(finding("skill_discovery", Severity.HIGH, "No skills found in target directory",
					skillPath.toString(), null, null,
					"Ensure SKILL.md files exist in skill directories under skills/ or team-skills/", null));
			return result;
		}

		result.getSummary().setFilesScanned(skillDirs.size());
		result.addSuccess("skill_discovery", "Found " + skillDirs.size() + " skill(s) in folder",
				Map.of("skill_count", skillDirs.size()));
		result.merge(validateFolderCompliance(skillPath));

		Map<String, List<Path>> declaredIds = new LinkedHashMap<>();
		for (Path skillDir : skillDirs) {
			ValidationResult skillResult = validateSingleSkill(skillDir);
			Object metadataId = skillResult.getMetadata().get("metadata_id");
			if (metadataId instanceof String id) {
				declaredIds.computeIfAbsent(id, key -> new ArrayList<>()).add(skillDir);
			}
			String skillName = skillDir.getFileName().toString();
			if (skillResult.isPassed()) {
				List<Map<String, Object>> checkDetails = new ArrayList<>();
				for (SuccessDetail detail : skillResult.getSuccessDetails()) {
					checkDetails.add(Map.of("name", detail.getCheckName(), "description", detail.getMessage(),
							"metadata", detail.getMetadata()));
				}
				int advisories = skillResult.getFindings().size();
				Map<String, Object> rollup = new LinkedHashMap<>();
				rollup.put("checks", checkDetails);
				rollup.put("total_checks", checkDetails.size());
				rollup.put("advisory_findings", advisories);
				result.addSuccess(skillName,
						advisories == 0 ? "All schema and structure checks passed"
								: "No blocking schema or structure findings (" + advisories + " advisory finding(s))",
						rollup);
				// A passing child can still carry policy advisories. Keep them named
				// after the skill they came from, without merging its successes a
				// second time; the roll-up above only says that nothing blocked.
				result.mergeAdvisoriesWithPrefix(skillResult, skillName);
			}
			else {
				result.mergeWithPrefix(skillResult, skillName);
				// mergeWithPrefix copies errors/warnings but not severity counters.
				for (Finding finding : skillResult.getFindings()) {
					result.getSummary().tallySeverity(finding.getSeverity());
				}
			}
		}

		result.merge(validateMetadataIdUniqueness(declaredIds, skillPath));
		return result;
	}

	/**
	 * Report {@code metadata.id} values declared by more than one skill in this
	 * collection. Uniqueness is evaluated only across the skills discovered together in
	 * one run; it does not consult a registry or a saved catalog.
	 */
	private ValidationResult validateMetadataIdUniqueness(Map<String, List<Path>> declaredIds, Path root) {
		ValidationResult result = new ValidationResult();
		if (declaredIds.isEmpty()) {
			return result;
		}
		Map<String, List<String>> duplicates = duplicateMetadataIds(declaredIds, root);
		for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
			String value = entry.getKey();
			List<String> relative = entry.getValue();
			result.addFinding(finding("metadata_id_duplicate", metadataIdDuplicateSeverity(),
					"metadata.id '" + value + "' is declared by " + relative.size() + " skills in this collection: "
							+ String.join(", ", relative),
					root.toString(), null, null, "Give each skill its own metadata.id.",
					meta("metadata_id", value, "skills", relative, "profile", policy.getProfile(),
							"metadata_id_requirement", policy.getMetadataIdRequirement())));
		}
		if (duplicates.isEmpty()) {
			int count = declaredIds.values().stream().mapToInt(List::size).sum();
			result.addSuccess("metadata_id_unique",
					count + " declared metadata.id value(s) are unique within this collection",
					Map.of("metadata_id_count", count));
		}
		return result;
	}

	/** Severity of a duplicate {@code metadata.id} (HIGH only when IDs are required). */
	private Severity metadataIdDuplicateSeverity() {
		Severity defaultSeverity = "require".equals(policy.getMetadataIdRequirement()) ? Severity.HIGH
				: Severity.MEDIUM;
		return policy.severityFor(CATEGORY, "metadata_id_duplicate", defaultSeverity);
	}

	/**
	 * Maps each {@code metadata.id} declared by more than one skill to sorted skill
	 * labels under {@code root}.
	 */
	private static Map<String, List<String>> duplicateMetadataIds(Map<String, List<Path>> declaredIds, Path root) {
		Map<String, List<String>> out = new LinkedHashMap<>();
		List<String> keys = new ArrayList<>(declaredIds.keySet());
		java.util.Collections.sort(keys);
		for (String value : keys) {
			List<Path> dirs = declaredIds.get(value);
			if (dirs.size() > 1) {
				List<String> labels = dirs.stream().map(dir -> relativeLabel(dir, root)).sorted().toList();
				out.put(value, labels);
			}
		}
		return out;
	}

	private static String relativeLabel(Path skillDir, Path root) {
		try {
			return toPosix(root.relativize(skillDir));
		}
		catch (IllegalArgumentException e) {
			return toPosix(skillDir);
		}
	}

	private static String toPosix(Path path) {
		return path.toString().replace(java.io.File.separatorChar, '/');
	}

	/** Runs all schema validation checks on a single skill directory. */
	private ValidationResult validateSingleSkill(Path skillPath) {
		ValidationResult result = new ValidationResult();
		Path skillMd = findSkillManifest(skillPath);

		if (skillMd == null) {
			result.addFinding(finding("manifest_exists", Severity.HIGH, "SKILL.md not found in skill directory",
					skillPath.toString(), null, null, "Create a SKILL.md file with valid YAML frontmatter", null));
			return result;
		}

		result.addSuccess("manifest_exists", "Found skill manifest: " + skillMd.getFileName(),
				Map.of("file_path", skillMd.toString()));

		if (isLowercaseManifest(skillPath)) {
			result.addFinding(finding("manifest_naming", Severity.HIGH,
					"Skill manifest uses non-canonical name 'skill.md' \u2014 "
							+ "the agentskills.io spec requires 'SKILL.md' (uppercase). "
							+ "Codex and Hermes never load it; Claude Code, OpenCode, Copilot CLI "
							+ "and OpenClaw load it only on case-insensitive filesystems (macOS), "
							+ "so it can work locally and be missing on Linux",
					skillMd.toString(), null, null,
					"Rename 'skill.md' to 'SKILL.md' to comply with the agentskills.io spec", null));
		}

		// Frontmatter validation is prerequisite for other checks.
		ValidationResult frontmatterResult = validateFrontmatter(skillMd);
		result.merge(frontmatterResult);
		if (!frontmatterResult.isPassed()) {
			return result;
		}

		result.merge(validateFolderStructure(skillPath));
		result.merge(validateNamingConventions(skillPath));
		result.merge(validateLineCount(skillMd));
		result.merge(validateBodyContent(skillMd));
		result.merge(validateOptionalFiles(skillPath));

		if (result.getMetadata().get("frontmatter") instanceof SchemaFrontmatter frontmatter) {
			result.merge(validateNameMatchesDirectory(skillPath, frontmatter));
			result.merge(validateAuthor(frontmatter, skillMd.toString()));
			result.merge(validateMetadataId(frontmatter, skillMd.toString()));
			result.merge(validatePortability(skillPath, skillMd, frontmatter));
		}

		return result;
	}

	/** True when the manifest exists only under the non-canonical lowercase name. */
	private boolean isLowercaseManifest(Path skillPath) {
		Path canonical = ScanPaths.findExactChild(skillPath, "SKILL.md");
		Path lowercase = ScanPaths.findExactChild(skillPath, "skill.md");
		return lowercase != null && canonical == null;
	}

	// ------------------------------------------------------------------
	// Frontmatter
	// ------------------------------------------------------------------

	/** Parses SKILL.md and validates its YAML frontmatter against the schema. */
	private ValidationResult validateFrontmatter(Path skillMd) {
		ValidationResult result = new ValidationResult();
		String filePath = skillMd.toString();

		String content;
		try {
			content = Files.readString(skillMd, StandardCharsets.UTF_8);
		}
		catch (IOException | RuntimeException e) {
			result.addFinding(finding("file_readable", Severity.HIGH, "Failed to read file: " + e.getMessage(),
					filePath, null, null, "Check file permissions and encoding", null));
			return result;
		}
		// Python reads with utf-8-sig, which drops a leading byte-order mark.
		if (content.startsWith("\uFEFF")) {
			content = content.substring(1);
		}

		Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
		if (!matcher.lookingAt()) {
			result.addFinding(finding("frontmatter_format", Severity.HIGH,
					"SKILL.md must have YAML frontmatter between --- markers", filePath, 1, null,
					"Add frontmatter block: start with '---', add YAML content, end with '---'", null));
			return result;
		}
		String frontmatterYaml = matcher.group(1);
		String markdownContent = matcher.group(2);

		Object data;
		try {
			LoaderOptions options = new LoaderOptions();
			// PyYAML keeps the last duplicate key silently; the duplicate_key check
			// below reports it, so the load itself must not reject it.
			options.setAllowDuplicateKeys(true);
			// Fed as UTF-8 bytes rather than a String: SnakeYAML 2.x's reader over a
			// java.lang.String throws IndexOutOfBoundsException when a plain (unquoted)
			// scalar holds supplementary characters that straddle its 1024-char buffer.
			// PyYAML parses those files fine, so the byte stream keeps parity with it.
			data = new Yaml(new SafeConstructor(options))
				.load(new java.io.ByteArrayInputStream(frontmatterYaml.getBytes(StandardCharsets.UTF_8)));
		}
		catch (MarkedYAMLException e) {
			result.addFinding(finding("yaml_syntax", Severity.HIGH,
					"Invalid YAML syntax in frontmatter: " + e.getMessage(), filePath, problemLine(e), null,
					"Fix YAML syntax errors (check indentation, quotes, colons)", null));
			return result;
		}
		catch (RuntimeException e) {
			result.addFinding(
					finding("yaml_syntax", Severity.HIGH, "Invalid YAML syntax in frontmatter: " + e.getMessage(),
							filePath, 2, null, "Fix YAML syntax errors (check indentation, quotes, colons)", null));
			return result;
		}

		if (!(data instanceof Map<?, ?> mapping) || mapping.isEmpty()) {
			result.addFinding(
					finding("frontmatter_content", Severity.HIGH, "Frontmatter must be a non-empty YAML dictionary",
							filePath, 2, null, "Add required fields: name, description, globs", null));
			return result;
		}

		for (SchemaYaml.DuplicateKey duplicate : SchemaYaml.duplicateFrontmatterKeys(frontmatterYaml)) {
			String key = duplicate.key();
			result.addFinding(finding("duplicate_key", policy.severityFor(CATEGORY, "duplicate_key", Severity.HIGH),
					"Duplicate frontmatter key '" + key + "': YAML parsers disagree on it "
							+ "(Codex, OpenCode, Copilot CLI and OpenClaw drop the skill; others keep the last value)",
					filePath, duplicate.line(), null, "Keep a single '" + key + "' entry", meta("key", key)));
		}

		List<SchemaFrontmatter.FieldError> fieldErrors = new ArrayList<>();
		SchemaFrontmatter frontmatter = SchemaFrontmatter.validate(asObjectMap(mapping), fieldErrors);
		if (frontmatter != null) {
			result.addSuccess("frontmatter_valid", "Valid frontmatter for skill '" + frontmatter.getName() + "'",
					Map.of("description_length",
							frontmatter.getDescription().codePointCount(0, frontmatter.getDescription().length())));
			result.getMetadata().put("frontmatter", frontmatter);
			result.getMetadata()
				.put("manifest",
						new SchemaManifest(frontmatter, markdownContent, filePath, pySplitLinesCount(content)));
			return result;
		}

		for (SchemaFrontmatter.FieldError error : fieldErrors) {
			result.addFinding(finding("frontmatter_field", Severity.HIGH,
					"Field '" + error.field() + "': " + error.message(), filePath, null, null,
					"Fix the '" + error.field() + "' field in frontmatter according to schema requirements",
					meta("field", error.field(), "error_type", error.errorType())));
		}
		return result;
	}

	/**
	 * The 1-based frontmatter line a YAML parse error names, or 2 when it does not say.
	 */
	private static Integer problemLine(MarkedYAMLException e) {
		Mark mark = e.getProblemMark();
		if (mark == null) {
			return 2;
		}
		return mark.getLine() + 2;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asObjectMap(Map<?, ?> raw) {
		// The parser may hand back non-string keys; the schema check reports those,
		// so the map is read through Object keys everywhere below.
		return (Map<String, Object>) raw;
	}

	// ------------------------------------------------------------------
	// Structure and naming
	// ------------------------------------------------------------------

	/** Verifies the skill sits in a valid folder hierarchy (skills/ or team-skills/). */
	private ValidationResult validateFolderStructure(Path skillPath) {
		ValidationResult result = new ValidationResult();
		List<String> parts = pathParts(skillPath);
		String filePath = skillPath.toString();
		String lastName = parts.isEmpty() ? "" : parts.get(parts.size() - 1);

		int skillsIdx = parts.indexOf("skills");
		int teamIdx = parts.indexOf("team-skills");
		if (skillsIdx >= 0) {
			if (skillsIdx == parts.size() - 2) {
				result.addSuccess("folder_hierarchy", "Valid general skill structure: skills/" + lastName + "/");
			}
			else {
				result.addFinding(
						finding("folder_hierarchy", Severity.MEDIUM, "Unexpected nesting depth for general skill",
								filePath, null, null, "General skills should be at: skills/<skill-name>/", null),
						false, false);
			}
		}
		else if (teamIdx >= 0) {
			int depth = parts.size() - teamIdx - 1;
			if (depth >= 2) {
				result.addSuccess("folder_hierarchy",
						"Valid team skill structure: team-skills/" + parts.get(teamIdx + 1) + "/.../" + lastName + "/");
			}
			else {
				result.addFinding(finding("folder_hierarchy", Severity.HIGH, "Invalid team-skills structure", filePath,
						null, null, "Team skills must be at: team-skills/<team>/<skill-name>/", null));
			}
		}
		else {
			result.addFinding(
					finding("folder_hierarchy", Severity.MEDIUM,
							"Skill not in standard location (skills/ or team-skills/)", filePath, null, null,
							"Move skill to skills/<skill-name>/ or team-skills/<team>/<skill-name>/", null),
					false, false);
		}

		if (findSkillManifest(skillPath) == null) {
			result.addFinding(finding("manifest_required", Severity.HIGH,
					"Missing " + Constants.SKILL_MANIFEST_FILE + " in skill directory", filePath, null, null,
					"Create a " + Constants.SKILL_MANIFEST_FILE + " file with valid frontmatter", null));
		}
		return result;
	}

	private static List<String> pathParts(Path path) {
		List<String> parts = new ArrayList<>();
		for (Path element : path) {
			parts.add(element.toString());
		}
		return parts;
	}

	/** Verifies the folder name follows the kebab-case convention (1-64 chars). */
	private ValidationResult validateNamingConventions(Path skillPath) {
		ValidationResult result = new ValidationResult();
		String name = skillPath.getFileName().toString();
		String filePath = skillPath.toString();

		if (!Constants.KEBAB_CASE.matcher(name).lookingAt()) {
			result.addFinding(finding("naming_convention", Severity.HIGH,
					"Folder '" + name + "' must be kebab-case (lowercase, numbers, hyphens)", filePath, null, null,
					"Rename folder to: " + name.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'), null));
		}
		else if (name.contains("--")) {
			result.addFinding(finding("naming_convention", Severity.HIGH,
					"Folder '" + name + "' cannot contain consecutive hyphens", filePath, null, null,
					"Rename folder to: " + name.replace("--", "-"), null));
		}
		else if (name.endsWith("-")) {
			result.addFinding(
					finding("naming_convention", Severity.HIGH, "Folder '" + name + "' cannot end with a hyphen",
							filePath, null, null, "Rename folder to: " + trimTrailing(name, '-'), null));
		}
		else if (name.codePointCount(0, name.length()) > 64) {
			int length = name.codePointCount(0, name.length());
			result.addFinding(finding("naming_convention", Severity.HIGH,
					"Folder '" + name + "' exceeds 64 char limit (" + length + " chars)", filePath, null, null,
					"Shorten the folder name to 64 characters or less",
					meta("current_length", length, "max_length", 64)));
		}
		else {
			result.addSuccess("naming_convention", "Folder name '" + name + "' follows kebab-case convention",
					Map.of("name_length", name.codePointCount(0, name.length())));
		}
		return result;
	}

	/** Ensures the directory name matches the frontmatter {@code name} field. */
	private ValidationResult validateNameMatchesDirectory(Path skillPath, SchemaFrontmatter frontmatter) {
		ValidationResult result = new ValidationResult();
		String dirName = skillPath.getFileName().toString();
		String fmName = frontmatter.getName();
		String filePath = skillPath.resolve(Constants.SKILL_MANIFEST_FILE).toString();

		if (!dirName.equals(fmName)) {
			result.addFinding(finding("name_consistency", Severity.HIGH,
					"Directory name '" + dirName + "' doesn't match frontmatter name '" + fmName + "'", filePath, null,
					null, "Either rename directory to '" + fmName + "' or update frontmatter name to '" + dirName + "'",
					meta("directory_name", dirName, "frontmatter_name", fmName)));
		}
		else {
			result.addSuccess("name_consistency", "Directory name matches frontmatter: '" + fmName + "'");
		}
		return result;
	}

	// ------------------------------------------------------------------
	// Body content
	// ------------------------------------------------------------------

	/** Warns when SKILL.md exceeds the recommended line count. */
	private ValidationResult validateLineCount(Path skillMd) {
		ValidationResult result = new ValidationResult();
		String filePath = skillMd.toString();
		try {
			String content = readUtf8Stripped(skillMd);
			int lineCount = pySplitLinesCount(content);
			if (lineCount > Constants.MAX_SKILL_MD_LINES) {
				result
					.addFinding(
							finding("line_count", Severity.MEDIUM,
									"SKILL.md has " + lineCount + " lines (limit: " + Constants.MAX_SKILL_MD_LINES
											+ ")",
									filePath, null, null,
									"Consider moving content to reference files in references/ directory",
									meta("line_count", lineCount, "limit", Constants.MAX_SKILL_MD_LINES)),
							false, false);
			}
			else {
				result.addSuccess("line_count",
						"SKILL.md within line limit (" + lineCount + "/" + Constants.MAX_SKILL_MD_LINES + ")",
						Map.of("line_count", lineCount, "limit", Constants.MAX_SKILL_MD_LINES));
			}
		}
		catch (IOException | RuntimeException e) {
			result.addFinding(finding("line_count", Severity.LOW, "Could not check line count: " + e.getMessage(),
					filePath, null, null, null, null), false, false);
		}
		return result;
	}

	private static final List<String> INSTRUCTIONS_ALTERNATIVES = List.of("## Instructions", "## Usage");

	/**
	 * Validates the SKILL.md body has a top-level heading and the recommended sections.
	 *
	 * <p>
	 * Per agentskills.io the body has no format restrictions, so only a missing top-level
	 * title hard-fails. Section headings are convention nudges: {@code ## Instructions}
	 * (or {@code ## Usage}) and {@code ## Examples} are MEDIUM.
	 */
	private ValidationResult validateBodyContent(Path skillMd) {
		ValidationResult result = new ValidationResult();
		String filePath = skillMd.toString();

		String content;
		try {
			content = readUtf8Stripped(skillMd);
		}
		catch (IOException | RuntimeException e) {
			return result;
		}

		Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
		String body = matcher.lookingAt() ? matcher.group(2) : content;

		if (!BODY_HEADING.matcher(body).find()) {
			result.addFinding(finding("body_heading", Severity.HIGH,
					"Missing top-level heading (# Title) in SKILL.md body", filePath, null, null,
					"Add a top-level heading after the frontmatter, e.g. '# My Skill'", null));
		}
		else {
			result.addSuccess("body_heading", "Body contains a top-level heading");
		}

		// REQUIRED_BODY_SECTIONS is empty upstream: nothing hard-fails in the body.
		for (String section : Constants.RECOMMENDED_BODY_SECTIONS) {
			if ("## Instructions".equals(section)) {
				if (INSTRUCTIONS_ALTERNATIVES.stream().anyMatch(body::contains)) {
					result.addSuccess("body_recommended_section",
							"Found recommended section: '## Instructions' (or '## Usage')");
				}
				else {
					result.addFinding(finding("body_recommended_section", Severity.MEDIUM,
							"Missing recommended section: '" + section + "'", filePath, null, null,
							"Consider adding a '## Instructions' or '## Usage' section. "
									+ "Per agentskills.io the body format is unrestricted, so this is "
									+ "a convention nudge \u2014 it also gives the quality scorer a stable "
									+ "anchor for instruction-quality heuristics.",
							meta("missing_section", section)));
				}
			}
			else if (!body.contains(section)) {
				result.addFinding(finding("body_recommended_section", Severity.MEDIUM,
						"Missing recommended section: '" + section + "'", filePath, null, null,
						"Consider adding a '" + section + "' section. "
								+ "If examples are already inline under instructions, " + "this can be skipped.",
						meta("missing_section", section)));
			}
			else {
				result.addSuccess("body_recommended_section", "Found recommended section: '" + section + "'");
			}
		}
		return result;
	}

	// ------------------------------------------------------------------
	// Author and stable id
	// ------------------------------------------------------------------

	/**
	 * Classifies {@code metadata.author} and applies the active policy. Four outcomes,
	 * each with its own check name so a policy can set its severity independently:
	 * {@code author_missing}, {@code author_without_email}, {@code author_format}
	 * (malformed, unsafe, or outside the policy's email domain), or success.
	 */
	private ValidationResult validateAuthor(SchemaFrontmatter frontmatter, String filePath) {
		ValidationResult result = new ValidationResult();
		Map<String, Object> metadata = frontmatter.getMetadata();
		String author = metadata != null && metadata.get("author") instanceof String value ? value.strip() : "";

		if (author.isEmpty()) {
			result.addFinding(finding("author_missing", policy.severityFor(CATEGORY, "author_missing", Severity.LOW),
					"Author not specified in metadata", filePath, null, null,
					"Add 'metadata.author' with a person, team, or organization name, "
							+ "or 'Name <email@example.com>' to include a contact address",
					meta("profile", policy.getProfile())));
			return result;
		}

		String problem = SchemaAuthor.authorValueProblem(author);
		boolean shapeOk = problem == null && SchemaPolicy.AUTHOR_SHAPE_PATTERN.matcher(author).matches();
		boolean domainOk = shapeOk && policy.isAuthorEmailAcceptable(author);
		Map<String, Object> findingMetadata = meta("current_author", SchemaAuthor.truncate(author, AUTHOR_MAX_LENGTH),
				"profile", policy.getProfile(), "shape_ok", shapeOk, "domain_ok", domainOk);

		String message;
		String suggestion;
		if (problem != null) {
			message = "Author " + problem;
			suggestion = "Use a plain name such as 'Example Team', or 'Full Name <email@example.com>'";
		}
		else if (shapeOk && domainOk) {
			result.addSuccess("author_format", "Valid author format: " + author);
			return result;
		}
		else if (policy.getAuthorEmailRegex() != null) {
			String subject = shapeOk ? "Author email does not match" : "Author has no email address matching";
			message = subject + " the required domain (profile '" + policy.getProfile() + "', pattern: "
					+ policy.getAuthorEmailRegex().pattern() + ")";
			suggestion = "Use 'Name <email>' with an email matching the active profile's author_email_regex, "
					+ "or review whether that domain restriction is appropriate for your repository.";
		}
		else {
			result
				.addFinding(
						finding("author_without_email",
								policy.severityFor(CATEGORY, "author_without_email", Severity.INFO),
								"Author has no email address: " + author, filePath, null, "author: " + author,
								"A name is accepted as attribution unless the profile requires a contact address "
										+ "(such as 'registry'); then use 'Name <email@example.com>'",
								findingMetadata));
			return result;
		}

		result.addFinding(finding("author_format", policy.severityFor(CATEGORY, "author_format", Severity.HIGH),
				message, filePath, null, "author: " + SchemaAuthor.truncate(author, AUTHOR_MAX_LENGTH), suggestion,
				findingMetadata));
		return result;
	}

	/**
	 * Validates the optional {@code metadata.id} against the stable-ID contract. A
	 * present ID must be an exact 1-64 character lowercase ASCII slug in every policy
	 * mode; {@code metadata_id_requirement} decides whether absence is ignored,
	 * recommended (LOW) or required (HIGH), and whether an invalid ID blocks.
	 */
	private ValidationResult validateMetadataId(SchemaFrontmatter frontmatter, String filePath) {
		ValidationResult result = new ValidationResult();
		String requirement = policy.getMetadataIdRequirement();
		Map<String, Object> extra = frontmatter.getMetadata();
		Map<String, Object> details = meta("profile", policy.getProfile(), "metadata_id_requirement", requirement);

		if (extra == null || !extra.containsKey("id")) {
			if ("ignore".equals(requirement)) {
				return result;
			}
			Severity defaultSeverity = "require".equals(requirement) ? Severity.HIGH : Severity.LOW;
			String verb = "require".equals(requirement) ? "requires" : "recommends";
			result
				.addFinding(finding("metadata_id_missing",
						policy.severityFor(CATEGORY, "metadata_id_missing", defaultSeverity),
						"metadata.id not specified; profile '" + policy.getProfile() + "' " + verb
								+ " a stable skill ID",
						filePath, null, null,
						"Add 'metadata.id' with a lowercase slug such as 'my-skill' that stays fixed across versions",
						details));
			return result;
		}

		Object value = extra.get("id");
		String problem = SchemaYaml.metadataIdProblem(value);
		if (problem != null) {
			Severity defaultSeverity = "require".equals(requirement) ? Severity.HIGH : Severity.MEDIUM;
			result.addFinding(finding("metadata_id_format",
					policy.severityFor(CATEGORY, "metadata_id_format", defaultSeverity), "metadata.id " + problem,
					filePath, null, "id: " + SchemaYaml.displayMetadataId(value),
					"Use a lowercase slug such as 'my-skill' (1-64 characters: a-z, 0-9, single hyphens)", details));
			return result;
		}

		result.addSuccess(
				"metadata_id_format", "Valid metadata.id '" + value
						+ "'. Uniqueness is checked only across skills validated together " + "in one collection run",
				Map.of("metadata_id", value));
		result.getMetadata().put("metadata_id", value);
		return result;
	}

	// ------------------------------------------------------------------
	// Folder compliance and optional files
	// ------------------------------------------------------------------

	/**
	 * Checks whether the folder carries the standard skills/ or team-skills/ directories.
	 */
	private ValidationResult validateFolderCompliance(Path folderPath) {
		ValidationResult result = new ValidationResult();
		Path skillsDir = folderPath.resolve("skills");
		Path teamSkillsDir = folderPath.resolve("team-skills");
		boolean hasSkills = Files.exists(skillsDir);
		boolean hasTeamSkills = Files.exists(teamSkillsDir);
		String filePath = folderPath.toString();

		if (hasSkills || hasTeamSkills) {
			result.addSuccess("folder_structure", "Folder structure compliant with SkillEvaluator guidelines");
			if (hasSkills) {
				int count = countEntries(skillsDir);
				result.addSuccess("skills_directory", "Found 'skills/' directory with " + count + " items",
						Map.of("item_count", count));
			}
			if (hasTeamSkills) {
				int count = countEntries(teamSkillsDir);
				result.addSuccess("team_skills_directory", "Found 'team-skills/' directory with " + count + " teams",
						Map.of("team_count", count));
			}
		}
		else {
			List<String> parts = pathParts(folderPath);
			if (parts.contains("skills") || parts.contains("team-skills")) {
				result.addSuccess("folder_structure", "Validating skills within standard folder structure");
			}
			else {
				result.addFinding(finding("folder_structure", Severity.MEDIUM,
						"Folder doesn't follow SkillEvaluator structure (missing skills/ or team-skills/)", filePath,
						null, null, "Create skills/ and/or team-skills/ directories for organizing skills", null),
						false, false);
			}
		}
		return result;
	}

	private static int countEntries(Path dir) {
		try (var stream = Files.list(dir)) {
			return (int) stream.count();
		}
		catch (IOException e) {
			return 0;
		}
	}

	/**
	 * Allowed skill-root subdirectories: the built-in defaults plus per-repo extras from
	 * {@code SKILLEVALUATOR_SCHEMA_ALLOWED_DIRS} (comma- or whitespace-separated), which
	 * can extend but never shrink the set.
	 */
	private static Set<String> allowedSkillDirs() {
		Set<String> dirs = new TreeSet<>(Constants.DEFAULT_ALLOWED_SKILL_DIRS);
		String extra = System.getenv(SCHEMA_ALLOWED_DIRS_ENV);
		if (extra != null) {
			for (String name : extra.split("[,\\s]+")) {
				if (!name.isEmpty()) {
					dirs.add(name);
				}
			}
		}
		return dirs;
	}

	/** Checks for optional supporting files and warns about unexpected items. */
	private ValidationResult validateOptionalFiles(Path skillPath) {
		ValidationResult result = new ValidationResult();
		Set<String> allowedDirs = allowedSkillDirs();
		Set<String> optional = new TreeSet<>(allowedDirs);
		optional.add("README.md");
		Set<String> expected = new LinkedHashSet<>(optional);
		expected.add("SKILL.md");
		expected.add("skill.md");

		List<String> found = new ArrayList<>();
		for (String name : optional) {
			if (Files.exists(skillPath.resolve(name))) {
				found.add(name);
			}
		}
		if (!found.isEmpty()) {
			result.addSuccess("optional_files", "Found optional supporting files: " + String.join(", ", found),
					Map.of("files_found", found));
		}

		String allowedHint = allowedDirs.stream().map(dir -> dir + "/").reduce((a, b) -> a + ", " + b).orElse("");
		List<String> unexpectedItems = new ArrayList<>();
		List<Path> entries = new ArrayList<>();
		try (var stream = Files.list(skillPath)) {
			stream.sorted(Comparator.<Path, String>comparing(path -> path.getFileName().toString()))
				.forEach(entries::add);
		}
		catch (IOException e) {
			entries.clear();
		}
		for (Path item : entries) {
			String name = item.getFileName().toString();
			if (ScanPaths.matchesFilesystemName(item, Constants.SCAN_EXCLUDED_FILES)) {
				continue;
			}
			if (!expected.contains(name) && !name.startsWith(".")) {
				unexpectedItems.add(name);
				result.addFinding(finding("unexpected_file", Severity.LOW, "Unexpected '" + name + "' in skill root",
						item.toString(), null, null, "Consider moving to one of: " + allowedHint
								+ ". To allow additional directories, set $" + SCHEMA_ALLOWED_DIRS_ENV + ".",
						null), false, false);
			}
		}

		if (found.isEmpty() && unexpectedItems.isEmpty()) {
			result.addSuccess("optional_files", "Skill directory contains only expected files");
		}
		return result;
	}

	// ------------------------------------------------------------------
	// Portability
	// ------------------------------------------------------------------

	/**
	 * Flags spec-valid patterns that common agent harnesses load differently or not at
	 * all.
	 */
	private ValidationResult validatePortability(Path skillPath, Path skillMd, SchemaFrontmatter frontmatter) {
		ValidationResult result = new ValidationResult();
		String filePath = skillMd.toString();
		List<Finding> findings = new ArrayList<>();

		if (Files.isSymbolicLink(skillMd)) {
			findings.add(portabilityFinding("manifest_symlink", Severity.MEDIUM,
					"SKILL.md is a symlink; Codex, Hermes and OpenClaw do not load symlinked manifests", filePath,
					"Replace the symlink with a regular SKILL.md file", null));
		}

		byte[] raw;
		try {
			raw = Files.readAllBytes(skillMd);
		}
		catch (IOException e) {
			raw = new byte[0];
		}
		if (startsWith(raw, UTF8_BOM)) {
			findings.add(portabilityFinding("manifest_bom", Severity.MEDIUM,
					"SKILL.md starts with a UTF-8 byte-order mark; Codex drops the skill", filePath,
					"Save SKILL.md as UTF-8 without BOM", null));
		}
		for (Object[] threshold : Constants.SKILL_MD_SIZE_THRESHOLDS) {
			int limit = (Integer) threshold[0];
			if (raw.length > limit) {
				findings.add(portabilityFinding("manifest_size", Severity.fromString((String) threshold[1]),
						"SKILL.md is " + formatThousands(raw.length) + " bytes (over " + formatThousands(limit)
								+ "); harnesses cut, refuse or skip large manifests (OpenClaw reads "
								+ "16,000-64,000 chars and skips over 256,000 bytes; OpenCode cuts at 50 KiB)",
						filePath, "Move detail into references/ files and keep SKILL.md short",
						meta("size_bytes", raw.length, "limit_bytes", limit)));
				break;
			}
		}

		// Same folders the harness check skips: evals/ and versions/ are scanned like any
		// other folder.
		for (Path nested : nestedManifests(skillPath)) {
			findings.add(portabilityFinding("nested_manifest", Severity.MEDIUM,
					"Nested skill manifest '" + toPosix(skillPath.relativize(nested)) + "' inside this skill; "
							+ "Codex, OpenCode and Hermes load it as a separate skill, others ignore it",
					nested.toString(), "Rename the nested file or move it out of the skill folder", null));
		}

		String description = frontmatter.getDescription();
		int utf16Units = description.length();
		int characters = description.codePointCount(0, description.length());
		if (characters <= Constants.DESCRIPTION_MAX_LENGTH && Constants.DESCRIPTION_MAX_LENGTH < utf16Units) {
			findings.add(portabilityFinding("description_utf16_length", Severity.MEDIUM,
					"Description is " + characters + " characters but " + utf16Units + " UTF-16 units; "
							+ "Copilot CLI counts UTF-16 units and drops descriptions over "
							+ Constants.DESCRIPTION_MAX_LENGTH,
					filePath, "Shorten the description or replace emoji and other non-BMP characters",
					meta("utf16_units", utf16Units)));
		}

		Matcher trigger = TRIGGER_PATTERN.matcher(description);
		boolean hasTrigger = trigger.find();
		int triggerStart = hasTrigger ? description.codePointCount(0, trigger.start()) : -1;
		int triggerEnd = hasTrigger ? description.codePointCount(0, trigger.end()) : -1;
		if (!hasTrigger || triggerEnd > Constants.DESCRIPTION_TRIGGER_WINDOW) {
			String where = hasTrigger ? "its trigger at characters " + triggerStart + "-" + triggerEnd
					: "no 'Use when ...' trigger";
			findings.add(portabilityFinding("description_trigger", Severity.LOW,
					"Description has " + where + "; some harnesses show only the first ~"
							+ Constants.DESCRIPTION_TRIGGER_WINDOW + " characters in their skill list",
					filePath, "State when to use the skill within the first " + Constants.DESCRIPTION_TRIGGER_WINDOW
							+ " characters",
					null));
		}

		Set<String> unknown = new TreeSet<>(frontmatter.getExtras());
		unknown.removeAll(Constants.SPEC_SKILL_FIELDS);
		if (!unknown.isEmpty()) {
			findings.add(portabilityFinding("unknown_field", Severity.LOW,
					"Frontmatter field(s) outside the agentskills.io spec: " + String.join(", ", unknown)
							+ "; agent harnesses do not pass them to the model",
					filePath, "Remove them or move them under 'metadata'", meta("fields", new ArrayList<>(unknown))));
		}

		for (Finding finding : findings) {
			result.addFinding(finding);
		}
		return result;
	}

	/**
	 * Manifest variants found below the skill root, excluding non-shipped directories.
	 */
	private static List<Path> nestedManifests(Path skillPath) {
		List<Path> found = new ArrayList<>();
		try {
			Files.walkFileTree(skillPath, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (!Constants.SKILL_MANIFEST_VARIANTS.contains(file.getFileName().toString())) {
						return FileVisitResult.CONTINUE;
					}
					Path relative;
					try {
						relative = skillPath.relativize(file);
					}
					catch (IllegalArgumentException e) {
						return FileVisitResult.CONTINUE;
					}
					if (relative.getNameCount() <= 1) {
						return FileVisitResult.CONTINUE;
					}
					for (Path part : relative) {
						if (SKILL_NOT_SHIPPED_DIRS.contains(part.toString())) {
							return FileVisitResult.CONTINUE;
						}
					}
					found.add(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException e) {
			return List.of();
		}
		found.sort(Comparator.comparing(Path::toString));
		return found;
	}

	private Finding portabilityFinding(String checkName, Severity defaultSeverity, String message, String filePath,
			String suggestion, Map<String, Object> metadata) {
		return finding(checkName, policy.severityFor(CATEGORY, checkName, defaultSeverity), message, filePath, null,
				null, suggestion, metadata);
	}

	// ------------------------------------------------------------------
	// Small shared helpers
	// ------------------------------------------------------------------

	private static Finding finding(String checkName, Severity severity, String message, String filePath,
			Integer lineNumber, String lineContent, String suggestion, Map<String, Object> metadata) {
		return new Finding(CATEGORY, severity, checkName, message, filePath, lineNumber, lineContent, suggestion,
				metadata == null ? Map.of() : metadata);
	}

	private static Map<String, Object> meta(Object... keyValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
		}
		return map;
	}

	/**
	 * Reads a file as UTF-8 with a leading byte-order mark removed (Python's utf-8-sig
	 * read).
	 */
	private static String readUtf8Stripped(Path path) throws IOException {
		String text = Files.readString(path, StandardCharsets.UTF_8);
		return text.startsWith("\uFEFF") ? text.substring(1) : text;
	}

	/** Python's {@code len(text.splitlines())} for \n, \r\n and \r breaks. */
	static int pySplitLinesCount(String text) {
		int breaks = 0;
		boolean trailingContent = false;
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '\r') {
				breaks++;
				i++;
				if (i < text.length() && text.charAt(i) == '\n') {
					i++;
				}
				trailingContent = false;
			}
			else if (c == '\n') {
				breaks++;
				i++;
				trailingContent = false;
			}
			else {
				trailingContent = true;
				i++;
			}
		}
		return breaks + (trailingContent ? 1 : 0);
	}

	/** Python's {@code str.rstrip(chars)} for a single character. */
	private static String trimTrailing(String value, char ch) {
		int end = value.length();
		while (end > 0 && value.charAt(end - 1) == ch) {
			end--;
		}
		return value.substring(0, end);
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		if (data.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (data[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	/** Python's {@code f"{value:,}"} thousands formatting. */
	private static String formatThousands(long value) {
		return String.format(java.util.Locale.US, "%,d", value);
	}

	/**
	 * The parsed manifest, mirroring the Python {@code SkillManifest} stored in result
	 * metadata.
	 */
	record SchemaManifest(SchemaFrontmatter frontmatter, String content, String filePath, int lineCount) {
	}

}
