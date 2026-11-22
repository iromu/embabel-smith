/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * YAML frontmatter extraction for .md/.mdc files.
 * <p>
 * Port of {@code validators/frontmatter_parser.py}.
 *
 * @author Ivan Rodriguez
 */
public final class Frontmatter {

	/** Same shape as the Python FRONTMATTER_PATTERN (DOTALL). */
	private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
			"^\\ufeff?---[^\\S\\r\\n]*\\r?\\n(.*?)\\r?\\n---[^\\S\\r\\n]*(?=\\r?\\n|\\z)(?:\\r?\\n)?(.*)",
			Pattern.DOTALL);

	private Frontmatter() {
	}

	@SuppressWarnings("unchecked")
	public static FrontmatterOutcome parse(Path filePath) {
		ValidationResult result = new ValidationResult();
		String content;
		try {
			content = Files.readString(filePath, StandardCharsets.UTF_8);
		}
		catch (IOException | IllegalArgumentException ex) {
			result.addError("Failed to read " + filePath + ": " + ex.getMessage());
			return new FrontmatterOutcome(null, result);
		}
		// readString(UTF_8) keeps a leading BOM; the pattern tolerates it, but
		// strip it so raw text offsets match the Python utf-8-sig read.
		if (content.startsWith("\uFEFF")) {
			content = content.substring(1);
		}
		Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
		if (!matcher.lookingAt()) {
			result.addError(filePath.getFileName() + " must have YAML frontmatter between --- markers");
			return new FrontmatterOutcome(null, result);
		}
		String frontmatterYaml = matcher.group(1);
		String markdownContent = matcher.group(2);

		Object data;
		try {
			LoaderOptions options = new LoaderOptions();
			options.setAllowDuplicateKeys(true);
			data = new Yaml(new SafeConstructor(options)).load(frontmatterYaml);
		}
		catch (RuntimeException ex) {
			result.addError("Invalid YAML in frontmatter: " + ex.getMessage());
			return new FrontmatterOutcome(null, result);
		}
		if (!(data instanceof Map)) {
			result.addError("Frontmatter must be a non-empty YAML dictionary");
			return new FrontmatterOutcome(null, result);
		}
		int lineCount = content.isEmpty() ? 0 : content.split("\r\n|\r|\n", -1).length;
		if (content.isEmpty()) {
			lineCount = 0;
		}
		return new FrontmatterOutcome(
				new ParsedFrontmatter((Map<String, Object>) data, markdownContent, frontmatterYaml, lineCount), result);
	}

	/**
	 * Read a whole file as UTF-8 text (BOM stripped), or null on failure.
	 * @param path the file to read
	 * @return its text, or {@code null} when it cannot be read or decoded
	 */
	public static String readUtf8(Path path) {
		try {
			String text = Files.readString(path, StandardCharsets.UTF_8);
			return text.startsWith("\uFEFF") ? text.substring(1) : text;
		}
		catch (IOException | IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Split text into lines the way Python's {@code splitlines()} does for \n / \r\n /
	 * \r.
	 * @param text the text to split
	 * @return the segments between separators; empty for empty input, and a trailing
	 * separator yields a final empty segment
	 */
	public static List<String> splitLines(String text) {
		if (text.isEmpty()) {
			return List.of();
		}
		return List.of(text.split("\r\n|\r|\n", -1));
	}

	/**
	 * Result of parsing a file's frontmatter.
	 *
	 * @param yamlData the frontmatter mapping as parsed
	 * @param content markdown body following the closing {@code ---}
	 * @param rawYaml the frontmatter text between the markers, as written
	 * @param lineCount lines in the whole file, {@code 0} for an empty one
	 * @author Ivan Rodriguez
	 */
	public record ParsedFrontmatter(Map<String, Object> yamlData, String content, String rawYaml, int lineCount) {
	}

	/**
	 * Outcome of a parse: either a parsed document or the errors explaining the failure.
	 *
	 * @param parsed the parsed document, or {@code null} when parsing failed
	 * @param result errors and warnings gathered while parsing; empty on success
	 * @author Ivan Rodriguez
	 */
	public record FrontmatterOutcome(ParsedFrontmatter parsed, ValidationResult result) {
		public boolean isOk() {
			return this.parsed != null;
		}
	}

}
