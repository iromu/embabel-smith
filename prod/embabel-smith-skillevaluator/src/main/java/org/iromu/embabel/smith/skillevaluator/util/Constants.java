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
 */
public final class Constants {

	private Constants() {
	}

	// Skill structure
	public static final String SKILL_MANIFEST_FILE = "SKILL.md";

	public static final List<String> SKILL_MANIFEST_VARIANTS = List.of("SKILL.md", "skill.md");

	public static final List<String> REQUIRED_SKILL_FIELDS = List.of("name", "description");

	public static final List<String> FORBIDDEN_SKILL_FIELDS = List.of("alwaysApply", "always_apply", "globs");

	public static final Set<String> RESERVED_SKILL_NAMES = Set.of("anthropic", "claude");

	public static final List<String> RECOMMENDED_BODY_SECTIONS = List.of("## Instructions", "## Examples");

	public static final Set<String> DEFAULT_ALLOWED_SKILL_DIRS = Set.of("agents", "references", "scripts", "assets",
			"evals", "tests", "tools", "config");

	public static final List<String> EXECUTABLE_SKILL_DIRS = List.of("scripts", "tools");

	public static final List<String> PYTHON_SCRIPT_PATTERNS = List.of("*.py");

	public static final List<String> NODE_SCRIPT_PATTERNS = List.of("*.mjs", "*.cjs", "*.js");

	public static final List<String> EXECUTABLE_SCRIPT_PATTERNS = List.of("*.py", "*.sh", "*.mjs", "*.cjs", "*.js");

	// Field length constraints
	public static final int NAME_MIN_LENGTH = 1;

	public static final int NAME_MAX_LENGTH = 64;

	public static final int TITLE_MIN_LENGTH = 1;

	public static final int TITLE_MAX_LENGTH = 256;

	public static final int DESCRIPTION_MIN_LENGTH = 1;

	public static final int DESCRIPTION_MAX_LENGTH = 1024;

	public static final int COMPATIBILITY_MAX_LENGTH = 500;

	public static final int MAX_SKILL_MD_LINES = 500;

	public static final String KEBAB_CASE_PATTERN = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$";

	public static final Pattern KEBAB_CASE = Pattern.compile(KEBAB_CASE_PATTERN);

	public static final Set<String> SPEC_SKILL_FIELDS = Set.of("name", "description", "license", "compatibility",
			"metadata", "allowed-tools");

	/** SKILL.md size thresholds (bytes, severity name), largest first. */
	public static final List<Object[]> SKILL_MD_SIZE_THRESHOLDS = List.of(new Object[] { 256_000, "high" },
			new Object[] { 50 * 1024, "medium" }, new Object[] { 32 * 1024, "low" });

	public static final int DESCRIPTION_TRIGGER_WINDOW = 60;

	public static final Pattern DESCRIPTION_TRIGGER = Pattern
		.compile("\\buse (?:this (?:skill )?|it )?(?:when|for|to|if|whenever)\\b"
				+ "|\\bwhen (?:the user|a user|you|asked)\\b|\\btriggers?\\b");

	public static final List<String> BANNED_PACKAGES = List.of("subprocess32", "pycrypto");

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
	public static final List<String> LICENSE_FILE_NAMES = List.of("LICENSE", "LICENSE.txt", "LICENSE.md", "LICENSE.rst",
			"LICENCE", "LICENCE.txt", "COPYING", "COPYING.txt", "NOTICE", "NOTICE.txt");

	public static final int LICENSE_HEADER_SCAN_LINES = 50;

	public static final Set<String> LICENSE_HEADER_EXTENSIONS = Set.of(".py", ".sh", ".js", ".ts", ".yaml", ".yml",
			".md", ".txt", ".json");

	public static final Pattern SPDX_LICENSE = Pattern.compile("SPDX-License-Identifier:\\s*([^\n]+)");

	// Unicode smuggling
	public static final Set<String> UNICODE_SCAN_EXTENSIONS = Set.of(".md", ".mdc", ".py", ".sh", ".yaml", ".yml",
			".json", ".txt", ".js", ".ts", ".html");

	public static final int BINARY_CHECK_CHUNK_SIZE = 8192;

	// Quality scoring
	public static final int QUALITY_DEFAULT_MIN_SCORE = 70;

	public static final int QUALITY_RECOMMENDED_MAX_TOKENS = 5000;

	public static final int QUALITY_CHARS_PER_TOKEN = 4;

	public static final int QUALITY_MAX_BODY_LINES = 500;

	public static final List<String> QUALITY_RESERVED_NAMES = List.of("anthropic", "claude");

	public static final Set<String> QUALITY_EXCLUDED_DIRS = Set.of("scripts", "tools", "references", "assets", "eval",
			"evals", ".evals", "results", ".results", "versions", ".versions", "tests", "examples", "docs", "resources",
			"templates", "design-system", "__pycache__", ".git", ".venv", "node_modules");

	public static final List<String> QUALITY_RESOURCE_DIRS = List.of("assets", "templates", "design-system",
			"resources");

	// Script lint
	public static final int SCRIPT_LINT_MAX_NESTING = 6;

}
