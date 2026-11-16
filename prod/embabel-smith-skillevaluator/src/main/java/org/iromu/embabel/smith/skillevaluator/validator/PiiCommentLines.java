/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

/**
 * The comment-line model behind the PII scan, ported from the module-level helpers of
 * upstream {@code validators/security.py} ({@code _comment_line_numbers} and its
 * per-language helpers).
 *
 * <p>
 * The PII scan skips lines that are comments in the file's own syntax, so an address
 * written in a {@code #} comment is not reported while the same address inside a string
 * literal is. Which lines count is therefore language specific: Python, YAML, shell
 * (heredoc aware) and Markdown (frontmatter plus fenced code, each fence parsed in its
 * own language).
 *
 * <h2>Documented deviations</h2>
 *
 * <ul>
 * <li><b>Python: a line model, not {@code tokenize}.</b> Upstream runs the
 * standard-library tokenizer and keeps the lines whose COMMENT token sits on a physical
 * line that itself starts with {@code #}. Java embeds no Python parser, so this walks the
 * file with a string/triple-quote and bracket-depth state machine instead: a line is
 * skipped when its first non-space character is {@code #} and it does not begin inside an
 * open triple-quoted string. That reproduces the tokenizer verdict for every comment
 * Python records, plus the two cases the tokenizer raises on (an unterminated string or
 * an unclosed bracket at EOF) fall back to the same leading-{@code #}-or-{@code //} rule
 * upstream falls back to. Errors that are neither — a bad indent, a stray keyword —
 * cannot be detected without a grammar, so such a file gets the tokenizer-style answer
 * here rather than the fallback.</li>
 * <li><b>YAML scalar coverage comes from snakeyaml.</b> Upstream asks PyYAML's event
 * stream which lines a scalar covers, so a {@code #} inside a block or quoted scalar is
 * content and stays scanned. The port asks snakeyaml's parser for the same scalar marks
 * (its event classes live in {@code org.yaml.snakeyaml.events}, and a block scalar is
 * {@code DumperOptions.ScalarStyle.LITERAL} or {@code FOLDED} where PyYAML says
 * {@code style in ('|', '>')}). The two scanners agree on the shapes the upstream tests
 * cover (plain, quoted, multi-line quoted, block scalars with explicit indentation, and a
 * comment after a sequence block scalar); a disagreement would show up as an extra or
 * missing skipped comment line, never as a changed pattern match.</li>
 * <li><b>Line ends.</b> Like upstream, the lines are a split on {@code \n}. The caller
 * reads files in Python's universal-newline mode, so a CRLF skill arrives here as LF text
 * and no fence or comment line ends in {@code \r}.</li>
 * </ul>
 */
final class PiiCommentLines {

	/** Python {@code _HEREDOC_OPEN}. */
	private static final Pattern HEREDOC_OPEN = Pattern
		.compile("<<-?(?!<)\\s*(?:'([^']+)'|\"([^\"]+)\"|\\\\?([A-Za-z_][A-Za-z0-9_]*))");

	/**
	 * Python {@code _FENCE_OPEN}, anchored with {@code \A}/{@code \z} (see class
	 * javadoc).
	 */
	private static final Pattern FENCE_OPEN = Pattern.compile("\\A( ?{0,3})(`{3,}|~{3,})(.*)\\z");

	private static final Set<String> PYTHON_FENCE_LANGS = Set.of("python", "py", "python3");

	private static final Set<String> YAML_FENCE_LANGS = Set.of("yaml", "yml");

	private static final Set<String> SHELL_FENCE_LANGS = Set.of("sh", "bash", "shell", "zsh");

	private PiiCommentLines() {
	}

	/**
	 * Python {@code _comment_line_numbers}: lines to skip as comments for this file's
	 * syntax.
	 */
	static Set<Integer> commentLineNumbers(Path filePath, List<String> lines) {
		String name = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		String suffix = dot < 0 ? "" : name.substring(dot);
		switch (suffix) {
			case ".py" -> {
				return pythonCommentLineNumbers(lines);
			}
			case ".yaml", ".yml" -> {
				return yamlCommentLineNumbers(lines);
			}
			case ".sh" -> {
				return shellCommentLineNumbers(lines);
			}
			case ".md", ".mdc", ".markdown" -> {
				return markdownCommentLineNumbers(lines);
			}
			default -> {
				return leadingHashOrSlashCommentLines(lines);
			}
		}
	}

	/** Python {@code _leading_hash_or_slash_comment_lines}. */
	static Set<Integer> leadingHashOrSlashCommentLines(List<String> lines) {
		Set<Integer> out = new LinkedHashSet<>();
		for (int i = 0; i < lines.size(); i++) {
			String stripped = lines.get(i).strip();
			if (stripped.startsWith("#") || stripped.startsWith("//")) {
				out.add(i + 1);
			}
		}
		return out;
	}

	/** Python {@code _shift_line_numbers}. */
	private static Set<Integer> shiftLineNumbers(Set<Integer> lineNumbers, int offset) {
		Set<Integer> out = new LinkedHashSet<>();
		for (int number : lineNumbers) {
			out.add(number + offset);
		}
		return out;
	}

	/**
	 * Python {@code _python_comment_line_numbers}: the tokenizer verdict, modelled as
	 * "first non-space character is {@code #} outside an open triple-quoted string", with
	 * the TokenError fallback when a string or bracket is still open at end of file.
	 */
	static Set<Integer> pythonCommentLineNumbers(List<String> lines) {
		Set<Integer> skip = new LinkedHashSet<>();
		// 0 = code, 1 = in """ string, 2 = in ''' string
		int openQuote = 0;
		int depth = 0;
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			// Inside an open docstring a leading hash is prose, not a comment token.
			if (openQuote == 0 && line.stripLeading().startsWith("#")) {
				skip.add(index + 1);
			}
			int i = 0;
			if (openQuote != 0) {
				int closed = consumeTriple(line, 0, openQuote);
				if (closed < 0) {
					continue; // the whole line is string content
				}
				openQuote = 0;
				i = closed;
			}
			while (i < line.length()) {
				char c = line.charAt(i);
				if (c == '#') {
					break; // the rest of the line is a comment
				}
				if (c == '\'' || c == '"') {
					if (line.startsWith(String.valueOf(c).repeat(3), i)) {
						int end = consumeTriple(line, i + 3, c == '"' ? 1 : 2);
						if (end < 0) {
							openQuote = c == '"' ? 1 : 2;
							break; // unterminated for now: the rest of the line is string
						}
						i = end;
						continue;
					}
					i = consumeQuoted(line, i);
					if (i < 0) {
						// Python's tokenizer raises here; treat the rest as string.
						i = line.length();
						break;
					}
					continue;
				}
				if (c == '(' || c == '[' || c == '{') {
					depth++;
				}
				else if (c == ')' || c == ']' || c == '}') {
					depth--;
				}
				i++;
			}
		}
		if (openQuote != 0 || depth > 0) {
			// TokenError / IndentationError fallback in the Python version.
			return leadingHashOrSlashCommentLines(lines);
		}
		return skip;
	}

	/**
	 * Index just past the closing triple quote starting at {@code from}, or -1 when
	 * unclosed.
	 */
	private static int consumeTriple(String line, int from, int kind) {
		String marker = kind == 1 ? "\"\"\"" : "'''";
		int i = from;
		while (i < line.length()) {
			if (line.charAt(i) == '\\') {
				i += 2;
				continue;
			}
			if (line.startsWith(marker, i)) {
				return i + 3;
			}
			i++;
		}
		return -1;
	}

	/**
	 * Index just past a single- or double-quoted string starting at {@code from}, or -1
	 * when unterminated.
	 */
	private static int consumeQuoted(String line, int from) {
		char quote = line.charAt(from);
		int i = from + 1;
		while (i < line.length()) {
			char c = line.charAt(i);
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == quote) {
				return i + 1;
			}
			if (c == '\n') {
				return -1;
			}
			i++;
		}
		return -1;
	}

	/**
	 * Python {@code _yaml_scalar_content_lines}: 1-based lines covered by YAML scalars,
	 * or null when the document cannot be parsed.
	 */
	static Set<Integer> yamlScalarContentLines(String source) {
		try {
			Set<Integer> covered = new LinkedHashSet<>();
			Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
			for (Event event : yaml.parse(new StringReader(source))) {
				if (!(event instanceof ScalarEvent scalar)) {
					continue;
				}
				int start = scalar.getStartMark().getLine();
				int end = scalar.getEndMark().getLine() - (scalar.getEndMark().getColumn() == 0 ? 1 : 0);
				for (int index = start; index <= Math.max(start, end); index++) {
					covered.add(index + 1);
				}
			}
			return covered;
		}
		catch (RuntimeException | StackOverflowError e) {
			return null;
		}
	}

	/**
	 * Python {@code _yaml_comment_line_numbers}: {@code #} comments outside scalar
	 * content.
	 */
	static Set<Integer> yamlCommentLineNumbers(List<String> lines) {
		Set<Integer> covered = yamlScalarContentLines(String.join("\n", lines));
		Set<Integer> out = new LinkedHashSet<>();
		if (covered == null) {
			return out;
		}
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).stripLeading().startsWith("#") && !covered.contains(i + 1)) {
				out.add(i + 1);
			}
		}
		return out;
	}

	/**
	 * Python {@code _shell_comment_line_numbers}: {@code #} comments, excluding heredoc
	 * payloads.
	 */
	static Set<Integer> shellCommentLineNumbers(List<String> lines) {
		Set<Integer> skip = new LinkedHashSet<>();
		String delimiter = null;
		boolean stripTabs = false;
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			if (delimiter != null) {
				String candidate = rstripNewlines(line);
				if (stripTabs) {
					candidate = candidate.replaceAll("^\t+", "");
				}
				if (candidate.equals(delimiter)) {
					delimiter = null;
				}
				continue;
			}
			if (line.stripLeading().startsWith("#")) {
				skip.add(index + 1);
				continue;
			}
			Matcher opened = HEREDOC_OPEN.matcher(line);
			if (opened.find()) {
				delimiter = firstNonNull(opened.group(1), opened.group(2), opened.group(3));
				stripTabs = opened.group().contains("<<-");
			}
		}
		return skip;
	}

	/** Python {@code _fence_language}. */
	private static String fenceLanguage(String info) {
		String trimmed = info.strip();
		if (trimmed.isEmpty()) {
			return "";
		}
		String token = trimmed.split("\\s+")[0];
		return token.replaceAll("^[{.]+", "").replaceAll("[}.]+$", "").toLowerCase(Locale.ROOT);
	}

	/** Python {@code _is_closing_fence}. */
	private static boolean isClosingFence(String line, String marker) {
		String stripped = line.strip();
		if (stripped.length() < marker.length()) {
			return false;
		}
		char only = marker.charAt(0);
		return stripped.chars().allMatch(c -> c == only);
	}

	/** Python {@code _comments_for_language}. */
	private static Set<Integer> commentsForLanguage(String language, List<String> lines) {
		if (PYTHON_FENCE_LANGS.contains(language)) {
			return pythonCommentLineNumbers(lines);
		}
		if (YAML_FENCE_LANGS.contains(language)) {
			return yamlCommentLineNumbers(lines);
		}
		if (SHELL_FENCE_LANGS.contains(language)) {
			return shellCommentLineNumbers(lines);
		}
		return leadingHashOrSlashCommentLines(lines);
	}

	/** Python {@code _markdown_frontmatter_end}. */
	private static Integer markdownFrontmatterEnd(List<String> lines) {
		if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
			return null;
		}
		for (int index = 1; index < lines.size(); index++) {
			if (lines.get(index).strip().equals("---")) {
				return index;
			}
		}
		return null;
	}

	/** Python {@code _markdown_fence_comment_line_numbers}. */
	private static Set<Integer> markdownFenceCommentLineNumbers(List<String> lines, int start) {
		Set<Integer> skip = new LinkedHashSet<>();
		int index = start;
		while (index < lines.size()) {
			Matcher match = FENCE_OPEN.matcher(lines.get(index));
			if (!match.matches()) {
				index++;
				continue;
			}
			String marker = match.group(2);
			String language = fenceLanguage(match.group(3));
			int bodyStart = index + 1;
			int bodyEnd = bodyStart;
			while (bodyEnd < lines.size() && !isClosingFence(lines.get(bodyEnd), marker)) {
				bodyEnd++;
			}
			skip.addAll(shiftLineNumbers(commentsForLanguage(language, lines.subList(bodyStart, bodyEnd)), bodyStart));
			index = bodyEnd + 1;
		}
		return skip;
	}

	/** Python {@code _markdown_comment_line_numbers}. */
	static Set<Integer> markdownCommentLineNumbers(List<String> lines) {
		Set<Integer> skip = new LinkedHashSet<>();
		int bodyAt = 0;
		Integer fmEnd = markdownFrontmatterEnd(lines);
		if (fmEnd != null) {
			skip.addAll(shiftLineNumbers(yamlCommentLineNumbers(lines.subList(1, fmEnd)), 1));
			bodyAt = fmEnd + 1;
		}
		skip.addAll(markdownFenceCommentLineNumbers(lines, bodyAt));
		return skip;
	}

	/** Python {@code str.rstrip("\r\n")}. */
	private static String rstripNewlines(String value) {
		int end = value.length();
		while (end > 0 && (value.charAt(end - 1) == '\r' || value.charAt(end - 1) == '\n')) {
			end--;
		}
		return value.substring(0, end);
	}

	private static String firstNonNull(String... values) {
		for (String value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}

}
