/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;
import org.iromu.embabel.smith.skillevaluator.util.ScanPaths;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Detects invisible Unicode characters that may indicate ASCII smuggling, hidden data
 * encoding, or trojan source attacks.
 *
 * <p>
 * Port of {@code validators/unicode_smuggle.py}. Character categories and thresholds come
 * from {@code org/skillevaluator/config/unicode_smuggle_patterns.yaml} on the classpath.
 */
public class UnicodeSmuggleValidator extends ValidatorBase {

	/**
	 * MIME types that are textual even when not under text/* (Python
	 * {@code _TEXTUAL_MIME_TYPES}).
	 */
	private static final Set<String> TEXTUAL_MIME_TYPES = Set.of("application/json", "application/ld+json",
			"application/x-ndjson", "application/xml", "application/javascript", "application/x-javascript",
			"application/x-sh", "application/x-yaml", "application/yaml", "application/toml", "image/svg+xml");

	/**
	 * Extension → MIME mappings reproducing Python {@code mimetypes.guess_type} for the
	 * extensions {@link Constants#UNICODE_SCAN_EXTENSIONS} walks (plus a few common
	 * ones). The quirky entries ({@code .mdc}, {@code .ts}) are deliberate: Python's
	 * guess_type classifies {@code .mdc} as a non-text MIME (so such files are skipped as
	 * "binary") and {@code .ts} as text.
	 */
	private static final Map<String, String> MIME_BY_EXTENSION = Map.ofEntries(Map.entry("md", "text/markdown"),
			Map.entry("mdc", "application/vnd.marlin.drm.mdcf"), Map.entry("py", "text/x-python"),
			Map.entry("sh", "application/x-sh"), Map.entry("yaml", "application/yaml"),
			Map.entry("yml", "application/yaml"), Map.entry("json", "application/json"), Map.entry("txt", "text/plain"),
			Map.entry("js", "text/javascript"), Map.entry("mjs", "text/javascript"),
			Map.entry("ts", "text/vnd.trolltech.linguist"), Map.entry("html", "text/html"),
			Map.entry("htm", "text/html"), Map.entry("css", "text/css"), Map.entry("xml", "application/xml"),
			Map.entry("svg", "image/svg+xml"), Map.entry("csv", "text/csv"), Map.entry("png", "image/png"),
			Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"));

	private final boolean includeSpaces;

	private Map<String, Object> config;

	private Map<Integer, String[]> charLookup;

	private int[] tagRange;

	private int[] tagAsciiRange;

	private int[] vsBasicRange;

	private int[] vsSupplementRange;

	private Map<String, Object> thresholds = Map.of();

	public UnicodeSmuggleValidator() {
		this(false);
	}

	public UnicodeSmuggleValidator(boolean includeSpaces) {
		this.includeSpaces = includeSpaces;
	}

	@Override
	public String getName() {
		return "Unicode Smuggling Detection";
	}

	@Override
	public String getDescription() {
		return "Detect invisible Unicode characters and ASCII smuggling";
	}

	@Override
	public ValidationResult validate(Path skillPath) {
		return validateFolderOrSkill(skillPath, this::validateSingle, "Unicode scanning");
	}

	/** Lazy config load, mirroring the Python {@code config} property. */
	@SuppressWarnings("unchecked")
	private Map<String, Object> getConfig() {
		if (config == null) {
			config = UnicodeSmuggleConfig.load();
			buildLookupTables();
		}
		return config;
	}

	@SuppressWarnings("unchecked")
	private void buildLookupTables() {
		Map<String, Object> categories = asMap(config.get("categories"));
		charLookup = new LinkedHashMap<>();

		// Explicit character maps
		for (Map.Entry<String, Object> entry : categories.entrySet()) {
			Map<String, Object> chars = asMap(asMap(entry.getValue()).get("characters"));
			for (Map.Entry<String, Object> charEntry : chars.entrySet()) {
				int codePoint = toCodePoint(charEntry.getKey());
				charLookup.put(codePoint, new String[] { entry.getKey(), String.valueOf(charEntry.getValue()) });
			}
		}

		// Range-based categories
		Map<String, Object> tags = asMap(categories.get("unicode_tags"));
		tagRange = new int[] { hexToInt(tags.getOrDefault("range_start", 0xE0000)),
				hexToInt(tags.getOrDefault("range_end", 0xE007F)) };
		tagAsciiRange = new int[] { hexToInt(tags.getOrDefault("ascii_decodable_start", 0xE0020)),
				hexToInt(tags.getOrDefault("ascii_decodable_end", 0xE007E)) };

		Map<String, Object> vs = asMap(categories.get("variation_selectors"));
		vsBasicRange = new int[] { hexToInt(vs.getOrDefault("range_basic_start", 0xFE00)),
				hexToInt(vs.getOrDefault("range_basic_end", 0xFE0F)) };
		vsSupplementRange = new int[] { hexToInt(vs.getOrDefault("range_supplement_start", 0xE0100)),
				hexToInt(vs.getOrDefault("range_supplement_end", 0xE01EF)) };

		// Optional confusable spaces
		if (includeSpaces) {
			Map<String, Object> opt = asMap(asMap(config.get("optional_categories")).get("confusable_spaces"));
			Map<String, Object> chars = asMap(opt.get("characters"));
			for (Map.Entry<String, Object> charEntry : chars.entrySet()) {
				int codePoint = toCodePoint(charEntry.getKey());
				charLookup.putIfAbsent(codePoint,
						new String[] { "confusable_spaces", String.valueOf(charEntry.getValue()) });
			}
		}

		thresholds = asMap(config.get("thresholds"));
	}

	private static int hexToInt(Object value) {
		if (value instanceof Number n) {
			return n.intValue();
		}
		String s = String.valueOf(value).trim();
		if (s.toLowerCase(Locale.ROOT).startsWith("0x")) {
			s = s.substring(2);
		}
		return Integer.parseUnsignedInt(s, 16);
	}

	private static int toCodePoint(Object key) {
		if (key instanceof Number n) {
			return n.intValue();
		}
		String s = String.valueOf(key).trim();
		if (s.toLowerCase(Locale.ROOT).startsWith("0x")) {
			s = s.substring(2);
		}
		return Integer.parseUnsignedInt(s, 16);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
	}

	private ValidationResult validateSingle(Path skillPath) {
		getConfig(); // ensure lookup tables are built
		ValidationResult result = new ValidationResult();
		List<Path> files = ScanPaths.iterScannableFiles(skillPath, Constants.UNICODE_SCAN_EXTENSIONS);

		if (files.isEmpty()) {
			result.addWarning("No scannable files found for Unicode scan");
			return result;
		}

		result.getSummary().setFilesScanned(files.size());
		boolean smuggleFound = false;

		for (Path filePath : files) {
			String relativePath;
			try {
				relativePath = skillPath.relativize(filePath).toString();
			}
			catch (IllegalArgumentException e) {
				relativePath = filePath.getFileName().toString();
			}
			for (Finding finding : scanFile(filePath, relativePath)) {
				smuggleFound = true;
				boolean isError = finding.getSeverity() == Severity.CRITICAL || finding.getSeverity() == Severity.HIGH;
				result.addStructuredFinding(finding, isError);
			}
		}

		if (!smuggleFound) {
			result.addSuccess("unicode_scan",
					"No invisible Unicode characters detected in " + files.size() + " file(s)",
					Map.of("files_scanned", files.size()));
		}
		return result;
	}

	/** One classified invisible character with its codepoint column in the line. */
	private static final class InvisibleChar {

		final int col;

		final int codePoint;

		final String category;

		final String name;

		InvisibleChar(int col, int codePoint, String category, String name) {
			this.col = col;
			this.codePoint = codePoint;
			this.category = category;
			this.name = name;
		}

	}

	private List<Finding> scanFile(Path filePath, String relativePath) {
		if (isBinary(filePath)) {
			return List.of();
		}
		String content;
		try {
			// errors="replace" semantics: String(byte[], UTF_8) substitutes malformed
			// input.
			content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			return List.of();
		}

		List<Finding> findings = new ArrayList<>();
		String[] lines = content.split("\n", -1);

		for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
			String line = lines[lineIdx];
			List<InvisibleChar> lineChars = new ArrayList<>();

			int col = 0;
			int i = 0;
			while (i < line.length()) {
				int cp = line.codePointAt(i);
				String[] match = classifyChar(cp);
				if (match != null) {
					lineChars.add(new InvisibleChar(col, cp, match[0], match[1]));
				}
				i += Character.charCount(cp);
				col++;
			}

			if (lineChars.isEmpty()) {
				continue;
			}
			for (List<InvisibleChar> group : groupConsecutive(lineChars)) {
				Finding finding = createFinding(group, lineIdx + 1, line, relativePath);
				if (finding != null) {
					findings.add(finding);
				}
			}
		}
		return findings;
	}

	private String[] classifyChar(int codePoint) {
		String[] known = charLookup.get(codePoint);
		if (known != null) {
			return known;
		}
		if (tagRange[0] <= codePoint && codePoint <= tagRange[1]) {
			return new String[] { "unicode_tags", "UNICODE TAG" };
		}
		if (vsBasicRange[0] <= codePoint && codePoint <= vsBasicRange[1]) {
			int vsNum = codePoint - vsBasicRange[0] + 1;
			return new String[] { "variation_selectors", "VARIATION SELECTOR-" + vsNum };
		}
		if (vsSupplementRange[0] <= codePoint && codePoint <= vsSupplementRange[1]) {
			int vsNum = codePoint - vsSupplementRange[0] + 17;
			return new String[] { "variation_selectors", "VARIATION SELECTOR-" + vsNum };
		}
		return null;
	}

	/** Group consecutive invisible characters by codepoint column position. */
	private static List<List<InvisibleChar>> groupConsecutive(List<InvisibleChar> chars) {
		if (chars.isEmpty()) {
			return List.of();
		}
		List<InvisibleChar> sorted = new ArrayList<>(chars);
		sorted.sort((a, b) -> Integer.compare(a.col, b.col));
		List<List<InvisibleChar>> groups = new ArrayList<>();
		List<InvisibleChar> current = new ArrayList<>();
		current.add(sorted.get(0));
		for (int i = 1; i < sorted.size(); i++) {
			if (sorted.get(i).col == current.get(current.size() - 1).col + 1) {
				current.add(sorted.get(i));
			}
			else {
				groups.add(current);
				current = new ArrayList<>();
				current.add(sorted.get(i));
			}
		}
		groups.add(current);
		return groups;
	}

	private Finding createFinding(List<InvisibleChar> group, int lineNum, String line, String relativePath) {
		int runLength = group.size();
		Set<String> categoriesInGroup = new LinkedHashSet<>();
		for (InvisibleChar c : group) {
			categoriesInGroup.add(c.category);
		}
		String primaryCategory = primaryCategory(categoriesInGroup);

		// BOM exception: U+FEFF at very start of file
		if (runLength == 1 && group.get(0).codePoint == 0xFEFF && lineNum == 1 && group.get(0).col == 0) {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("unicode_category", "zero_width");
			metadata.put("char_count", 1);
			return new Finding("UNICODE", Severity.INFO, "bom_marker", "UTF-8 BOM marker at file start (benign)",
					relativePath, lineNum, null, "BOM at file start is harmless. Remove if not needed.", metadata);
		}

		// Unicode Tags that decode to ASCII -> CRITICAL
		if (categoriesInGroup.contains("unicode_tags")) {
			List<InvisibleChar> tagChars = new ArrayList<>();
			for (InvisibleChar c : group) {
				if (c.category.equals("unicode_tags")) {
					tagChars.add(c);
				}
			}
			String decoded = decodeTagPayload(tagChars);
			if (decoded != null) {
				Map<String, Object> metadata = new LinkedHashMap<>();
				metadata.put("decoded_payload", decoded);
				metadata.put("char_count", tagChars.size());
				metadata.put("consecutive_run", runLength);
				metadata.put("unicode_category", "unicode_tags");
				return new Finding("UNICODE", Severity.CRITICAL, "ascii_smuggling_payload",
						"Hidden ASCII text decoded from Unicode Tags: '" + decoded + "'", relativePath, lineNum,
						safeLineContent(line), "Remove Unicode Tag characters (U+E0020-U+E007E). "
								+ "These encode hidden ASCII text invisible to reviewers.",
						metadata);
			}
		}

		return classifyByRunAndCategory(group, runLength, primaryCategory, categoriesInGroup, lineNum, line,
				relativePath);
	}

	private Finding classifyByRunAndCategory(List<InvisibleChar> group, int runLength, String primaryCategory,
			Set<String> categoriesInGroup, int lineNum, String line, String relativePath) {
		int critThreshold = threshold("consecutive_run_critical", 40);
		int highThreshold = threshold("consecutive_run_high", 10);
		int perLineMedium = threshold("per_line_medium", 3);

		if (runLength >= critThreshold) {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("char_count", runLength);
			metadata.put("consecutive_run", runLength);
			metadata.put("unicode_category", primaryCategory);
			return new Finding("UNICODE", Severity.CRITICAL, "long_invisible_run",
					"Very long consecutive invisible run (" + runLength + " chars): " + summarizeCharNames(group),
					relativePath, lineNum, safeLineContent(line),
					"Long consecutive invisible character runs strongly indicate data hiding. Remove them.", metadata);
		}

		// BiDi overrides -> HIGH
		if (categoriesInGroup.contains("bidi_marks")) {
			List<String> bidiNames = new ArrayList<>();
			int bidiCount = 0;
			for (InvisibleChar c : group) {
				if (c.category.equals("bidi_marks")) {
					bidiCount++;
					if (bidiNames.size() < 3) {
						bidiNames.add(c.name);
					}
				}
			}
			String charNames = String.join(", ", bidiNames);
			if (bidiCount > 3) {
				charNames += " (+" + (bidiCount - 3) + " more)";
			}
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("char_count", bidiCount);
			metadata.put("consecutive_run", runLength);
			metadata.put("unicode_category", "bidi_marks");
			return new Finding("UNICODE", Severity.HIGH, "bidi_override",
					"BiDi override characters detected (trojan source risk, CVE-2021-42574): " + charNames,
					relativePath, lineNum, safeLineContent(line),
					"Remove directional override characters that can disguise code execution flow.", metadata);
		}

		if (runLength >= highThreshold) {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("char_count", runLength);
			metadata.put("consecutive_run", runLength);
			metadata.put("unicode_category", primaryCategory);
			return new Finding("UNICODE", Severity.HIGH, "consecutive_invisible_run",
					"Long consecutive invisible run (" + runLength + " chars): " + summarizeCharNames(group),
					relativePath, lineNum, safeLineContent(line),
					"Consecutive invisible character runs suggest intentional encoding. Investigate and remove.",
					metadata);
		}

		// MEDIUM: zero-width chars 3+ per line, deprecated controls, invisible operators
		if (runLength >= perLineMedium
				&& (primaryCategory.equals("zero_width") || primaryCategory.equals("deprecated_format_controls")
						|| primaryCategory.equals("invisible_operators"))) {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("char_count", runLength);
			metadata.put("consecutive_run", runLength);
			metadata.put("unicode_category", primaryCategory);
			return new Finding("UNICODE", Severity.MEDIUM, "suspicious_invisible_chars",
					"Multiple invisible characters (" + runLength + "): " + summarizeCharNames(group), relativePath,
					lineNum, safeLineContent(line),
					"Multiple invisible characters in close proximity may indicate steganography.", metadata);
		}

		// Deprecated format controls and invisible operators are always at least MEDIUM
		if (primaryCategory.equals("deprecated_format_controls") || primaryCategory.equals("invisible_operators")) {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("char_count", runLength);
			metadata.put("unicode_category", primaryCategory);
			return new Finding("UNICODE", Severity.MEDIUM, "deprecated_format_control",
					"Deprecated/invisible format characters: " + summarizeCharNames(group), relativePath, lineNum,
					safeLineContent(line),
					"Deprecated format controls have no legitimate use in modern content. Remove them.", metadata);
		}

		// LOW: isolated zero-width, confusable spaces, variation selectors
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("char_count", runLength);
		metadata.put("unicode_category", primaryCategory);
		return new Finding("UNICODE", Severity.LOW, "isolated_invisible_char",
				"Isolated invisible character(s) (" + runLength + "): " + summarizeCharNames(group), relativePath,
				lineNum, safeLineContent(line), "Likely a copy-paste artifact. Remove if not intentional.", metadata);
	}

	private int threshold(String key, int defaultValue) {
		Object value = thresholds.get(key);
		return value instanceof Number n ? n.intValue() : defaultValue;
	}

	/** Decode Unicode Tag characters to their ASCII equivalents; null when empty. */
	private String decodeTagPayload(List<InvisibleChar> tagChars) {
		if (tagChars.isEmpty()) {
			return null;
		}
		StringBuilder decoded = new StringBuilder();
		int asciiStart = tagAsciiRange[0];
		int asciiEnd = tagAsciiRange[1];
		for (InvisibleChar tc : tagChars) {
			int code = tc.codePoint;
			if (code >= asciiStart && code <= asciiEnd) {
				decoded.appendCodePoint(code - 0xE0000);
			}
			else if (code == 0xE0001) {
				// TAG_START marker, skip
			}
			else if (code == 0xE007F) {
				// TAG_END marker, skip
			}
		}
		String payload = decoded.toString().strip();
		return payload.isEmpty() ? null : payload;
	}

	/** Determine the primary (highest-risk) category from a set. */
	private static String primaryCategory(Set<String> categories) {
		List<String> priority = List.of("unicode_tags", "bidi_marks", "deprecated_format_controls",
				"invisible_operators", "zero_width", "confusable_spaces", "variation_selectors");
		for (String cat : priority) {
			if (categories.contains(cat)) {
				return cat;
			}
		}
		return categories.isEmpty() ? "unknown" : categories.iterator().next();
	}

	/** Compact summary of character names in a group (first 4, then "(+N more)"). */
	private static String summarizeCharNames(List<InvisibleChar> group) {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < Math.min(4, group.size()); i++) {
			names.add(group.get(i).name);
		}
		String summary = String.join(", ", names);
		if (group.size() > 4) {
			summary += " (+" + (group.size() - 4) + " more)";
		}
		return summary;
	}

	/** Truncate line content for display, replacing non-printable characters with ␣. */
	private static String safeLineContent(String line) {
		StringBuilder visible = new StringBuilder();
		int i = 0;
		while (i < line.length()) {
			int cp = line.codePointAt(i);
			visible.append(isPrintable(cp) || cp == ' ' || cp == '\t' ? new String(Character.toChars(cp)) : "\u2423");
			i += Character.charCount(cp);
		}
		String stripped = visible.toString().strip();
		// Python len() counts codepoints; truncate on the codepoint scale.
		if (stripped.codePointCount(0, stripped.length()) > 120) {
			return stripped.substring(0, stripped.offsetByCodePoints(0, 117)) + "...";
		}
		return stripped;
	}

	/**
	 * Python {@code str.isprintable()}: false for Cc, Cf, Cs, Co, Cn, Zl, Zp, Zs (except
	 * ASCII space).
	 */
	private static boolean isPrintable(int cp) {
		if (cp == ' ') {
			return true;
		}
		int type = Character.getType(cp);
		return type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE
				&& type != Character.PRIVATE_USE && type != Character.UNASSIGNED && type != Character.LINE_SEPARATOR
				&& type != Character.PARAGRAPH_SEPARATOR && type != Character.SPACE_SEPARATOR;
	}

	/** MIME-type and null-byte binary detection, port of Python {@code _is_binary}. */
	private static boolean isBinary(Path filePath) {
		String fileName = filePath.getFileName().toString();
		String mimeType = guessMimeType(fileName);
		if (mimeType != null && !mimeType.startsWith("text/") && !TEXTUAL_MIME_TYPES.contains(mimeType)
				&& !mimeType.endsWith("+json") && !mimeType.endsWith("+xml")) {
			return true;
		}
		try (InputStream in = Files.newInputStream(filePath)) {
			byte[] chunk = in.readNBytes(Constants.BINARY_CHECK_CHUNK_SIZE);
			for (byte b : chunk) {
				if (b == 0) {
					return true;
				}
			}
		}
		catch (IOException e) {
			return true;
		}
		return false;
	}

	/**
	 * Mirrors Python {@code mimetypes.guess_type} on the filename extension
	 * (case-insensitive).
	 */
	private static String guessMimeType(String fileName) {
		int dot = fileName.lastIndexOf('.');
		if (dot <= 0 || dot == fileName.length() - 1) {
			return null;
		}
		return MIME_BY_EXTENSION.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
	}

	/**
	 * Loads the YAML pattern config from the classpath with snakeyaml SafeConstructor.
	 */
	private static final class UnicodeSmuggleConfig {

		private static final String RESOURCE_PATH = "org/iromu/embabel/smith/skillevaluator/config/unicode_smuggle_patterns.yaml";

		private UnicodeSmuggleConfig() {
		}

		@SuppressWarnings("unchecked")
		static Map<String, Object> load() {
			ClassLoader loader = UnicodeSmuggleValidator.class.getClassLoader();
			InputStream in = loader != null ? loader.getResourceAsStream(RESOURCE_PATH) : null;
			if (in == null) {
				in = ClassLoader.getSystemResourceAsStream(RESOURCE_PATH);
			}
			if (in == null) {
				throw new IllegalStateException("Missing classpath resource " + RESOURCE_PATH);
			}
			try (InputStream stream = in) {
				Object data = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
				if (!(data instanceof Map)) {
					throw new IllegalStateException("unicode_smuggle_patterns.yaml must contain a mapping");
				}
				return (Map<String, Object>) data;
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

	}

}
