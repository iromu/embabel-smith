/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.util;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Skill-related constants, ported from the Python {@code constants.py}. Only Tier 1
 * skill-validation entries are carried over.
 *
 * @author Ivan Rodriguez
 */
public final class Constants {

	private Constants() {
	}

	// Skill structure
	/**
	 * Canonical skill manifest name that the schema and license validators locate inside
	 * a skill folder.
	 */
	public static final String SKILL_MANIFEST_FILE = "SKILL.md";

	/**
	 * Accepted manifest spellings used by ScanPaths and the PII checks to recognize a
	 * skill and its manifest.
	 */
	public static final List<String> SKILL_MANIFEST_VARIANTS = List.of("SKILL.md", "skill.md");

	/** Frontmatter keys every skill must declare: name and description. */
	public static final List<String> REQUIRED_SKILL_FIELDS = List.of("name", "description");

	/** Frontmatter keys the schema frontmatter validator rejects as unsupported. */
	public static final List<String> FORBIDDEN_SKILL_FIELDS = List.of("alwaysApply", "always_apply", "globs");

	/** Vendor names the schema frontmatter validator forbids inside a skill name. */
	public static final Set<String> RESERVED_SKILL_NAMES = Set.of("anthropic", "claude");

	/** Markdown headings the schema validator recommends in a SKILL.md body. */
	public static final List<String> RECOMMENDED_BODY_SECTIONS = List.of("## Instructions", "## Examples");

	/**
	 * Subdirectories the schema validator accepts in a skill tree without further
	 * scrutiny.
	 */
	public static final Set<String> DEFAULT_ALLOWED_SKILL_DIRS = Set.of("agents", "references", "scripts", "assets",
			"evals", "tests", "tools", "config");

	/** Folders whose scripts the script-lint and quality validators inspect. */
	public static final List<String> EXECUTABLE_SKILL_DIRS = List.of("scripts", "tools");

	/** Glob patterns selecting Python scripts inside the executable skill folders. */
	public static final List<String> PYTHON_SCRIPT_PATTERNS = List.of("*.py");

	/** Glob patterns selecting Node.js scripts inside the executable skill folders. */
	public static final List<String> NODE_SCRIPT_PATTERNS = List.of("*.mjs", "*.cjs", "*.js");

	/** Glob patterns for any runnable script, used by the quality validator. */
	public static final List<String> EXECUTABLE_SCRIPT_PATTERNS = List.of("*.py", "*.sh", "*.mjs", "*.cjs", "*.js");

	// Field length constraints
	/**
	 * Minimum length of the frontmatter {@code name} accepted by the schema validators.
	 */
	public static final int NAME_MIN_LENGTH = 1;

	/**
	 * Maximum length of the frontmatter {@code name} accepted by the schema validators.
	 */
	public static final int NAME_MAX_LENGTH = 64;

	/** Minimum title length from the Python port; unused by the Java validators. */
	public static final int TITLE_MIN_LENGTH = 1;

	/** Maximum title length from the Python port; unused by the Java validators. */
	public static final int TITLE_MAX_LENGTH = 256;

	/**
	 * Minimum length of the frontmatter {@code description} accepted by the schema
	 * validators.
	 */
	public static final int DESCRIPTION_MIN_LENGTH = 1;

	/**
	 * Maximum length of the frontmatter {@code description} enforced by the schema
	 * validators.
	 */
	public static final int DESCRIPTION_MAX_LENGTH = 1024;

	/** Maximum length of the optional frontmatter {@code compatibility} field. */
	public static final int COMPATIBILITY_MAX_LENGTH = 500;

	/** Line budget the schema validator allows a SKILL.md before warning. */
	public static final int MAX_SKILL_MD_LINES = 500;

	/** Regex source behind {@link #KEBAB_CASE}. */
	public static final String KEBAB_CASE_PATTERN = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$";

	/** Kebab-case form the schema validators require for skill names. */
	public static final Pattern KEBAB_CASE = Pattern.compile(KEBAB_CASE_PATTERN);

	/**
	 * Frontmatter keys the schema validator treats as known when flagging unknown fields.
	 */
	public static final Set<String> SPEC_SKILL_FIELDS = Set.of("name", "description", "license", "compatibility",
			"metadata", "allowed-tools");

	/** SKILL.md size thresholds (bytes, severity name), largest first. */
	public static final List<Object[]> SKILL_MD_SIZE_THRESHOLDS = List.of(new Object[] { 256_000, "high" },
			new Object[] { 50 * 1024, "medium" }, new Object[] { 32 * 1024, "low" });

	/**
	 * Character window within which the schema validator expects a trigger phrase, for
	 * the skill list.
	 */
	public static final int DESCRIPTION_TRIGGER_WINDOW = 60;

	/** Phrases marking a "when to use" trigger in a skill description. */
	public static final Pattern DESCRIPTION_TRIGGER = Pattern
		.compile("\\buse (?:this (?:skill )?|it )?(?:when|for|to|if|whenever)\\b"
				+ "|\\bwhen (?:the user|a user|you|asked)\\b|\\btriggers?\\b");

	/** Python packages the hygiene validator rejects in skill scripts. */
	public static final List<String> BANNED_PACKAGES = List.of("subprocess32", "pycrypto");

	/** File extensions the PII scanner reads as text. */
	public static final Set<String> SCANNABLE_EXTENSIONS = Set.of(".py", ".sh", ".yaml", ".yml", ".json", ".md", ".mdc",
			".txt");

	/** Directories skipped at any depth during Tier 1 file-tree walks. */
	public static final Set<String> SCAN_EXCLUDED_DIRS = Set.of("evals", ".evals", "results", ".results", "versions",
			".versions", "__pycache__", ".git", ".venv", "node_modules");

	/** Generated publishing/signing artifacts not scanned as source. */
	public static final Set<String> SCAN_EXCLUDED_FILES = Set.of("skill-card.md", "BENCHMARK.md", "skill.oms.sig");

	/** Environment variables consulted for the home-path PII check submitter identity. */
	public static final List<String> HOME_PATH_SUBMITTER_ENV_VARS = List.of("SKILLEVALUATOR_SUBMITTER", "GITHUB_ACTOR",
			"USER", "LOGNAME", "USERNAME");

	// License validation
	/** License file names the license validator looks for in an asset folder. */
	public static final List<String> LICENSE_FILE_NAMES = List.of("LICENSE", "LICENSE.txt", "LICENSE.md", "LICENSE.rst",
			"LICENCE", "LICENCE.txt", "COPYING", "COPYING.txt", "NOTICE", "NOTICE.txt");

	/**
	 * Leading lines of a source file the license validator searches for an SPDX header.
	 */
	public static final int LICENSE_HEADER_SCAN_LINES = 50;

	/** Source extensions whose SPDX headers the license validator's Tier 3 scan reads. */
	public static final Set<String> LICENSE_HEADER_EXTENSIONS = Set.of(".py", ".sh", ".js", ".ts", ".yaml", ".yml",
			".md", ".txt", ".json");

	/**
	 * SPDX header matcher from the Python port; {@code LicenseValidator} keeps its own
	 * case-insensitive copy.
	 */
	public static final Pattern SPDX_LICENSE = Pattern.compile("SPDX-License-Identifier:\\s*([^\n]+)");

	// Unicode smuggling
	/** Extensions the Unicode-smuggling validator reads as text. */
	public static final Set<String> UNICODE_SCAN_EXTENSIONS = Set.of(".md", ".mdc", ".py", ".sh", ".yaml", ".yml",
			".json", ".txt", ".js", ".ts", ".html");

	/** Byte chunk size the Unicode-smuggling validator uses for its binary sniff. */
	public static final int BINARY_CHECK_CHUNK_SIZE = 8192;

	// Quality scoring
	/** Score below which the quality validator fails a skill by default. */
	public static final int QUALITY_DEFAULT_MIN_SCORE = 70;

	/**
	 * Estimated token count above which the quality validator warns that a skill is
	 * large.
	 */
	public static final int QUALITY_RECOMMENDED_MAX_TOKENS = 5000;

	/** Characters per token the quality validator uses to estimate token counts. */
	public static final int QUALITY_CHARS_PER_TOKEN = 4;

	/** Body-line budget from the Python port; unused by the Java validators. */
	public static final int QUALITY_MAX_BODY_LINES = 500;

	/** Vendor names the quality validator penalizes in a skill name. */
	public static final List<String> QUALITY_RESERVED_NAMES = List.of("anthropic", "claude");

	/** Subdirectories the quality validator skips when walking a skill tree. */
	public static final Set<String> QUALITY_EXCLUDED_DIRS = Set.of("scripts", "tools", "references", "assets", "eval",
			"evals", ".evals", "results", ".results", "versions", ".versions", "tests", "examples", "docs", "resources",
			"templates", "design-system", "__pycache__", ".git", ".venv", "node_modules");

	/** Folders whose contents the quality validator treats as bundled resources. */
	public static final List<String> QUALITY_RESOURCE_DIRS = List.of("assets", "templates", "design-system",
			"resources");

	// Script lint
	/** Nesting depth above which the script lint validator flags deeply nested code. */
	public static final int SCRIPT_LINT_MAX_NESTING = 6;

}
