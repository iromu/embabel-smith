/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;
import org.iromu.embabel.smith.skillevaluator.util.Frontmatter;
import org.iromu.embabel.smith.skillevaluator.util.ScanPaths;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * License compliance validation for Skills (and the rule/workflow frontmatter the Python
 * validator also reads). Port of {@code validators/license.py}.
 *
 * <p>
 * Multi-tier detection:
 * <ol>
 * <li>frontmatter {@code license} field of {@code SKILL.md}/{@code skill.md}/
 * {@code workflow-rules.mdc}/any {@code *.mdc};</li>
 * <li>LICENSE/COPYING-style files matched against the configured content patterns;</li>
 * <li>{@code SPDX-License-Identifier} headers in the first
 * {@link Constants#LICENSE_HEADER_SCAN_LINES} lines of scannable source files.</li>
 * </ol>
 *
 * <p>
 * Allowlist, blocklist, exception list, detection patterns and proprietary indicators
 * come from {@code org/skillevaluator/config/license_config.yaml} on the classpath.
 * License ids are compared through {@link #normalizeLicenseId}; SPDX expressions
 * ({@code AND}/{@code OR}/ {@code WITH} and parentheses) are parsed by
 * {@link #parseLicenseExpression} exactly as the Python grammar does, so a blocked id
 * fails even when the value does not parse.
 */
public class LicenseValidator extends ValidatorBase {

	/** Python {@code _SPDX_EXPRESSION_TOKEN}. */
	private static final Pattern SPDX_EXPRESSION_TOKEN = Pattern.compile("[()]|[^\\s()]+");

	/** Python {@code _SPDX_OPERATORS}. */
	private static final Set<String> SPDX_OPERATORS = Set.of("AND", "OR", "WITH");

	/** Python {@code _SPDX_VERSION_VARIANT_SUFFIX}. */
	private static final Pattern SPDX_VERSION_VARIANT_SUFFIX = Pattern.compile("-(?:only|or-later)$");

	/** Python {@code _EXPRESSION_OPERAND_STATES}. */
	private static final Set<String> EXPRESSION_OPERAND_STATES = Set.of("start", "(", "AND", "OR");

	/** Python {@code _EXPRESSION_COMPLETE_STATES}. */
	private static final Set<String> EXPRESSION_COMPLETE_STATES = Set.of("license", "exception", ")");

	/** Python {@code _LICENSE_SUFFIX_PATTERN}. */
	private static final Pattern LICENSE_SUFFIX_PATTERN = Pattern.compile("(-license|-licence)$");

	/**
	 * Python {@code _THE_PREFIX_PATTERN} ({@code ^(the-)?}) anchored at the string start.
	 */
	private static final String THE_PREFIX = "the-";

	/**
	 * Python {@code _SPDX_PATTERN}: {@code SPDX_LICENSE_PATTERN} compiled with
	 * IGNORECASE.
	 */
	private static final Pattern SPDX_HEADER_PATTERN = Pattern.compile("SPDX-License-Identifier:\\s*([^\n]+)",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	/** Python {@code _FILE_REFERENCE_KEYWORDS}. */
	private static final Set<String> FILE_REFERENCE_KEYWORDS = Set.of("see ", "refer to ", "license.txt", "license.md",
			"copying");

	/** Python {@code _LICENSE_DECLARATION_STEMS}. */
	private static final Set<String> LICENSE_DECLARATION_STEMS = Set.of("license", "licence", "copying");

	/** Python {@code _TERMINAL_LICENSE_STATUSES}. */
	private static final Set<String> TERMINAL_LICENSE_STATUSES = Set.of("conflict", "unrecognized");

	private final boolean strictMode;

	private Map<String, Object> config;

	private Set<String> normalizedAllowlist;

	private Set<String> normalizedBlocklist;

	private Set<String> normalizedExceptionAllowlist;

	/**
	 * @param strictMode when true, unknown licenses and a missing license fail instead of
	 * warn
	 */
	public LicenseValidator(boolean strictMode) {
		this.strictMode = strictMode;
	}

	public LicenseValidator() {
		this(false);
	}

	public boolean isStrictMode() {
		return strictMode;
	}

	@Override
	public String getName() {
		return "License Compliance";
	}

	@Override
	public String getDescription() {
		return "Validate license compliance for Skills, Rules, and Workflows";
	}

	@Override
	public ValidationResult validate(Path assetPath) {
		if (Files.isDirectory(assetPath) && !isAssetDirectory(assetPath)) {
			return validateFolderOrSkill(assetPath, this::validateSingleAsset, "Checking license compliance for");
		}
		return validateSingleAsset(assetPath);
	}

	// ------------------------------------------------------------------
	// Configuration
	// ------------------------------------------------------------------

	/** Lazy config load, mirroring the Python {@code config} cached property. */
	public Map<String, Object> getConfig() {
		if (config == null) {
			config = LicenseConfig.load();
		}
		return config;
	}

	private Set<String> normalizedAllowlist() {
		if (normalizedAllowlist == null) {
			normalizedAllowlist = normalizeAll(getStringList("allowed_licenses"));
		}
		return normalizedAllowlist;
	}

	private Set<String> normalizedBlocklist() {
		if (normalizedBlocklist == null) {
			normalizedBlocklist = normalizeAll(getStringList("blocked_licenses"));
		}
		return normalizedBlocklist;
	}

	private Set<String> normalizedExceptionAllowlist() {
		if (normalizedExceptionAllowlist == null) {
			normalizedExceptionAllowlist = normalizeAll(getStringList("allowed_license_exceptions"));
		}
		return normalizedExceptionAllowlist;
	}

	private Set<String> normalizeAll(List<String> ids) {
		Set<String> out = new LinkedHashSet<>();
		for (String id : ids) {
			out.add(normalizeLicenseId(id));
		}
		return out;
	}

	private List<String> getStringList(String key) {
		Object value = getConfig().get(key);
		List<String> out = new ArrayList<>();
		if (value instanceof List<?> list) {
			for (Object item : list) {
				if (item != null) {
					out.add(String.valueOf(item));
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------
	// Single-asset validation
	// ------------------------------------------------------------------

	/**
	 * Python {@code _is_asset_directory}: a skill, workflow-rules, or any {@code *.mdc}
	 * file.
	 */
	private boolean isAssetDirectory(Path path) {
		if (findSkillManifest(path) != null) {
			return true;
		}
		if (Files.exists(path.resolve("workflow-rules.mdc"))) {
			return true;
		}
		try (Stream<Path> children = Files.list(path)) {
			return children.anyMatch(child -> child.getFileName().toString().endsWith(".mdc"));
		}
		catch (IOException e) {
			return false;
		}
	}

	private ValidationResult validateSingleAsset(Path assetPath) {
		ValidationResult result = new ValidationResult();
		result.setValidatorName(getName());
		result.setValidatorDescription(getDescription());

		LicenseDetection detection = detectLicense(assetPath, result);

		if (TERMINAL_LICENSE_STATUSES.contains(licenseStatus(result))) {
			return result;
		}
		if (detection == null) {
			handleNoLicense(result);
		}
		else {
			validateLicense(detection, result);
		}

		if (result.isPassed()) {
			String licenseStatus = stringOrFallback(result.getMetadata().get("license_status"), "not_detected");
			result.addSuccess("license_compliance",
					"License compliance scan completed (" + licenseStatus.replace('_', ' ') + ")",
					Map.of("license_status", licenseStatus));
		}
		return result;
	}

	/** Python {@code _handle_no_license}. */
	private void handleNoLicense(ValidationResult result) {
		String msg = "No license information found";
		if (strictMode) {
			result.addError(msg + " - cannot verify compliance");
		}
		else {
			result.addWarning(msg + " - manual review required. Add a LICENSE file or 'license' field in frontmatter.");
		}
	}

	/** Python {@code _detect_license}: the three-tier detection ladder. */
	private LicenseDetection detectLicense(Path assetPath, ValidationResult result) {
		LicenseFileScan scan = collectLicenseFiles(assetPath);

		LicenseDetection frontmatter = checkFrontmatter(assetPath);
		if (frontmatter != null) {
			result.addMessage("Tier 1: Found license declaration in " + frontmatter.source);
			if (isTruthy(frontmatter.licenseId) && isFileReference(frontmatter.licenseId)) {
				result.addMessage("  License field references file: '" + frontmatter.licenseId + "'");
				LicenseDetection chosen = chooseFromLicenseFiles(scan, result);
				if (chosen != null || TERMINAL_LICENSE_STATUSES.contains(licenseStatus(result))) {
					return chosen;
				}
			}
			else {
				return resolveFrontmatterAndFiles(frontmatter, scan, result);
			}
		}

		LicenseDetection chosen = chooseFromLicenseFiles(scan, result);
		if (TERMINAL_LICENSE_STATUSES.contains(licenseStatus(result))) {
			return chosen;
		}
		if (chosen != null) {
			result.addMessage("Tier 2: Detected " + chosen.licenseId + " from " + chosen.filePath);
			return chosen;
		}

		LicenseDetection header = scanSourceHeaders(assetPath);
		if (header != null) {
			result.addMessage("Tier 3: Found SPDX header '" + header.licenseId + "' in " + header.filePath);
			return header;
		}

		result.addMessage("No license detected in any tier");
		return null;
	}

	/** Python {@code _check_frontmatter}: Tier 1. */
	private LicenseDetection checkFrontmatter(Path assetPath) {
		List<Path> manifests = new ArrayList<>();
		manifests.add(assetPath.resolve("SKILL.md"));
		manifests.add(assetPath.resolve("skill.md"));
		manifests.add(assetPath.resolve("workflow-rules.mdc"));
		manifests.addAll(listMdcFiles(assetPath));

		for (Path manifest : manifests) {
			if (!Files.exists(manifest)) {
				continue;
			}
			try {
				Frontmatter.FrontmatterOutcome outcome = Frontmatter.parse(manifest);
				Map<String, Object> yamlData = outcome.parsed() == null ? null : outcome.parsed().yamlData();
				if (yamlData != null && yamlData.containsKey("license")) {
					return new LicenseDetection(pythonStr(yamlData.get("license")).strip(),
							"frontmatter (" + manifest.getFileName() + ")", "high", manifest.getFileName().toString(),
							null);
				}
			}
			catch (RuntimeException e) {
				// Python logs the parse failure at debug level and keeps looking.
			}
		}
		return null;
	}

	/**
	 * Python {@code asset_path.glob("*.mdc")} — non-recursive, case-sensitive on Linux.
	 */
	private List<Path> listMdcFiles(Path assetPath) {
		try (Stream<Path> children = Files.list(assetPath)) {
			return children.filter(child -> child.getFileName().toString().endsWith(".mdc")).toList();
		}
		catch (IOException e) {
			return List.of();
		}
	}

	/** Python {@code _is_file_reference}. */
	static boolean isFileReference(String licenseValue) {
		String lower = licenseValue.toLowerCase(Locale.ROOT);
		for (String ref : FILE_REFERENCE_KEYWORDS) {
			if (lower.contains(ref)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Tier 2: license files
	// ------------------------------------------------------------------

	/** Python {@code _collect_license_files}. */
	private LicenseFileScan collectLicenseFiles(Path assetPath) {
		List<LicenseDetection> detections = new ArrayList<>();
		List<String> unrecognized = new ArrayList<>();

		for (String licenseName : Constants.LICENSE_FILE_NAMES) {
			if (!isLicenseDeclarationFilename(licenseName)) {
				continue;
			}
			Path licensePath = assetPath.resolve(licenseName);
			if (!Files.isRegularFile(licensePath)) {
				continue;
			}
			String content = readTextIgnoringErrors(licensePath);
			if (content == null) {
				unrecognized.add(licenseName);
				continue;
			}
			List<ContentMatch> matches = identifyAllLicensesFromContent(content);
			if (!matches.isEmpty()) {
				for (ContentMatch match : matches) {
					detections.add(new LicenseDetection(match.licenseId(), "license_file", match.confidence(),
							licenseName, null));
				}
				continue;
			}
			String indicator = findProprietaryIndicator(content);
			if (indicator != null) {
				detections.add(new LicenseDetection("Proprietary", "license_file", "high", licenseName,
						"Found proprietary indicator: '" + indicator + "'"));
				continue;
			}
			unrecognized.add(licenseName);
		}
		return new LicenseFileScan(detections, unrecognized);
	}

	/**
	 * Python {@code _is_license_declaration_filename}: LICENSE/COPYING names, not NOTICE.
	 */
	private static boolean isLicenseDeclarationFilename(String name) {
		return LICENSE_DECLARATION_STEMS.contains(stem(name).toLowerCase(Locale.ROOT));
	}

	/** Python {@code Path(name).stem}: the basename without its last suffix. */
	private static String stem(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	/**
	 * Python {@code read_text(encoding="utf-8", errors="ignore")}; null when unreadable.
	 */
	private static String readTextIgnoringErrors(Path path) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.IGNORE)
				.onUnmappableCharacter(CodingErrorAction.IGNORE);
			return decoder.decode(ByteBuffer.wrap(bytes)).toString();
		}
		catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * Python {@code _identify_all_licenses_from_content}: every configured pattern that
	 * matches.
	 */
	private List<ContentMatch> identifyAllLicensesFromContent(String content) {
		String contentUpper = content.toUpperCase(Locale.ROOT);
		List<ContentMatch> matches = new ArrayList<>();
		Map<String, Object> patterns = asMap(getConfig().get("license_patterns"));
		for (Map.Entry<String, Object> entry : patterns.entrySet()) {
			Map<String, Object> patternConfig = asMap(entry.getValue());
			List<String> required = asStringList(patternConfig.get("required"));
			List<String> exclude = asStringList(patternConfig.get("exclude"));
			if (!allPatternsMatch(required, content, contentUpper)) {
				continue;
			}
			if (anyPatternMatches(exclude, contentUpper)) {
				continue;
			}
			matches.add(new ContentMatch(entry.getKey(),
					String.valueOf(patternConfig.getOrDefault("confidence", "medium"))));
		}
		return matches;
	}

	/**
	 * Python {@code _all_patterns_match}: {@code |}-bearing patterns are regexes, the
	 * rest substrings.
	 */
	private static boolean allPatternsMatch(List<String> patterns, String content, String contentUpper) {
		for (String pattern : patterns) {
			if (pattern.contains("|")) {
				Pattern compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
				if (!compiled.matcher(content).find()) {
					return false;
				}
			}
			else if (!contentUpper.contains(pattern.toUpperCase(Locale.ROOT))) {
				return false;
			}
		}
		return true;
	}

	/** Python {@code _any_pattern_matches}. */
	private static boolean anyPatternMatches(List<String> patterns, String contentUpper) {
		for (String pattern : patterns) {
			if (contentUpper.contains(pattern.toUpperCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Python {@code _find_proprietary_indicator}: plain case-insensitive substring
	 * search.
	 */
	private String findProprietaryIndicator(String content) {
		String contentLower = content.toLowerCase(Locale.ROOT);
		for (String indicator : getStringList("proprietary_indicators")) {
			if (contentLower.contains(indicator.toLowerCase(Locale.ROOT))) {
				return indicator;
			}
		}
		return null;
	}

	/** Python {@code _choose_from_license_files}. */
	private LicenseDetection chooseFromLicenseFiles(LicenseFileScan scan, ValidationResult result) {
		if (!scan.unrecognizedDeclarationFiles.isEmpty()) {
			recordUnrecognizedLicenseFile(result, scan.unrecognizedDeclarationFiles, null);
			return null;
		}
		if (scan.detections.isEmpty()) {
			return null;
		}
		for (LicenseDetection item : scan.detections) {
			if (licenseBucket(item.licenseId).equals("blocked")) {
				return item;
			}
		}
		Map<String, LicenseDetection> unique = uniqueById(scan.detections);
		if (unique.size() == 1) {
			return unique.values().iterator().next();
		}
		List<LicenseDetection> values = new ArrayList<>(unique.values());
		addLicenseMismatchFinding(result, values.get(0), values.get(1), "license_file_mismatch");
		return null;
	}

	/** Python {@code _resolve_frontmatter_and_files}. */
	private LicenseDetection resolveFrontmatterAndFiles(LicenseDetection frontmatter, LicenseFileScan scan,
			ValidationResult result) {
		if (!scan.unrecognizedDeclarationFiles.isEmpty()) {
			recordUnrecognizedLicenseFile(result, scan.unrecognizedDeclarationFiles, frontmatter);
			return null;
		}
		if (scan.detections.isEmpty()) {
			return frontmatter;
		}

		LicenseDetection blockedFile = null;
		for (LicenseDetection item : scan.detections) {
			if (licenseBucket(item.licenseId).equals("blocked")) {
				blockedFile = item;
				break;
			}
		}
		if (blockedFile != null) {
			result.addMessage("Frontmatter license '" + frontmatter.licenseId + "' differs from " + blockedFile.filePath
					+ " license '" + blockedFile.licenseId + "'");
			result.addMessage("Tier 2: Detected " + blockedFile.licenseId + " from " + blockedFile.filePath);
			return blockedFile;
		}

		String frontmatterBucket = licenseBucket(frontmatter.licenseId);
		if (frontmatterBucket.equals("blocked") || frontmatterBucket.equals("unsupported")) {
			return frontmatter;
		}

		Map<String, LicenseDetection> fileById = uniqueById(scan.detections);
		Set<String> declared = new LinkedHashSet<>();
		for (String name : parseLicenseExpression(orEmpty(frontmatter.licenseId)).licenses) {
			declared.add(normalizeLicenseId(name));
		}
		if (declared.containsAll(fileById.keySet())) {
			return frontmatter;
		}

		LicenseDetection other = null;
		for (Map.Entry<String, LicenseDetection> entry : fileById.entrySet()) {
			if (!declared.contains(entry.getKey())) {
				other = entry.getValue();
				break;
			}
		}
		if (other != null && frontmatterBucket.equals("allowed") && licenseBucket(other.licenseId).equals("allowed")) {
			result.addMessage("Frontmatter license '" + frontmatter.licenseId + "' differs from " + other.filePath
					+ " license '" + other.licenseId + "'");
			addLicenseMismatchFinding(result, frontmatter, other, "frontmatter_license_mismatch");
			return null;
		}
		if (other != null && licenseBucket(other.licenseId).equals("unknown")) {
			return other;
		}
		return frontmatter;
	}

	/**
	 * Collapse detections by normalized id, first occurrence winning, skipping empty ids.
	 */
	private Map<String, LicenseDetection> uniqueById(List<LicenseDetection> detections) {
		Map<String, LicenseDetection> unique = new LinkedHashMap<>();
		for (LicenseDetection item : detections) {
			if (!isTruthy(item.licenseId)) {
				continue;
			}
			unique.putIfAbsent(normalizeLicenseId(item.licenseId), item);
		}
		return unique;
	}

	// ------------------------------------------------------------------
	// Tier 3: SPDX headers
	// ------------------------------------------------------------------

	/**
	 * Python {@code _scan_source_headers}: the most common header id wins, first seen on
	 * a tie.
	 */
	private LicenseDetection scanSourceHeaders(Path assetPath) {
		Map<String, List<String>> foundLicenses = new LinkedHashMap<>();

		for (Path file : ScanPaths.iterScannableFiles(assetPath, Constants.LICENSE_HEADER_EXTENSIONS)) {
			String licenseId = extractSpdxFromFile(file);
			if (licenseId != null) {
				foundLicenses.computeIfAbsent(licenseId, key -> new ArrayList<>())
					.add(assetPath.relativize(file).toString());
			}
		}

		if (foundLicenses.isEmpty()) {
			return null;
		}

		String mostCommon = null;
		int best = -1;
		for (Map.Entry<String, List<String>> entry : foundLicenses.entrySet()) {
			if (entry.getValue().size() > best) {
				best = entry.getValue().size();
				mostCommon = entry.getKey();
			}
		}
		List<String> files = foundLicenses.get(mostCommon);
		return new LicenseDetection(mostCommon, "spdx_header", files.size() > 1 ? "high" : "medium", files.get(0),
				"Found in " + files.size() + " file(s)");
	}

	/** Python {@code _extract_spdx_from_file}. */
	static String extractSpdxFromFile(Path file) {
		String header = firstLines(file, Constants.LICENSE_HEADER_SCAN_LINES);
		if (header == null) {
			return null;
		}
		Matcher matcher = SPDX_HEADER_PATTERN.matcher(header);
		if (!matcher.find()) {
			return null;
		}
		int lineStart = header.lastIndexOf('\n', Math.max(matcher.start() - 1, 0)) + 1;
		String raw = stripSpdxCapture(matcher.group(1), header.substring(lineStart, matcher.start()));
		return raw.isEmpty() ? null : raw;
	}

	/**
	 * Read the first {@code lineLimit} lines the way Python's text-mode read with
	 * errors="ignore" does.
	 */
	private static String firstLines(Path file, int lineLimit) {
		String text = readTextIgnoringErrors(file);
		if (text == null) {
			return null;
		}
		int index = 0;
		for (int seen = 1; seen < lineLimit; seen++) {
			int next = text.indexOf('\n', index);
			if (next < 0) {
				return text;
			}
			index = next + 1;
		}
		int end = text.indexOf('\n', index);
		return end < 0 ? text : text.substring(0, end + 1);
	}

	/**
	 * Python {@code _strip_spdx_capture}: drop inline comment terminators and trailing
	 * source after the SPDX value. A backtick or {@code |} ends the value only when the
	 * same delimiter opens before the tag on that line, so {@code MIT | GPL-3.0} is not
	 * shortened to {@code MIT}.
	 */
	private static String stripSpdxCapture(String raw, String linePrefix) {
		int hash = raw.indexOf('#');
		if (hash >= 0) {
			raw = raw.substring(0, hash);
		}
		List<String> markers = new ArrayList<>(List.of("*/", "-->"));
		if (linePrefix.contains("`")) {
			markers.add("`");
		}
		if (linePrefix.contains("|")) {
			markers.add("|");
		}
		for (String marker : markers) {
			int idx = raw.indexOf(marker);
			if (idx != -1) {
				raw = raw.substring(0, idx);
			}
		}
		return rstrip(rstripChar(raw.strip(), ';'));
	}

	/** Python {@code str.rstrip()} over the whole string. */
	private static String rstrip(String value) {
		int end = value.length();
		while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(0, end);
	}

	/** Python {@code str.rstrip(char)}. */
	private static String rstripChar(String value, char ch) {
		int end = value.length();
		while (end > 0 && value.charAt(end - 1) == ch) {
			end--;
		}
		return value.substring(0, end);
	}

	// ------------------------------------------------------------------
	// Policy
	// ------------------------------------------------------------------

	/** Python {@code _license_bucket}. */
	private String licenseBucket(String licenseId) {
		if (!isTruthy(licenseId)) {
			return "unknown";
		}
		return licensePolicy(parseLicenseExpression(licenseId))[0];
	}

	/**
	 * Python {@code _license_policy}: the first blocked name blocks the value, a syntax
	 * error makes it unsupported, and it is allowed only when every license and every
	 * {@code WITH} exception is listed.
	 * @return {@code {status, decidingName}} where the name may be null
	 */
	private String[] licensePolicy(LicenseExpression expression) {
		for (String name : expression.names) {
			if (isBlockedLicense(name)) {
				return new String[] { "blocked", name };
			}
		}
		if (expression.error != null) {
			return new String[] { "unsupported", null };
		}
		for (String name : expression.licenses) {
			if (!normalizedAllowlist().contains(normalizeLicenseId(name))) {
				return new String[] { "unknown", name };
			}
		}
		for (String[] pair : expression.exceptions) {
			if (!normalizedExceptionAllowlist().contains(normalizeLicenseId(pair[1]))) {
				return new String[] { "unknown", pair[0] + " WITH " + pair[1] };
			}
		}
		return new String[] { "allowed", null };
	}

	/**
	 * Python {@code _is_blocked_license}: true for a blocked id or one of its SPDX
	 * variants ({@code -only}/{@code -or-later}/{@code +}, and the deprecated
	 * {@code -with-*-exception} ids).
	 */
	private boolean isBlockedLicense(String licenseId) {
		String normalized = normalizeLicenseId(licenseId);
		String base = normalized;
		int withIndex = normalized.indexOf("-with-");
		if (withIndex >= 0) {
			String head = normalized.substring(0, withIndex);
			String tail = normalized.substring(withIndex + "-with-".length());
			if (tail.endsWith("-exception")) {
				base = head;
			}
		}
		Set<String> blocklist = normalizedBlocklist();
		return blocklist.contains(normalized) || blocklist.contains(base)
				|| blocklist.contains(SPDX_VERSION_VARIANT_SUFFIX.matcher(base).replaceAll(""));
	}

	/**
	 * Python {@code _normalize_license_id}: case-insensitive, separator-insensitive
	 * comparison key.
	 */
	static String normalizeLicenseId(String licenseId) {
		String normalized = licenseId.strip().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
		if (normalized.startsWith(THE_PREFIX)) {
			normalized = normalized.substring(THE_PREFIX.length());
		}
		normalized = LICENSE_SUFFIX_PATTERN.matcher(normalized).replaceAll("");
		if (normalized.endsWith("+")) {
			normalized = normalized.substring(0, normalized.length() - 1) + "-or-later";
		}
		return normalized;
	}

	// ------------------------------------------------------------------
	// SPDX expression grammar
	// ------------------------------------------------------------------

	/**
	 * Python {@code _parse_license_expression}: check SPDX expression syntax and collect
	 * the names the value references. Adjacent words without an operator form one name,
	 * so free-text values such as {@code MIT License} keep working; a value that does not
	 * parse but names no upper-case SPDX operator collapses to a single free-text name.
	 */
	static LicenseExpression parseLicenseExpression(String value) {
		List<String[]> items = new ArrayList<>();
		List<String> words = new ArrayList<>();
		Matcher tokens = SPDX_EXPRESSION_TOKEN.matcher(value);
		while (tokens.find()) {
			String token = tokens.group();
			String keyword = token.toUpperCase(Locale.ROOT);
			if (token.equals("(") || token.equals(")") || SPDX_OPERATORS.contains(keyword)) {
				if (!words.isEmpty()) {
					items.add(new String[] { "name", String.join(" ", words) });
					words = new ArrayList<>();
				}
				items.add(new String[] { keyword, token });
			}
			else {
				words.add(token);
			}
		}
		if (!words.isEmpty()) {
			items.add(new String[] { "name", String.join(" ", words) });
		}

		List<String> names = new ArrayList<>();
		for (String[] item : items) {
			if (item[0].equals("name")) {
				names.add(item[1]);
			}
		}

		LicenseExpression expression = parseExpressionItems(items);
		if (expression.error != null && !namesOfAnyOperator(items)) {
			String whole = value.strip();
			List<String> wholeNames = new ArrayList<>();
			wholeNames.add(whole);
			wholeNames.addAll(names);
			return new LicenseExpression(List.of(whole), List.of(), null, wholeNames);
		}
		return new LicenseExpression(expression.licenses, expression.exceptions, expression.error, names);
	}

	/** Python {@code any(text in _SPDX_OPERATORS ...)}, comparing the raw token. */
	private static boolean namesOfAnyOperator(List<String[]> items) {
		for (String[] item : items) {
			if (SPDX_OPERATORS.contains(item[1])) {
				return true;
			}
		}
		return false;
	}

	/** Python {@code _parse_expression_items}: the grammar walk over grouped tokens. */
	private static LicenseExpression parseExpressionItems(List<String[]> items) {
		List<String> licenses = new ArrayList<>();
		List<String[]> exceptions = new ArrayList<>();
		int depth = 0;
		String state = "start";
		for (String[] item : items) {
			String kind = item[0];
			String text = item[1];
			if (kind.equals("name") && state.equals("WITH")) {
				if (licenses.isEmpty()) {
					return LicenseExpression.error("WITH must follow a single license identifier");
				}
				exceptions.add(new String[] { licenses.get(licenses.size() - 1), text });
				state = "exception";
			}
			else if (kind.equals("name") && EXPRESSION_OPERAND_STATES.contains(state)) {
				licenses.add(text);
				state = "license";
			}
			else if (kind.equals("name") || (kind.equals("(") && !EXPRESSION_OPERAND_STATES.contains(state))) {
				return LicenseExpression.error("missing operator before '" + text + "'");
			}
			else if (kind.equals("(")) {
				depth += 1;
				state = "(";
			}
			else if (kind.equals("WITH") && !state.equals("license")) {
				return LicenseExpression.error("WITH must follow a single license identifier");
			}
			else if (kind.equals("WITH")) {
				state = "WITH";
			}
			else if (!EXPRESSION_COMPLETE_STATES.contains(state)) {
				return LicenseExpression.error("missing license identifier before '" + text + "'");
			}
			else if (kind.equals(")") && depth == 0) {
				return LicenseExpression.error("unbalanced parenthesis");
			}
			else if (kind.equals(")")) {
				depth -= 1;
				state = ")";
			}
			else {
				state = kind;
			}
		}
		if (depth != 0) {
			return LicenseExpression.error("unbalanced parenthesis");
		}
		if (!items.isEmpty() && !EXPRESSION_COMPLETE_STATES.contains(state)) {
			String missing = state.equals("WITH") ? "exception" : "license";
			return LicenseExpression
				.error("missing " + missing + " identifier after '" + items.get(items.size() - 1)[1] + "'");
		}
		return new LicenseExpression(licenses, exceptions, null, List.of());
	}

	// ------------------------------------------------------------------
	// Reporting
	// ------------------------------------------------------------------

	/** Python {@code _record_unrecognized_license_file}. */
	private void recordUnrecognizedLicenseFile(ValidationResult result, List<String> filenames,
			LicenseDetection frontmatter) {
		String joined = String.join(", ", filenames);
		String declared = frontmatter != null && isTruthy(frontmatter.licenseId)
				? " Frontmatter declares '" + frontmatter.licenseId + "'." : "";
		result
			.addStructuredFinding(
					new Finding("LICENSE", Severity.HIGH, "unrecognized_license_file",
							"License file '" + joined + "' is present but could not be identified." + declared
									+ " Manual review is required.",
							filenames.get(0), null, null,
							"Identify the license in the file, add a detection pattern, or remove the file.", null),
					true);
		Map<String, Object> metadata = result.getMetadata();
		metadata.put("license", frontmatter == null ? null : frontmatter.licenseId);
		metadata.put("license_status", "unrecognized");
		metadata.put("license_source", "license_file");
	}

	/** Python {@code _add_license_mismatch_finding}. */
	private void addLicenseMismatchFinding(ValidationResult result, LicenseDetection first, LicenseDetection second,
			String checkName) {
		String filePath = isTruthy(second.filePath) ? second.filePath
				: (isTruthy(first.filePath) ? first.filePath : "unknown");
		result.addStructuredFinding(new Finding("LICENSE", Severity.HIGH, checkName,
				"License '" + first.licenseId + "' conflicts with " + second.filePath + " license '" + second.licenseId
						+ "'",
				filePath, null, null, "Make every license declaration agree, or remove the conflicting file.", null),
				true);
		Map<String, Object> metadata = result.getMetadata();
		metadata.put("license", first.licenseId + " vs " + second.licenseId);
		metadata.put("license_status", "conflict");
		metadata.put("license_source",
				first.source.startsWith("frontmatter") ? "frontmatter+license_file" : "license_file");
	}

	/** Python {@code _validate_license}. */
	private void validateLicense(LicenseDetection detection, ValidationResult result) {
		String licenseId = detection.licenseId;
		if (!isTruthy(licenseId)) {
			result.addWarning("License detection returned empty identifier");
			return;
		}

		LicenseExpression expression = parseLicenseExpression(licenseId);
		String[] policy = licensePolicy(expression);
		String status = policy[0];
		String name = policy[1];
		setLicenseMetadata(result, detection, status);

		if (status.equals("allowed")) {
			result.addMessage("License: " + licenseId + " (ALLOWED - permissive)");
		}
		else if (status.equals("blocked")) {
			String subject = isTruthy(name) && expression.names.size() > 1 ? name : licenseId;
			if (!subject.equals(licenseId)) {
				result.addMessage("SPDX expression '" + licenseId + "' includes blocked license '" + subject + "'");
			}
			if (expression.error != null) {
				result
					.addMessage("License expression '" + licenseId + "' is not valid SPDX syntax: " + expression.error);
			}
			addBlockedLicenseFinding(result, subject, detection.filePath);
		}
		else if (expression.error != null) {
			addUnsupportedLicenseExpressionFinding(result, licenseId, detection.filePath, expression.error);
		}
		else {
			String subject = isTruthy(name) && (expression.licenses.size() > 1 || !expression.exceptions.isEmpty())
					? name : licenseId;
			handleUnknownLicense(result, subject, detection.filePath);
		}
	}

	/** Python {@code _set_license_metadata}. */
	private static void setLicenseMetadata(ValidationResult result, LicenseDetection detection, String status) {
		Map<String, Object> metadata = result.getMetadata();
		metadata.put("license", detection.licenseId);
		metadata.put("license_status", status);
		metadata.put("license_source", detection.source);
	}

	/** Python {@code _add_blocked_license_finding}. */
	private static void addBlockedLicenseFinding(ValidationResult result, String licenseId, String filePath) {
		result.addStructuredFinding(new Finding("LICENSE", Severity.HIGH, "blocked_license",
				"License '" + licenseId + "' is not allowed (restrictive/copyleft)",
				filePath == null ? "unknown" : filePath, null, null,
				"This asset uses a restrictive license that is not permitted. Contact the asset owner"
						+ " about re-licensing, or find an alternative.",
				null), true);
	}

	/** Python {@code _add_unsupported_license_expression_finding}. */
	private static void addUnsupportedLicenseExpressionFinding(ValidationResult result, String licenseId,
			String filePath, String reason) {
		result.addStructuredFinding(new Finding("LICENSE", Severity.HIGH, "unsupported_license_expression",
				"License expression '" + licenseId + "' is not valid SPDX syntax: " + reason,
				filePath == null ? "unknown" : filePath, null, null,
				"Use a valid SPDX license expression, such as 'MIT OR Apache-2.0'.", null), true);
	}

	/** Python {@code _handle_unknown_license}. */
	private void handleUnknownLicense(ValidationResult result, String licenseId, String filePath) {
		if (strictMode) {
			result
				.addStructuredFinding(
						new Finding("LICENSE", Severity.MEDIUM, "unknown_license",
								"License '" + licenseId + "' is not in the allowlist",
								filePath == null ? "unknown" : filePath, null, null,
								"Review license '" + licenseId + "' and add to allowlist if permissive, or blocklist"
										+ " if restrictive. See skillevaluator/config/license_config.yaml",
								null),
						true);
		}
		else {
			result.addWarning("License '" + licenseId + "' not in allowlist - manual review required");
		}
	}

	// ------------------------------------------------------------------
	// Small helpers
	// ------------------------------------------------------------------

	private String licenseStatus(ValidationResult result) {
		Object value = result.getMetadata().get("license_status");
		return value == null ? "" : String.valueOf(value);
	}

	/** Python truthiness for the strings this validator inspects. */
	private static boolean isTruthy(String value) {
		return value != null && !value.isEmpty();
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String stringOrFallback(Object value, String fallback) {
		String text = value == null ? "" : String.valueOf(value);
		return text.isEmpty() ? fallback : text;
	}

	/**
	 * Render a YAML scalar the way Python's {@code str()} would for the values a
	 * frontmatter can carry, so a YAML {@code true} reads as {@code True} rather than
	 * {@code true}.
	 */
	private static String pythonStr(Object value) {
		if (value == null) {
			return "None";
		}
		if (value instanceof Boolean bool) {
			return bool ? "True" : "False";
		}
		return String.valueOf(value);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
	}

	private static List<String> asStringList(Object value) {
		List<String> out = new ArrayList<>();
		if (value instanceof List<?> list) {
			for (Object item : list) {
				if (item != null) {
					out.add(String.valueOf(item));
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------
	// Value types
	// ------------------------------------------------------------------

	/** Python {@code LicenseDetection}: one license-detection attempt. */
	public static final class LicenseDetection {

		private final String licenseId;

		private final String source;

		private final String confidence;

		private final String filePath;

		private final String details;

		public LicenseDetection(String licenseId, String source, String confidence) {
			this(licenseId, source, confidence, null, null);
		}

		public LicenseDetection(String licenseId, String source, String confidence, String filePath, String details) {
			this.licenseId = licenseId;
			this.source = source;
			this.confidence = confidence;
			this.filePath = filePath;
			this.details = details;
		}

		public String getLicenseId() {
			return licenseId;
		}

		public String getSource() {
			return source;
		}

		public String getConfidence() {
			return confidence;
		}

		public String getFilePath() {
			return filePath;
		}

		public String getDetails() {
			return details;
		}

		@Override
		public String toString() {
			return "LicenseDetection(license_id=" + licenseId + ", source=" + source + ", confidence=" + confidence
					+ ", file_path=" + filePath + ", details=" + details + ")";
		}

	}

	/**
	 * Python {@code _LicenseExpression}: the license names and {@code WITH} exceptions of
	 * a value, or its syntax error. {@code names} holds every name written in the value,
	 * whatever its position and even when the value does not parse, so the blocked check
	 * stays fail-closed.
	 */
	static final class LicenseExpression {

		final List<String> licenses;

		final List<String[]> exceptions;

		final String error;

		final List<String> names;

		LicenseExpression(List<String> licenses, List<String[]> exceptions, String error, List<String> names) {
			this.licenses = licenses;
			this.exceptions = exceptions;
			this.error = error;
			this.names = names;
		}

		static LicenseExpression error(String message) {
			return new LicenseExpression(List.of(), List.of(), message, List.of());
		}

	}

	/**
	 * Python {@code _LicenseFileScan}: detections plus unidentified declaration files.
	 */
	private static final class LicenseFileScan {

		private final List<LicenseDetection> detections;

		private final List<String> unrecognizedDeclarationFiles;

		LicenseFileScan(List<LicenseDetection> detections, List<String> unrecognizedDeclarationFiles) {
			this.detections = detections;
			this.unrecognizedDeclarationFiles = unrecognizedDeclarationFiles;
		}

	}

	/** One configured {@code license_patterns} entry that matched a file's content. */
	private record ContentMatch(String licenseId, String confidence) {
	}

	/**
	 * Loads {@code org/skillevaluator/config/license_config.yaml}; Python
	 * {@code load_license_config}.
	 */
	private static final class LicenseConfig {

		private static final String RESOURCE_PATH = "org/iromu/embabel/smith/skillevaluator/config/license_config.yaml";

		private LicenseConfig() {
		}

		@SuppressWarnings("unchecked")
		static Map<String, Object> load() {
			ClassLoader loader = LicenseValidator.class.getClassLoader();
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
					throw new IllegalStateException("license_config.yaml must contain a mapping");
				}
				return (Map<String, Object>) data;
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

	}

}
