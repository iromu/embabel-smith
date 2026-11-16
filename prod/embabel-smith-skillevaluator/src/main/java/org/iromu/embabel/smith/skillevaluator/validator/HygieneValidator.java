/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;
import org.iromu.embabel.smith.skillevaluator.util.ScanPaths;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Validates code integrity: dead links, dependencies, and test-file presence.
 *
 * <p>
 * Port of {@code validators/hygiene.py}.
 */
public class HygieneValidator extends ValidatorBase {

	private static final List<String> TEST_FILE_PATTERNS = List.of("test_*.py", "*_test.py");

	private static final Pattern PKG_PATTERN = Pattern.compile("^([a-zA-Z0-9_-]+)");

	private static final Pattern INLINE_COMMENT = Pattern.compile("\\s+#");

	private static final Pattern VERSION_CONSTRAINT = Pattern.compile("[=<>!]");

	/** Same shape as the Python FRONTMATTER_PATTERN (DOTALL). */
	private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
			"^\\ufeff?---[^\\S\\r\\n]*\\r?\\n(.*?)\\r?\\n---[^\\S\\r\\n]*(?=\\r?\\n|\\z)(?:\\r?\\n)?(.*)",
			Pattern.DOTALL);

	@Override
	public String getName() {
		return "Code Integrity & Hygiene";
	}

	@Override
	public String getDescription() {
		return "Validate dead links, dependencies, and static Python test-file discovery";
	}

	@Override
	public ValidationResult validate(Path skillPath) {
		return validateFolderOrSkill(skillPath, this::validateSingleSkill, "Checking code integrity for");
	}

	private ValidationResult validateSingleSkill(Path skillPath) {
		ValidationResult result = new ValidationResult();
		result.merge(checkDeadLinks(skillPath));
		result.merge(auditDependencies(skillPath));
		result.merge(checkTestPresence(skillPath));
		return result;
	}

	// ------------------------------------------------------------------
	// Dead links
	// ------------------------------------------------------------------

	ValidationResult checkDeadLinks(Path skillPath) {
		ValidationResult result = new ValidationResult();
		List<Path> mdFiles = ScanPaths.iterScannableFiles(skillPath, Set.of(".md"));

		if (mdFiles.isEmpty()) {
			result.addSuccess("dead_links", "No markdown files found to check for links");
			return result;
		}

		result.getSummary().setFilesScanned(result.getSummary().getFilesScanned() + mdFiles.size());
		result.addSuccess("dead_links_scan", "Checking " + mdFiles.size() + " markdown files for dead links",
				Map.of("file_count", mdFiles.size()));

		for (Path mdFile : mdFiles) {
			String content;
			try {
				content = Files.readString(mdFile, StandardCharsets.UTF_8);
			}
			catch (IOException | RuntimeException e) {
				result.addWarning("Could not read " + mdFile + ": " + e);
				continue;
			}

			Set<String> seenTargets = new LinkedHashSet<>();
			Set<String> seenInvalid = new LinkedHashSet<>();
			for (String linkHref : MarkdownLinks.linkTargets(content, true)) {
				String localPath;
				try {
					localPath = normalizedLocalPath(linkHref, true, true);
				}
				catch (IllegalArgumentException e) {
					if (seenInvalid.contains(linkHref)) {
						continue;
					}
					seenInvalid.add(linkHref);
					result.addError("Invalid local link in " + linkDisplay(mdFile.getFileName().toString()) + ": "
							+ linkDisplay(linkHref) + " (absolute or drive-relative path)");
					continue;
				}
				if (localPath == null || seenTargets.contains(localPath)) {
					continue;
				}
				seenTargets.add(localPath);
				Path target = mdFile.getParent().resolve(localPath);
				boolean exists;
				try {
					exists = Files.exists(target);
				}
				catch (RuntimeException e) {
					exists = false;
				}
				if (!exists) {
					result.addError("Dead link in " + linkDisplay(mdFile.getFileName().toString()) + ": "
							+ linkDisplay(linkHref));
				}
			}
		}

		if (result.getErrors().isEmpty()) {
			result.addSuccess("dead_links", "All relative links valid in " + mdFiles.size() + " markdown file(s)",
					Map.of("files_checked", mdFiles.size()));
		}
		return result;
	}

	/**
	 * Keep untrusted link diagnostics bounded and on one physical line (Python
	 * {@code _link_display}).
	 */
	static String linkDisplay(String value) {
		int total = value.codePointCount(0, value.length());
		String truncated = value.substring(0, value.offsetByCodePoints(0, Math.min(160, total)));
		String escaped = asciiRepr(truncated);
		int escapedLength = escaped.codePointCount(0, escaped.length());
		String head = escaped.substring(0, escaped.offsetByCodePoints(0, Math.min(160, escapedLength)));
		return head + ((total > 160 || escapedLength > 160) ? "..." : "");
	}

	/**
	 * ASCII repr of a string without its surrounding quotes, mirroring Python
	 * {@code ascii()}.
	 */
	private static String asciiRepr(String value) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < value.length()) {
			int cp = value.codePointAt(i);
			switch (cp) {
				case '\\' -> sb.append("\\\\");
				case '\'' -> sb.append("\\'");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (isPrintableAscii(cp)) {
						sb.appendCodePoint(cp);
					}
					else if (cp < 0x100) {
						sb.append(String.format("\\x%02x", cp));
					}
					else if (cp < 0x10000) {
						sb.append(String.format("\\u%04x", cp));
					}
					else {
						sb.append(String.format("\\U%08x", cp));
					}
				}
			}
			i += Character.charCount(cp);
		}
		return sb.toString();
	}

	private static boolean isPrintableAscii(int cp) {
		return cp >= 0x20 && cp < 0x7f;
	}

	private static boolean isPrintable(int cp) {
		if (cp == ' ') {
			return true;
		}
		int type = Character.getType(cp);
		return type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE
				&& type != Character.PRIVATE_USE && type != Character.UNASSIGNED && type != Character.LINE_SEPARATOR
				&& type != Character.PARAGRAPH_SEPARATOR && type != Character.SPACE_SEPARATOR;
	}

	/**
	 * Normalize a local destination (Python {@code normalized_local_path}).
	 * @throws IllegalArgumentException when {@code rejectAnchors} is set and
	 * normalization produced an absolute or drive-relative path
	 */
	static String normalizedLocalPath(String href, boolean allowDirectory, boolean rejectAnchors) {
		String url = stripEdgeC0OrSpace(href);
		int colon = url.indexOf(':');
		if (colon > 0) {
			int q = url.indexOf('?');
			int h = url.indexOf('#');
			boolean beforeBoth = (q < 0 || q > colon) && (h < 0 || h > colon);
			if (beforeBoth && isValidScheme(url.substring(0, colon))) {
				return null; // explicit URL scheme
			}
		}
		if (url.startsWith("//")) {
			String netloc = url.substring(2);
			int cut = netloc.length();
			for (int i = 0; i < netloc.length(); i++) {
				char c = netloc.charAt(i);
				if (c == '/' || c == '?' || c == '#') {
					cut = i;
					break;
				}
			}
			if (!netloc.substring(0, cut).isEmpty()) {
				return null; // netloc present
			}
		}
		String path = url;
		int q = path.indexOf('?');
		int h = path.indexOf('#');
		int cut = path.length();
		if (q >= 0) {
			cut = Math.min(cut, q);
		}
		if (h >= 0) {
			cut = Math.min(cut, h);
		}
		path = path.substring(0, cut);
		if (path.startsWith("/")) {
			return null;
		}

		String decoded = percentDecode(path).replace('\\', '/');
		String normalized = posixNormpath(decoded);
		if (hasWindowsAnchor(normalized)) {
			if (rejectAnchors) {
				throw new IllegalArgumentException("absolute or drive-relative path");
			}
			return null;
		}
		if (!allowDirectory && (path.endsWith("/") || path.endsWith("/.") || path.endsWith("/.."))) {
			return null;
		}
		return normalized;
	}

	private static String stripEdgeC0OrSpace(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && value.charAt(start) <= 0x20) {
			start++;
		}
		while (end > start && value.charAt(end - 1) <= 0x20) {
			end--;
		}
		return value.substring(start, end);
	}

	private static boolean isValidScheme(String candidate) {
		if (candidate.isEmpty() || !Character.isLetter(candidate.charAt(0))) {
			return false;
		}
		for (int i = 1; i < candidate.length(); i++) {
			char c = candidate.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '+' && c != '-' && c != '.') {
				return false;
			}
		}
		return true;
	}

	/**
	 * {@code PureWindowsPath(...).anchor} check: drive ("C:"), root ("/") or UNC ("//").
	 */
	private static boolean hasWindowsAnchor(String normalized) {
		if (normalized.length() >= 2 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':') {
			return true;
		}
		return normalized.startsWith("/") || normalized.startsWith("\\") || normalized.startsWith("//");
	}

	/** Port of {@code posixpath.normpath}. */
	static String posixNormpath(String path) {
		boolean absolute = path.startsWith("/");
		int leadingSlashes = 0;
		while (leadingSlashes < path.length() && path.charAt(leadingSlashes) == '/') {
			leadingSlashes++;
		}
		Deque<String> parts = new ArrayDeque<>();
		for (String component : path.split("/")) {
			if (component.isEmpty() || component.equals(".")) {
				continue;
			}
			if (component.equals("..")) {
				if (!parts.isEmpty() && !parts.peekLast().equals("..")) {
					parts.removeLast();
				}
				else if (!absolute) {
					parts.addLast("..");
				}
			}
			else {
				parts.addLast(component);
			}
		}
		if (parts.isEmpty()) {
			return absolute ? "/" : ".";
		}
		String prefix;
		if (!absolute) {
			prefix = "";
		}
		else {
			prefix = leadingSlashes == 2 ? "//" : "/";
		}
		return prefix + String.join("/", parts);
	}

	/**
	 * Percent-decode as Python {@code unquote}: raw bytes collected, then UTF-8 decoded
	 * with replacement.
	 */
	static String percentDecode(String value) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		int i = 0;
		while (i < value.length()) {
			char c = value.charAt(i);
			if (c == '%' && i + 2 < value.length() && isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2))) {
				out.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));
				i += 3;
			}
			else {
				int cp = value.codePointAt(i);
				byte[] encoded = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
				out.write(encoded, 0, encoded.length);
				i += Character.charCount(cp);
			}
		}
		// errors="surrogateescape" keeps distinct invalid bytes distinct in Python; Java
		// substitutes U+FFFD instead. Distinct malformed destinations may alias.
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	// ------------------------------------------------------------------
	// Dependency audit
	// ------------------------------------------------------------------

	ValidationResult auditDependencies(Path skillPath) {
		ValidationResult result = new ValidationResult();
		boolean foundAny = false;

		for (String reqName : List.of("requirements.txt", "requirements-dev.txt")) {
			Path reqFile = skillPath.resolve(reqName);
			if (Files.exists(reqFile)) {
				foundAny = true;
				result.merge(checkRequirementsFile(reqFile));
			}
		}

		Path pyproject = skillPath.resolve("pyproject.toml");
		if (Files.exists(pyproject)) {
			foundAny = true;
			result.addSuccess("pyproject_toml", "Found pyproject.toml - dependencies managed by uv/pip");
		}

		if (!foundAny) {
			result.addSuccess("dependencies", "No dependency files found (requirements.txt, pyproject.toml)");
		}
		return result;
	}

	/** Audit a single requirements.txt for banned/unpinned packages. */
	ValidationResult checkRequirementsFile(Path reqFile) {
		ValidationResult result = new ValidationResult();
		String text;
		try {
			text = Files.readString(reqFile, StandardCharsets.UTF_8);
		}
		catch (IOException | RuntimeException e) {
			result.addWarning("Could not read " + reqFile + ": " + e);
			return result;
		}

		String[] lines = text.strip().split("\n", -1);
		String fileName = reqFile.getFileName().toString();
		result.addSuccess("dependency_audit", "Auditing " + fileName);
		Set<String> bannedLower = new LinkedHashSet<>();
		for (String pkg : Constants.BANNED_PACKAGES) {
			bannedLower.add(pkg.toLowerCase(Locale.ROOT));
		}

		for (int idx = 0; idx < lines.length; idx++) {
			int lineNum = idx + 1;
			String line = lines[idx].strip();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith("-")) {
				continue;
			}
			Matcher matcher = PKG_PATTERN.matcher(line);
			if (!matcher.find()) {
				continue;
			}
			String pkgName = matcher.group(1).toLowerCase(Locale.ROOT);
			// Pip strips inline comments introduced by whitespace before it parses a
			// requirement. Remove them here as well so an "@" in a comment cannot be
			// mistaken for a direct-reference separator; URL fragments remain intact
			// because their "#" is not preceded by whitespace.
			Matcher comment = INLINE_COMMENT.matcher(line);
			String logicalLine = comment.find() ? line.substring(0, comment.start()) : line;

			// Marker comparisons do not constrain the package version. Detect direct
			// references before the marker so an "@" inside a marker value cannot hide
			// an otherwise unpinned requirement.
			int semi = logicalLine.indexOf(';');
			String requirementPart = semi >= 0 ? logicalLine.substring(0, semi) : logicalLine;
			int at = requirementPart.indexOf('@');
			boolean isDirectReference = at >= 0 && !requirementPart.substring(at + 1).strip().isEmpty();

			if (bannedLower.contains(pkgName)) {
				result.addError(fileName + ":" + lineNum + " - Banned package: " + pkgName);
			}
			else if (!isDirectReference && !VERSION_CONSTRAINT.matcher(requirementPart).find()) {
				result.addWarning(fileName + ":" + lineNum + " - Unpinned: " + line);
			}
		}

		if (result.getErrors().isEmpty() && result.getWarnings().isEmpty()) {
			result.addSuccess("dependency_audit", fileName + " passed dependency audit");
		}
		return result;
	}

	// ------------------------------------------------------------------
	// Static test discovery
	// ------------------------------------------------------------------

	/** Discover conventional Python test filenames without reading or executing them. */
	ValidationResult checkTestPresence(Path skillPath) {
		ValidationResult result = new ValidationResult();
		Path skillRoot;
		try {
			skillRoot = skillPath.toRealPath();
		}
		catch (IOException e) {
			skillRoot = skillPath.toAbsolutePath().normalize();
		}

		List<Path> testFiles = new ArrayList<>();
		for (Path candidate : ScanPaths.iterScannableFiles(skillPath, Set.of(".py"))) {
			if (Files.isSymbolicLink(candidate)) {
				continue;
			}
			Path resolved;
			try {
				resolved = candidate.toRealPath();
			}
			catch (IOException e) {
				continue;
			}
			if (!Files.isRegularFile(resolved) || !resolved.startsWith(skillRoot)) {
				continue;
			}
			String name = candidate.getFileName().toString();
			for (String pattern : TEST_FILE_PATTERNS) {
				if (fnmatchcase(name, pattern)) {
					testFiles.add(candidate);
					break;
				}
			}
		}

		result.getSummary().setFilesScanned(result.getSummary().getFilesScanned() + testFiles.size());
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("test_count", testFiles.size());
		metadata.put("execution_performed", false);
		metadata.put("coverage_measured", false);
		metadata.put("patterns", List.copyOf(TEST_FILE_PATTERNS));

		String message;
		if (!testFiles.isEmpty()) {
			message = "Found " + testFiles.size() + " standard Python test-file candidate(s); "
					+ "target tests were not executed and coverage was not measured";
		}
		else {
			message = "No standard Python test-file candidates found; target tests were not executed "
					+ "and coverage was not measured. Consider adding tests.";
			result.addWarning("No standard Python test-file candidates found; consider adding tests");
		}
		result.addSuccess("test_discovery", message, metadata);
		return result;
	}

	/**
	 * Case-sensitive fnmatch: supports *, ? and [...] character classes (Python
	 * {@code fnmatchcase}).
	 */
	static boolean fnmatchcase(String name, String pattern) {
		int n = name.length();
		int p = 0;
		int star = -1;
		int match = 0;
		while (n > 0) {
			if (p < pattern.length() && pattern.charAt(p) == '?') {
				p++;
				n--;
			}
			else if (p < pattern.length() && pattern.charAt(p) == '*') {
				star = p++;
				match = n;
			}
			else if (p < pattern.length() && pattern.charAt(p) == '['
					&& classMatches(pattern, p, name.charAt(name.length() - n))) {
				p = classEnd(pattern, p);
				n--;
			}
			else if (p < pattern.length() && pattern.charAt(p) == name.charAt(name.length() - n)) {
				p++;
				n--;
			}
			else if (star >= 0) {
				p = star + 1;
				n = --match;
			}
			else {
				return false;
			}
		}
		while (p < pattern.length() && pattern.charAt(p) == '*') {
			p++;
		}
		return p == pattern.length();
	}

	private static boolean classMatches(String pattern, int start, char ch) {
		int i = start + 1;
		int end = classEnd(pattern, start);
		if (end < 0) {
			return false;
		}
		boolean negate = i < end && pattern.charAt(i) == '!';
		if (negate) {
			i++;
		}
		boolean matched = false;
		while (i < end) {
			char lo = pattern.charAt(i);
			if (lo == '\\' && i + 1 < end) {
				i++;
				lo = pattern.charAt(i);
			}
			if (i + 2 < end && pattern.charAt(i + 1) == '-' && lo != '-') {
				char hi = pattern.charAt(i + 2);
				if (hi == '\\' && i + 3 < end) {
					i++;
					hi = pattern.charAt(i + 2);
				}
				if (ch >= lo && ch <= hi) {
					matched = true;
				}
				i += 3;
			}
			else {
				if (ch == lo) {
					matched = true;
				}
				i++;
			}
		}
		return negate != matched;
	}

	/** Index just past the closing {@code ]} of a bracket class, or -1 when malformed. */
	private static int classEnd(String pattern, int start) {
		int i = start + 1;
		if (i < pattern.length() && pattern.charAt(i) == '!') {
			i++;
		}
		if (i < pattern.length() && pattern.charAt(i) == ']') {
			i++;
		}
		while (i < pattern.length()) {
			if (pattern.charAt(i) == ']') {
				return i + 1;
			}
			if (pattern.charAt(i) == '\\' && i + 1 < pattern.length()) {
				i++;
			}
			i++;
		}
		return -1;
	}

	// ------------------------------------------------------------------
	// Markdown link extraction (port of validators/markdown.py)
	// ------------------------------------------------------------------

	/**
	 * Parser-normalized Markdown destinations in document order.
	 *
	 * <p>
	 * Deviation: upstream uses markdown-it (CommonMark). This is a focused
	 * reimplementation covering inline links/images with balanced parens and
	 * angle-bracket targets, reference definitions with full/collapsed/shortcut links,
	 * and {@code <a href>} HTML anchors, while keeping frontmatter, fenced code, inline
	 * code, HTML comments, and non-navigation HTML tags inert. Unusual CommonMark corners
	 * may extract different targets.
	 */
	static final class MarkdownLinks {

		/**
		 * Python _TEXT_ONLY_HTML_TAGS plus the inert {@code template} tag
		 * (_NON_NAVIGATION_HTML_TAGS).
		 */
		private static final Set<String> NON_NAVIGATION_HTML_TAGS = Set.of("script", "style", "textarea", "title",
				"xmp", "iframe", "noembed", "noframes", "plaintext", "template");

		private static final Pattern FRONTMATTER_YAML = FRONTMATTER_PATTERN;

		private static final Pattern LINK_DEFINITION = Pattern.compile(
				"^ {0,3}\\[([^\\]]+)\\]:[ \\t]*(?:<([^>\\n]*)>|(\\S+))(?:[ \\t]+(?:\"[^\"\\n]*\"|'[^'\\n]*'|\\([^)\\n]*\\)))?[ \\t]*$");

		private static final Pattern ANCHOR_HREF = Pattern.compile(
				"<a(?:\\s[^>]*?)?href[ \\t]*=[ \\t]*(?:\"([^\"]*)\"|'([^']*)'|([^ \\t>]+))", Pattern.CASE_INSENSITIVE);

		private MarkdownLinks() {
		}

		static List<String> linkTargets(String content, boolean includeImages) {
			String body = stripFrontmatter(content);
			char[] text = body.toCharArray();
			blankFencedCode(text);
			blankHtmlComments(text);
			blankInertHtml(text);
			blankCodeSpans(text);
			String sanitized = new String(text);

			Map<String, String> definitions = new LinkedHashMap<>();
			char[] withoutDefs = sanitized.toCharArray();
			int offset = 0;
			for (String line : sanitized.split("\n", -1)) {
				Matcher m = LINK_DEFINITION.matcher(line);
				if (m.matches()) {
					String label = normalizeLabel(m.group(1));
					String dest = m.group(2) != null ? m.group(2) : m.group(3);
					if (label != null && !label.isEmpty() && dest != null) {
						definitions.putIfAbsent(label, unescapeMarkdown(dest));
					}
					for (int i = offset; i < offset + line.length(); i++) {
						withoutDefs[i] = ' ';
					}
				}
				offset += line.length() + 1;
			}
			String doc = new String(withoutDefs);

			List<String> targets = new ArrayList<>();
			scanInlineAndReferenceLinks(doc, definitions, includeImages, targets);
			Matcher anchors = ANCHOR_HREF.matcher(doc);
			while (anchors.find()) {
				String href = anchors.group(1) != null ? anchors.group(1)
						: anchors.group(2) != null ? anchors.group(2) : anchors.group(3);
				targets.add(unescapeHtml(href));
			}
			return targets;
		}

		private static String stripFrontmatter(String content) {
			Matcher m = FRONTMATTER_YAML.matcher(content);
			if (m.lookingAt()) {
				try {
					Object data = new Yaml(new SafeConstructor(new LoaderOptions())).load(m.group(1));
					if (data instanceof Map) {
						return m.group(2);
					}
				}
				catch (RuntimeException ignored) {
					// fall through: keep the whole document
				}
			}
			return content;
		}

		private static void blank(char[] text, int from, int to) {
			for (int i = Math.max(0, from); i < Math.min(text.length, to); i++) {
				if (text[i] != '\n' && text[i] != '\r') {
					text[i] = ' ';
				}
			}
		}

		/**
		 * Blank fenced code blocks (including the fence lines), from fence-open to
		 * fence-close.
		 */
		private static void blankFencedCode(char[] text) {
			String s = new String(text);
			int lineStart = 0;
			while (lineStart <= s.length()) {
				int lineEnd = s.indexOf('\n', lineStart);
				if (lineEnd < 0) {
					lineEnd = s.length();
				}
				int[] open = fenceRun(s.substring(lineStart, lineEnd));
				if (open == null) {
					if (lineEnd >= s.length()) {
						return;
					}
					lineStart = lineEnd + 1;
					continue;
				}
				char marker = (char) open[2];
				int length = open[1];
				int scan = lineEnd < s.length() ? lineEnd + 1 : s.length();
				int closeLineEnd = -1;
				while (scan <= s.length()) {
					int next = s.indexOf('\n', scan);
					if (next < 0) {
						next = s.length();
					}
					int[] close = fenceRun(s.substring(scan, next));
					if (close != null && close[1] >= length && (char) close[2] == marker) {
						String body = s.substring(scan, next).stripLeading();
						if (body.substring(countRun(body, marker, 0)).isBlank()) {
							closeLineEnd = next;
							break;
						}
					}
					if (next >= s.length()) {
						break;
					}
					scan = next + 1;
				}
				int end = closeLineEnd >= 0 ? closeLineEnd : s.length();
				blank(text, lineStart, end < s.length() ? end + 1 : end);
				if (closeLineEnd < 0) {
					return;
				}
				lineStart = closeLineEnd + 1;
			}
		}

		/**
		 * For a fence candidate line: {indent, runLength, markerChar}, or null when not a
		 * fence.
		 */
		private static int[] fenceRun(String line) {
			int indent = 0;
			while (indent < line.length() && line.charAt(indent) == ' ' && indent < 4) {
				indent++;
			}
			if (indent >= 4 || indent >= line.length()) {
				return null;
			}
			char marker = line.charAt(indent);
			if (marker != '`' && marker != '~') {
				return null;
			}
			int run = countRun(line, marker, indent);
			if (run < 3) {
				return null;
			}
			String rest = line.substring(indent + run);
			if (marker == '`' && !rest.isBlank() && rest.contains("`")) {
				return null; // backtick fence info strings must not contain a backtick
			}
			return new int[] { indent, run, marker };
		}

		private static int countRun(String s, char ch, int from) {
			int n = 0;
			while (from + n < s.length() && s.charAt(from + n) == ch) {
				n++;
			}
			return n;
		}

		private static void blankHtmlComments(char[] text) {
			String s = new String(text);
			int pos = 0;
			while (true) {
				int start = s.indexOf("<!--", pos);
				if (start < 0) {
					return;
				}
				int end = s.indexOf("-->", start + 4);
				int stop = end < 0 ? s.length() : end + 3;
				blank(text, start, stop);
				pos = stop;
			}
		}

		private static void blankInertHtml(char[] text) {
			String lower = new String(text).toLowerCase(Locale.ROOT);
			for (String tag : NON_NAVIGATION_HTML_TAGS) {
				int pos = 0;
				while (true) {
					int start = indexOfTag(lower, "<" + tag, pos);
					if (start < 0) {
						break;
					}
					int end = lower.indexOf("</" + tag, start + tag.length() + 1);
					if (end < 0) {
						blank(text, start, text.length);
						break;
					}
					int gt = lower.indexOf('>', end);
					blank(text, start, gt < 0 ? text.length : gt + 1);
					pos = gt < 0 ? text.length : gt + 1;
				}
			}
		}

		/**
		 * Match {@code prefix} only when followed by a tag-name delimiter (or line end).
		 */
		private static int indexOfTag(String haystack, String prefix, int from) {
			int pos = from;
			while (true) {
				pos = haystack.indexOf(prefix, pos);
				if (pos < 0) {
					return -1;
				}
				int after = pos + prefix.length();
				if (after >= haystack.length() || " \t\n\r\f/>".indexOf(haystack.charAt(after)) >= 0) {
					return pos;
				}
				pos = after;
			}
		}

		private static void blankCodeSpans(char[] text) {
			int i = 0;
			while (i < text.length) {
				if (text[i] != '`') {
					i++;
					continue;
				}
				int run = countRun(text, '`', i);
				int search = i + run;
				int close = -1;
				while (search < text.length) {
					if (text[search] == '`') {
						int candidate = countRun(text, '`', search);
						if (candidate == run) {
							close = search;
							break;
						}
						search += candidate;
					}
					else {
						search++;
					}
				}
				if (close > 0) {
					blank(text, i, close + run);
					i = close + run;
				}
				else {
					i += run;
				}
			}
		}

		private static int countRun(char[] text, char ch, int from) {
			int n = 0;
			while (from + n < text.length && text[from + n] == ch) {
				n++;
			}
			return n;
		}

		private static void scanInlineAndReferenceLinks(String doc, Map<String, String> definitions,
				boolean includeImages, List<String> targets) {
			int i = 0;
			int n = doc.length();
			while (i < n) {
				char c = doc.charAt(i);
				if (c == '\\') {
					i += 2;
					continue;
				}
				if (c != '[') {
					i++;
					continue;
				}
				boolean image = i > 0 && doc.charAt(i - 1) == '!';
				int close = matchBracket(doc, i);
				if (close < 0) {
					i++;
					continue;
				}
				String inner = doc.substring(i + 1, close);
				int j = close + 1;
				if (j < n && doc.charAt(j) == '(') {
					int end = findParenEnd(doc, j);
					if (end > 0) {
						String dest = parseLinkDestination(doc.substring(j + 1, end));
						if (!image || includeImages) {
							targets.add(dest);
						}
						i = end + 1;
						continue;
					}
				}
				String label = null;
				if (j < n && doc.charAt(j) == '[') {
					int labelEnd = doc.indexOf(']', j + 1);
					if (labelEnd >= 0) {
						String explicit = doc.substring(j + 1, labelEnd).strip();
						label = explicit.isEmpty() ? normalizeLabel(inner) : normalizeLabel(explicit);
						i = labelEnd + 1;
					}
					else {
						i = close + 1;
					}
				}
				else {
					label = normalizeLabel(inner);
					i = close + 1;
				}
				if (label != null && definitions.containsKey(label) && (!image || includeImages)) {
					targets.add(definitions.get(label));
				}
			}
		}

		private static int matchBracket(String doc, int open) {
			int depth = 1;
			int k = open + 1;
			while (k < doc.length()) {
				char c = doc.charAt(k);
				if (c == '\\') {
					k += 2;
					continue;
				}
				if (c == '[') {
					depth++;
				}
				else if (c == ']') {
					depth--;
					if (depth == 0) {
						return k;
					}
				}
				k++;
			}
			return -1;
		}

		private static int findParenEnd(String doc, int open) {
			int depth = 1;
			boolean inAngle = false;
			int k = open + 1;
			while (k < doc.length()) {
				char c = doc.charAt(k);
				if (c == '\\') {
					k += 2;
					continue;
				}
				if (c == '<') {
					inAngle = true;
				}
				else if (c == '>') {
					inAngle = false;
				}
				else if (!inAngle) {
					if (c == '(') {
						depth++;
					}
					else if (c == ')') {
						depth--;
						if (depth == 0) {
							return k;
						}
					}
				}
				k++;
			}
			return -1;
		}

		private static String parseLinkDestination(String raw) {
			String s = raw.strip();
			if (s.startsWith("<")) {
				int gt = s.indexOf('>');
				if (gt >= 0) {
					return unescapeMarkdown(s.substring(1, gt));
				}
			}
			int cut = s.length();
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (Character.isWhitespace(c)) {
					cut = i;
					break;
				}
			}
			return unescapeMarkdown(s.substring(0, cut));
		}

		private static String unescapeMarkdown(String value) {
			return value.replaceAll("\\\\([!\"#$%&'()*+,\\-./:;<=>?@\\[\\\\\\]^_`{|}~])", "$1");
		}

		private static String normalizeLabel(String label) {
			return label.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		}

		private static String unescapeHtml(String value) {
			if (value == null || value.indexOf('&') < 0) {
				return value;
			}
			String out = value.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&#39;", "'")
				.replace("&apos;", "'")
				.replace("&amp;", "&");
			Matcher m = Pattern.compile("&#([0-9]+|[xX][0-9a-fA-F]+);").matcher(out);
			StringBuilder sb = new StringBuilder();
			int last = 0;
			while (m.find()) {
				sb.append(out, last, m.start());
				String num = m.group(1);
				int cp = num.charAt(0) == 'x' || num.charAt(0) == 'X' ? Integer.parseUnsignedInt(num.substring(1), 16)
						: Integer.parseUnsignedInt(num);
				sb.append(cp > 0 && cp <= 0x10FFFF ? new String(Character.toChars(cp)) : m.group());
				last = m.end();
			}
			sb.append(out.substring(last));
			return sb.toString();
		}

	}

}
