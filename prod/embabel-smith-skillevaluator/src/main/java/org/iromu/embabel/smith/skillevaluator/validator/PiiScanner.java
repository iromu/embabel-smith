/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
 * The deterministic PII half of upstream's {@code security} check, ported from the PII
 * section of {@code validators/security.py} ({@code _scan_for_pii},
 * {@code _scan_file_for_pii}, {@code _compile_pii_patterns} and their false-positive
 * guards) plus {@code validators/pii_ownership.py}. It is the {@code pii} Tier 1 check:
 * display name {@code PII Scan}, description {@code Detect PII and local
 * identifiers} (upstream {@code tier1/commands.py}).
 * <h2>What the check does</h2> Every scannable file of the skill is matched against the
 * patterns in {@code org/skillevaluator/config/pii_patterns.yaml} (copied byte-for-byte
 * from upstream's config dir) with per-category guards that drop the technical data those
 * shapes also describe: version literals and CIDR-looking artifact names for IPv4,
 * Luhn/GUID/identifier rules for card numbers, scientific-notation exponents for phone
 * numbers, GeoJSON and geospatial cues for coordinate pairs, the Proton virtual profile
 * for personal paths, and an identity rule for {@code /home/<user>/} — a home directory
 * counts as PII only when it belongs to the skill author or the submitter, so unrelated
 * {@code /home/root/} examples stay unflagged without an organization-specific allowlist.
 * Comment lines in the file's own syntax are skipped (see {@link PiiCommentLines}) and a
 * declared ownership contact is reported as {@code ownership_emails} rather than
 * {@code emails} (see {@link PiiOwnership}).
 * <p>
 * Findings are then folded: repeated values produce one finding per
 * {@code (check, value)} carrying an occurrence list and the
 * {@code " — N occurrences (…)"} message suffix, so a doc-heavy skill that repeats one
 * address twenty times reads as one row.
 * <h2>Documented deviations from upstream</h2>
 * <ul>
 * <li><b>No waiver machinery and no LLM verification.</b> {@code pii_waiver_path},
 * {@code _with_pii_waivers} and {@code verify_llm} are out of scope for this port, so
 * every value the deterministic scan reports is reported. The {@code evidence_sha256}
 * metadata key is still emitted — it is part of the finding's shape, and a waiver file is
 * what consumes it.</li>
 * <li><b>No cross-finding secret redaction.</b> Upstream runs every message, line content
 * and metadata string through {@code utils/secret_redaction.Redactor}, which masks a
 * credential literal that also appears in some <em>other</em> finding's text. That helper
 * is a reporting-evidence utility rather than part of the PII scan, and it is not ported
 * here. The per-finding rules that do belong to the scan are kept exactly: a value in a
 * {@code SENSITIVE_PII_CATEGORIES} check is shown as {@code [REDACTED]} and its line
 * content is dropped. The residual gap is therefore only a credential repeated into an
 * unrelated finding's message, and the finding itself still names nothing secret.</li>
 * <li><b>{@code str.casefold()}.</b> Java has no full Unicode case folding, so the
 * grouping key and the {@code evidence_sha256} digest use
 * {@link String#toLowerCase(Locale)} with {@link Locale#ROOT} plus a hard-coded
 * supplement of the {@code CaseFolding.txt} full folds simple lowercasing misses — the
 * multichar Latin folds ({@code ß} folds to {@code ss}, long s to {@code s}, the
 * {@code FB0x} ligatures), the Greek accented and iota-subscript forms, and the Cyrillic
 * and Armenian variants. One block still diverges: Cherokee, where Java folds
 * <em>farther</em> than Python does, into a different code-point block. A value in that
 * script groups and digests differently than upstream would, consistently within a
 * run.</li>
 * <li><b>Submitter identity source.</b> Upstream falls back to {@code getpass.getuser()};
 * the JVM has no passwd lookup, so the fallback is the {@code user.name} system property.
 * The environment variables in {@link Constants#HOME_PATH_SUBMITTER_ENV_VARS} are
 * consulted first, exactly as upstream does; {@link #PiiScanner(Collection)} overrides
 * them and {@link #PiiScanner(Function)} replaces the environment lookup itself.</li>
 * <li><b>Python tokenizer.</b> The Python comment model is a line-level state machine
 * rather than {@code tokenize}; see {@link PiiCommentLines} for the exact difference and
 * its consequences.</li>
 * <li><b>Regex engine.</b> Python patterns are transcribed verbatim into
 * {@code java.util.regex} compiled with
 * {@code CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS} where upstream's
 * {@code re.IGNORECASE} and Unicode {@code \w\d\s\b} semantics apply, and the
 * {@code (?-i:…)} groups the credit-card pattern uses to escape IGNORECASE are Java's own
 * scoped-flag form. Python's {@code $}/{@code \Z} become {@code \z} (upstream searches
 * line-sized strings that never end in a newline, so the engines agree), and a bounded
 * search window keeps Python's {@code search(text, pos, end)} context by searching a
 * one-character-wider slice, so a lookbehind still sees the character before
 * {@code pos}.</li>
 * <li><b>Line ends.</b> Files are read in Python's universal-newline mode, so a CRLF
 * skill is scanned as LF text and no {@code line_content} carries a trailing carriage
 * return.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public class PiiScanner extends ValidatorBase {

	/** Classpath copy of upstream's {@code config/pii_patterns.yaml}. */
	static final String PATTERNS_RESOURCE = "org/iromu/embabel/smith/skillevaluator/config/pii_patterns.yaml";

	/**
	 * Python {@code SENSITIVE_PII_CATEGORIES} (from {@code utils/secret_redaction.py}).
	 */
	static final Set<String> SENSITIVE_PII_CATEGORIES = Set.of("database_credentials", "jwt_tokens",
			"hardcoded_secrets", "webhook_urls", "aws_identifiers", "github_tokens", "private_keys");

	private static final System.Logger LOG = System.getLogger(PiiScanner.class.getName());

	/**
	 * Flags Python's {@code re.compile(raw, re.IGNORECASE)} plus Unicode
	 * {@code \w\d\s\b}.
	 */
	private static final int PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

	/** Python {@code _AUTHOR_IDENTITY_RE}. */
	private static final Pattern AUTHOR_IDENTITY = Pattern
		.compile("\\A\\S[^<>\\n]* <(?<email>[^<>@\\s]+@[^<>\\s]+)>\\z", Pattern.UNICODE_CHARACTER_CLASS);

	/**
	 * Email local part, as in {@code _usernames_from_identity}'s
	 * {@code ([A-Za-z0-9._%+\-]+)@}.
	 */
	private static final Pattern EMAIL_LOCAL_PART = Pattern.compile("([A-Za-z0-9._%+\\-]+)@");

	/**
	 * Frontmatter author key, as in
	 * {@code re.match(r"^\s*author\s*:", line, re.IGNORECASE)}.
	 */
	private static final Pattern FRONTMATTER_AUTHOR_KEY = Pattern.compile("\\A\\s*author\\s*:", PATTERN_FLAGS);

	/** Python {@code _GPS_ZERO_PATTERN}. */
	private static final Pattern GPS_ZERO_PATTERN = Pattern.compile("[-+]?0+\\.0+[,\\s]+[-+]?0+\\.0+");

	/** Python {@code _GPS_STRONG_CONTEXT_PATTERN}. */
	private static final Pattern GPS_STRONG_CONTEXT_PATTERN = Pattern
		.compile("(?i)\\b(?:gps|geolocations?|geospatial|latitudes?|longitudes?|lats?|lons?|lngs?|latlngs?|latlong?s?"
				+ "|lonlats?|lnglats?|wgs[- ]?84)\\d*\\b" + "|\\bgeo:"
				+ "|\\bmaps\\.google\\.|\\bgoogle(?:\\.[a-z]{2,3}){1,2}/maps\\b|\\bmaps\\.apple\\.com\\b"
				+ "|\\bopenstreetmap\\.org\\b" + "|\\bbing\\.com/maps\\b"
				+ "|\\btype[\"']?\\s*:\\s*(?:[\"'](?:(?:multi)?(?:point|linestring|polygon)"
				+ "|(?-i:Feature(?:Collection)?|GeometryCollection))[\"']"
				+ "|(?-i:(?:Multi)?(?:Point|LineString|Polygon)|Feature(?:Collection)?|GeometryCollection)\\b)"
				+ "|\\b(?-i:geometry)[\"']?\\s*:\\s*\\{", Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _GPS_SOFT_CONTEXT_PATTERN}. */
	private static final Pattern GPS_SOFT_CONTEXT_PATTERN = Pattern
		.compile("(?i)\\b(?:locations?|offices?|address(?:es)?|degrees?)\\d*\\b|[°º]", Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _TECHNICAL_COORDINATE_CONTEXT_PATTERN}. */
	private static final Pattern TECHNICAL_COORDINATE_CONTEXT_PATTERN = Pattern.compile(
			"(?i)\\b(?:texture|uv|pixel|screen|canvas|image|normalized|cartesian|robot(?:ic(?:s)?)?|local|world|camera"
					+ "|pose|joint|model|object|viewport|mesh|vertex|clip|device)[ -]+coordinates?\\b",
			Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _TECHNICAL_VECTOR_CONTEXT_PATTERN}. */
	private static final Pattern TECHNICAL_VECTOR_CONTEXT_PATTERN = Pattern
		.compile("(?i)\\b(?:quaternion|pose(?:[ _-]?translation)?|waypoint|vector|calibration|roi|scale)s?\\b"
				+ "|\\bcoordinates?\\s*=|(?:^|\\s)(?:x\\s+y|u\\s+v)(?:\\s|$)", Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _CAMEL_CASE_BOUNDARY}. */
	private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

	/** Python {@code _JSON_TOKEN_PATTERN}. */
	private static final Pattern JSON_TOKEN_PATTERN = Pattern
		.compile("\"((?:[^\"\\\\\n]|\\\\.)*)\"|'((?:[^'\\\\\n]|\\\\.)*)'|([A-Za-z_$][\\w$]*)|([{}\\[\\]:,])");

	/** Python {@code _GEOJSON_GEOMETRY_TYPE}. */
	private static final Pattern GEOJSON_GEOMETRY_TYPE = Pattern.compile("(?i)(?:multi)?(?:point|linestring|polygon)");

	/** Python {@code _GEOJSON_BARE_GEOMETRY_TYPE}. */
	private static final Pattern GEOJSON_BARE_GEOMETRY_TYPE = Pattern.compile("(?:Multi)?(?:Point|LineString|Polygon)");

	/** Python {@code _SCIENTIFIC_LITERAL_PATTERN}. */
	private static final Pattern SCIENTIFIC_LITERAL_PATTERN = Pattern
		.compile("(?<![\\w.])[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)[eE][-+]?\\d+");

	/** Python {@code _UNDERSCORE_JOINED_PREFIX_PATTERN}. */
	private static final Pattern UNDERSCORE_JOINED_PREFIX_PATTERN = Pattern
		.compile("(?-i:([A-Za-z0-9]+(?:_[A-Za-z0-9]+)*)_)\\z");

	/** Python {@code _NON_PAYMENT_NUMBER_CONTEXT_PATTERN}. */
	private static final Pattern NON_PAYMENT_NUMBER_CONTEXT_PATTERN = Pattern
		.compile("(?i)(?:(?<![A-Za-z0-9])(?:(?:timestamp|unix(?:[_ -]?time)?|epoch)"
				+ "(?:[_ -]?(?:microseconds?|micros?|us|usec|nanoseconds?|nanos?|ns|nsec))?"
				+ "|(?:microseconds?|nanoseconds?)(?:[_ -]?timestamp)?)\\b"
				+ "|(?<![A-Za-z0-9])(?:md5|sha(?:1|224|256|384|512)|hash|digest|checksum)\\b)"
				+ "[\"']?(?:\\s*[:=]\\s*[\"']?|\\s+[\"']?)\\z", Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _VERSION_LABEL_PATTERN}. */
	private static final Pattern VERSION_LABEL_PATTERN = Pattern
		.compile("(?:" + "(?i:(?<![a-z0-9])(?:[a-z0-9]+[_-])*(?:versions?|tags?)(?:[_-][a-z0-9]+)*(?![a-z0-9]))"
				+ "|(?<![A-Za-z0-9])(?:[A-Za-z][a-z0-9]*)*(?:Version|Versions|Tag|Tags)"
				+ "(?:[A-Z][A-Za-z0-9]*)*(?![A-Za-z0-9])" + "|(?<![A-Za-z0-9])(?:version|versions|tag|tags)"
				+ "(?:[A-Z][A-Za-z0-9]*)+(?![A-Za-z0-9])" + ")");

	/** Python {@code _PACKAGE_VERSION_CALL_PATTERN}. */
	private static final Pattern PACKAGE_VERSION_CALL_PATTERN = Pattern.compile(
			"(?i)\\b[a-z_]\\w*(?:wheel|archive|artifact|package|conda)[a-z_]*\\([^)]*\\z",
			Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _PACKAGE_ARTIFACT_SUFFIX_PATTERN}. */
	private static final String PACKAGE_ARTIFACT_SUFFIX_PATTERN = "(?:7z|apk|conda|crate|deb|egg|gem|jar|nupkg|rpm|tgz|tbz2|txz|whl|zip|"
			+ "tar\\.(?:bz2|gz|xz|zst))";

	/** Python {@code _NETWORK_ADDRESS_PATTERN}. */
	private static final Pattern NETWORK_ADDRESS_PATTERN = Pattern
		.compile("(?i)(?:\\A|[^a-z0-9])(?:address|dns|endpoint|gateway|host|hostname|ip|mirror|nameserver|proxy|"
				+ "registry|resolver|server|uri|url)(?=\\z|[^a-z0-9])");

	/** Python {@code _URL_AUTHORITY_PREFIX_PATTERN}. */
	private static final Pattern URL_AUTHORITY_PREFIX_PATTERN = Pattern
		.compile("(?i)[a-z][a-z0-9+.-]*://[^/\\s\"']*\\z", Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _IPV4_LITERAL_PATTERN}. */
	private static final Pattern IPV4_LITERAL_PATTERN = Pattern
		.compile("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])");

	/** Python {@code _USER_AGENT_PRODUCT_PREFIX_PATTERN}. */
	private static final Pattern USER_AGENT_PRODUCT_PREFIX_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9]*\\\\?/\\z");

	/** Python {@code _REDUCED_USER_AGENT_VERSION_PATTERN}. */
	private static final Pattern REDUCED_USER_AGENT_VERSION_PATTERN = Pattern.compile("\\d{1,3}\\.0\\.0\\.0");

	/** Python {@code _UUID_PATTERN}. */
	private static final Pattern UUID_PATTERN = Pattern
		.compile("(?i)(?<!\\w)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}(?!\\w)", Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _PAYMENT_LABEL_PATTERN}. */
	private static final Pattern PAYMENT_LABEL_PATTERN = Pattern
		.compile("(?i)(?<![a-z0-9])(?:(?:credit[_ -]?)?card(?:[_ -]?(?:number|num|no))?|cc[_ -]?(?:number|num|no)|pan)"
				+ "(?![a-z0-9])[\"']?(?:\\s*[:=]\\s*[\"']?|\\s+[\"']?)\\z", Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code _PAYMENT_LABEL_WORDS}. */
	private static final Set<String> PAYMENT_LABEL_WORDS = Set.of("card", "cards", "cc", "ccn", "pan", "credit",
			"debit", "payment", "visa", "mastercard", "amex");

	/** Python {@code SecurityValidator._PROTON_PREFIX}. */
	private static final String PROTON_PREFIX = "pfx/drive_c";

	/** Python {@code SecurityValidator._PROTON_PREFIX_BOUNDARIES}. */
	private static final String PROTON_PREFIX_BOUNDARIES = "/\\'\"`=(){}[]<>|*,; \t";

	/**
	 * Python {@code SecurityValidator._DOT_DOT_BOUNDARY}: the boundary character class
	 * plus the two percent-encoded separators as whole alternatives — {@code %2f} and
	 * {@code %5c} are boundaries themselves, not class members.
	 */
	private static final String DOT_DOT_BOUNDARY = "(?:[/\\\\'\"`=(){}\\[\\]<>|*,; &:\\s]|%2f|%5c)";

	/** Python {@code SecurityValidator._DOT_DOT_SEGMENT_PATTERN}. */
	private static final Pattern DOT_DOT_SEGMENT_PATTERN = Pattern.compile(
			"(?:\\A|" + DOT_DOT_BOUNDARY + ")(?:\\.|%2e){2}(?=\\z|" + DOT_DOT_BOUNDARY + ")",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

	/** Python {@code SecurityValidator._LOW_CONFIDENCE_HINTS}, in upstream order. */
	private static final List<String> LOW_CONFIDENCE_HINTS = List.of("words", "characters", "chars", "lines", "pages",
			"items", "files", "bytes", "tokens", "total", "count", "size", "length", "offset", "index", "version",
			"Timestamp", "timestamp", "duration", "http://", "https://", "pageId", "url=", "href=", "src=", "```",
			"import ", "from ", "def ", "class ", "return ");

	/** Python {@code _estimate_confidence}'s always-high categories. */
	private static final Set<String> HIGH_CONFIDENCE_CATEGORIES = Set.of("ssn", "private_keys", "hardcoded_secrets",
			"database_credentials", "github_tokens", "aws_identifiers", "jwt_tokens", "webhook_urls");

	/**
	 * Python {@code re.fullmatch(r"[0-9\\s-]*[0-9][0-9\\s-]*", exc)} for card exceptions.
	 */
	private static final Pattern NUMERIC_EXCEPTION = Pattern.compile("[0-9\\s-]*[0-9][0-9\\s-]*");

	/** Python {@code re.sub(r"[\s-]", "", value)} — card digits in any grouping. */
	private static final Pattern CARD_SEPARATORS = Pattern.compile("[\\s-]");

	private final Function<String, String> environment;

	private final Set<String> submitterOverride;

	private Set<String> submitterCache;

	private boolean homeCheckDisabledWarned;

	private Map<String, Object> piiPatterns;

	private List<CompiledPattern> compiledPatterns;

	/**
	 * Submitter identity is auto-detected from the environment, like upstream's default.
	 */
	public PiiScanner() {
		this(System::getenv, null);
	}

	/**
	 * Python {@code SecurityValidator(submitter_usernames=…)}: use these identities for
	 * the home-path check instead of the environment. An empty collection disables
	 * submitter detection entirely, which is how the upstream tests keep a result
	 * independent of whoever runs it.
	 * @param submitterUsernames identities whose {@code /home/<user>/} paths count as
	 * PII; normalized to trimmed lowercase, and empty disables the home-path check
	 */
	public PiiScanner(Collection<String> submitterUsernames) {
		this(System::getenv, normalizeUsernames(submitterUsernames));
	}

	/**
	 * Test seam for the environment lookup. The JVM cannot change its own environment, so
	 * the {@link Constants#HOME_PATH_SUBMITTER_ENV_VARS} read goes through this function
	 * ({@code System::getenv} by default).
	 * @param environment looks up one submitter-environment variable by name, returning
	 * its identity or {@code null}
	 */
	public PiiScanner(Function<String, String> environment) {
		this(environment, null);
	}

	private PiiScanner(Function<String, String> environment, Set<String> submitterOverride) {
		this.environment = environment;
		this.submitterOverride = submitterOverride;
	}

	@Override
	public String getName() {
		return "PII Scan";
	}

	@Override
	public String getDescription() {
		return "Detect PII and local identifiers";
	}

	/**
	 * Port of {@code validate_pii_only}: folder-or-skill PII scan, no SkillSpector stage.
	 */
	@Override
	public ValidationResult validate(Path skillPath) {
		ValidationResult result = validateFolderOrSkill(skillPath, this::scanForPii, "PII scanning");
		result.setValidatorName(getName());
		result.setValidatorDescription(getDescription());
		return result;
	}

	// ------------------------------------------------------------------ scan

	/**
	 * Python {@code _scan_for_pii}.
	 * @param skillPath folder or single skill file to scan
	 * @return the scan result: one folded finding per {@code (check, value)}, or the
	 * success and warning messages when nothing matched
	 */
	ValidationResult scanForPii(Path skillPath) {
		ValidationResult result = new ValidationResult();
		List<Path> files = ScanPaths.iterScannableFiles(skillPath, Constants.SCANNABLE_EXTENSIONS);
		if (files.isEmpty()) {
			result.addWarning("No scannable files found for PII scan");
			return result;
		}

		result.getSummary().setFilesScanned(files.size());
		result.addSuccess("pii_scan_start", "Scanning " + files.size() + " files for PII",
				Map.of("file_count", files.size()));

		// Identities whose /home/<user>/ paths count as PII for this skill. When
		// neither the submitter nor the author resolves, the home-path check is a
		// no-op; warn once per instance so operators can enable it via
		// SKILLEVALUATOR_SUBMITTER (logged, not per matched line).
		Set<String> protectedUsernames = protectedHomeUsernames(skillPath);
		if (protectedUsernames.isEmpty() && !this.homeCheckDisabledWarned) {
			this.homeCheckDisabledWarned = true;
			LOG.log(System.Logger.Level.WARNING,
					"home-path PII check disabled: could not resolve author or submitter identity. "
							+ "Set SKILLEVALUATOR_SUBMITTER (or add 'author' to SKILL.md) to enable it.");
		}

		// The same matched value often repeats across a doc-heavy skill; report one
		// finding per (check, value) with the occurrence list.
		Map<String, Group> groups = new LinkedHashMap<>();
		for (Path filePath : files) {
			String relativePath = relativize(filePath, skillPath);
			for (Candidate data : scanFileForPii(filePath, protectedUsernames, skillPath)) {
				String value = data.matchedValue();
				String key = (value == null || value.isEmpty())
						? "p" + data.category() + "\u0000" + relativePath + "\u0000" + data.line()
						: "v" + data.category() + "\u0000" + casefold(value);
				groups.computeIfAbsent(key, (ignored) -> new Group(data, relativePath))
					.add(relativePath, data.line(), data.confidence());
			}
		}

		for (Group group : groups.values()) {
			result.addStructuredFinding(toFinding(group), isErrorSeverity(group.first().severity()));
		}

		if (groups.isEmpty()) {
			result.addSuccess("pii_detection",
					"No PII detected in " + files.size() + " files (emails, SSNs, phone numbers, paths)",
					Map.of("files_scanned", files.size()));
		}
		return result;
	}

	/**
	 * Python's per-group {@code Finding(...)} construction.
	 * @param group one {@code (check, value)} group of occurrences
	 * @return the finding to report: masked value and dropped line content for a
	 * sensitive category, occurrence metadata and message suffix when the value repeats
	 */
	private Finding toFinding(Group group) {
		Candidate first = group.first();
		String severity = first.severity().toUpperCase(Locale.ROOT);
		String value = first.matchedValue();
		boolean hasValue = value != null && !value.isEmpty();
		boolean sensitive = SENSITIVE_PII_CATEGORIES.contains(first.category());
		String displayValue = (sensitive && hasValue) ? "[REDACTED]" : value;

		String message = (displayValue != null && !displayValue.isEmpty()) ? first.description() + ": " + displayValue
				: first.description();

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("confidence", strongestConfidence(group.confidences()));
		if (hasValue) {
			metadata.put("matched_value", displayValue);
			metadata.put("occurrence_count", group.occurrences().size());
			List<Map<String, Object>> occurrences = new ArrayList<>();
			for (Occurrence occurrence : group.occurrences()) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("file", occurrence.file());
				entry.put("line", occurrence.line());
				occurrences.add(entry);
			}
			metadata.put("occurrences", occurrences);
		}
		if (group.occurrences().size() > 1) {
			message += " \u2014 " + group.occurrences().size() + " occurrences ("
					+ formatOccurrences(group.occurrences()) + ")";
		}
		if (hasValue && !sensitive) {
			// Identifies the matched value for a PII waiver. Only a value the report
			// shows in full gets one: a digest of a masked credential would let
			// anyone holding the report test guesses for it offline.
			metadata.put("evidence_sha256", evidenceSha256(first.category(), value));
		}

		String lineContent = null;
		if (!sensitive && first.lineContent() != null && !first.lineContent().isEmpty()) {
			lineContent = first.lineContent();
		}
		return new Finding("PII", Severity.fromString(severity), first.category(), message, group.firstFile(),
				first.line(), lineContent, first.suggestion(), metadata);
	}

	/**
	 * Python {@code _format_occurrences}: {@code a.md lines 3, 9; b.md line 12}.
	 * @param occurrences the places a value was seen, in discovery order
	 * @return the occurrences grouped per file, joined with {@code "; "}, capped at 3
	 * files and 10 lines each
	 */
	static String formatOccurrences(List<Occurrence> occurrences) {
		return formatOccurrences(occurrences, 3, 10);
	}

	static String formatOccurrences(List<Occurrence> occurrences, int maxFiles, int maxLines) {
		Map<String, List<Integer>> byFile = new LinkedHashMap<>();
		for (Occurrence occurrence : occurrences) {
			byFile.computeIfAbsent(occurrence.file(), (ignored) -> new ArrayList<>()).add(occurrence.line());
		}
		List<String> parts = new ArrayList<>();
		int index = 0;
		for (Map.Entry<String, List<Integer>> entry : byFile.entrySet()) {
			if (index == maxFiles) {
				parts.add("+" + (byFile.size() - maxFiles) + " more file(s)");
				break;
			}
			index++;
			List<Integer> lines = entry.getValue();
			StringBuilder shown = new StringBuilder();
			for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
				if (i > 0) {
					shown.append(", ");
				}
				shown.append(lines.get(i));
			}
			if (lines.size() > maxLines) {
				shown.append(", …");
			}
			parts.add(entry.getKey() + ((lines.size() == 1) ? " line " : " lines ") + shown);
		}
		return String.join("; ", parts);
	}

	// ----------------------------------------------------------- one file

	/**
	 * Python {@code _scan_file_for_pii}.
	 * @param filePath the file to scan
	 * @param protectedUsernames identities whose home paths count as PII; {@code null} is
	 * treated as none
	 * @param skillRoot scanned root that decides whether this file is the manifest
	 * declaring ownership contacts; {@code null} falls back to the file's parent
	 * @return one candidate per matched line, empty for an unreadable file or no matches
	 */
	List<Candidate> scanFileForPii(Path filePath, Set<String> protectedUsernames, Path skillRoot) {
		String content = readUtf8Lenient(filePath);
		if (content == null) {
			LOG.log(System.Logger.Level.WARNING, "Could not read " + filePath);
			return List.of();
		}

		List<String> lines = splitOnNewline(content);
		Map<Integer, String> authorEmails = frontmatterAuthorEmails(filePath, lines);
		Path root = (skillRoot != null) ? skillRoot : filePath.getParent();
		// Only the manifest sitting directly in the scanned directory may declare
		// ownership contacts; a SKILL.md nested deeper in the tree is content.
		boolean isRootManifest = root != null && (root.equals(filePath.getParent()) || root.equals(filePath));
		Map<Integer, List<int[]>> ownershipSpans = isRootManifest ? PiiOwnership.ownershipEmailSpans(filePath, content)
				: Map.of();
		Set<Integer> commentLines = PiiCommentLines.commentLineNumbers(filePath, lines);

		List<Candidate> findings = new ArrayList<>();
		for (CompiledPattern compiled : compiledPatterns()) {
			scanLinesForPattern(lines, compiled, filePath, findings,
					(protectedUsernames != null) ? protectedUsernames : Set.of(), authorEmails, commentLines,
					ownershipSpans);
		}
		return findings;
	}

	/**
	 * Python {@code _frontmatter_author_emails}: frontmatter lines that echo the declared
	 * author address.
	 * @param filePath the file being scanned, only its name consulted for the manifest
	 * test
	 * @param lines the file split on newlines
	 * @return line number to declared address for each frontmatter author line, empty
	 * unless this is a manifest with a parseable author identity
	 */
	Map<Integer, String> frontmatterAuthorEmails(Path filePath, List<String> lines) {
		Map<Integer, String> out = new LinkedHashMap<>();
		String fileName = filePath.getFileName().toString();
		if (!Constants.SKILL_MANIFEST_VARIANTS.contains(fileName) || lines.isEmpty()
				|| !isFrontmatterDelimiter(lines.get(0))) {
			return out;
		}
		int frontmatterEnd = -1;
		for (int index = 1; index < lines.size(); index++) {
			if (isFrontmatterDelimiter(lines.get(index))) {
				frontmatterEnd = index;
				break;
			}
		}
		if (frontmatterEnd < 0) {
			return out;
		}
		Matcher identity = AUTHOR_IDENTITY
			.matcher((authorIdentity(filePath.getParent()) == null) ? "" : authorIdentity(filePath.getParent()));
		if (!identity.matches()) {
			return out;
		}
		String authorEmail = identity.group("email");
		for (int index = 1; index < frontmatterEnd; index++) {
			String line = lines.get(index);
			int lineNumber = index + 1;
			if (FRONTMATTER_AUTHOR_KEY.matcher(line).lookingAt()
					&& line.toLowerCase(Locale.ROOT).contains(authorEmail.toLowerCase(Locale.ROOT))) {
				out.put(lineNumber, authorEmail);
			}
		}
		return out;
	}

	/**
	 * Python {@code _is_frontmatter_delimiter} (BOM-prefixed openers included).
	 * @param line the line to test, stripped of a byte-order mark
	 * @return {@code true} when the line is nothing but the {@code ---} delimiter
	 */
	static boolean isFrontmatterDelimiter(String line) {
		return line.strip().replace("\uFEFF", "").strip().equals("---");
	}

	/**
	 * Python {@code _compile_pii_patterns}.
	 * @return every config entry with a compilable pattern, global and per-pattern
	 * exceptions merged, cached after the first call
	 */
	private List<CompiledPattern> compiledPatterns() {
		if (this.compiledPatterns != null) {
			return this.compiledPatterns;
		}
		Map<String, Object> patterns = piiPatterns();
		List<String> globalExceptions = globalExceptions(patterns);
		List<CompiledPattern> compiled = new ArrayList<>();
		for (Map.Entry<String, Object> entry : patterns.entrySet()) {
			if ("exceptions".equals(entry.getKey()) || !(entry.getValue() instanceof List<?> patternsList)) {
				continue;
			}
			for (Object item : patternsList) {
				if (!(item instanceof Map<?, ?> definition)) {
					continue;
				}
				Object raw = definition.get("pattern");
				if (raw == null || String.valueOf(raw).isEmpty()) {
					continue;
				}
				Pattern regex;
				try {
					regex = Pattern.compile(String.valueOf(raw), PATTERN_FLAGS);
				}
				catch (PatternSyntaxException ex) {
					continue;
				}
				List<String> exceptions = new ArrayList<>(globalExceptions);
				if (definition.get("exceptions") instanceof List<?> perPattern) {
					for (Object exception : perPattern) {
						exceptions.add(String.valueOf(exception));
					}
				}
				compiled.add(new CompiledPattern(entry.getKey(), regex, exceptions, asStringMap(definition)));
			}
		}
		this.compiledPatterns = compiled;
		return compiled;
	}

	/**
	 * Python {@code pii_patterns} — lazily loaded {@code pii_patterns.yaml}.
	 * @return the parsed pattern config, loaded from the classpath on first use
	 */
	Map<String, Object> piiPatterns() {
		if (this.piiPatterns == null) {
			this.piiPatterns = PiiConfig.load();
		}
		return this.piiPatterns;
	}

	@SuppressWarnings("unchecked")
	private static List<String> globalExceptions(Map<String, Object> patterns) {
		List<String> out = new ArrayList<>();
		if (patterns.get("exceptions") instanceof Map<?, ?> exceptions
				&& exceptions.get("allowed_paths") instanceof List<?> allowed) {
			for (Object path : allowed) {
				out.add(String.valueOf(path));
			}
		}
		return out;
	}

	/**
	 * Python {@code _scan_lines_for_pattern}: one config entry over one file's lines,
	 * skipping comments and applying the category's false-positive guards.
	 * @param lines the file split on newlines
	 * @param compiled the config entry to search for
	 * @param filePath the file being scanned, for its name and GeoJSON cue scan
	 * @param findings accumulator that receives each surviving match
	 * @param protectedUsernames identities whose home paths count as PII
	 * @param authorEmails line number to declared author address, blanked rather than
	 * deleted so offsets stay intact
	 * @param commentLines lines to skip, in the file's own comment syntax
	 * @param ownershipSpans manifest line number to contact spans that reclassify an
	 * email as {@code ownership_emails}
	 */
	private void scanLinesForPattern(List<String> lines, CompiledPattern compiled, Path filePath,
			List<Candidate> findings, Set<String> protectedUsernames, Map<Integer, String> authorEmails,
			Set<Integer> commentLines, Map<Integer, List<int[]>> ownershipSpans) {
		String category = compiled.category();
		List<String> lineExceptions = compiled.exceptions();
		Set<String> exceptedCardDigits = Set.of();
		if ("credit_cards".equals(category)) {
			// Known test card numbers are compared with each candidate in any
			// grouping, so one on a line does not hide a different card number.
			List<String> numericExceptions = new ArrayList<>();
			List<String> otherExceptions = new ArrayList<>();
			for (String exception : compiled.exceptions()) {
				if (NUMERIC_EXCEPTION.matcher(exception).matches()) {
					numericExceptions.add(exception);
				}
				else {
					otherExceptions.add(exception);
				}
			}
			exceptedCardDigits = new LinkedHashSet<>();
			for (String exception : numericExceptions) {
				exceptedCardDigits.add(CARD_SEPARATORS.matcher(exception).replaceAll(""));
			}
			lineExceptions = otherExceptions;
		}

		Set<Integer> geojsonLines = null;
		for (int lineNum = 1; lineNum <= lines.size(); lineNum++) {
			String line = lines.get(lineNum - 1);
			if (commentLines.contains(lineNum)) {
				continue;
			}

			String scanLine = line;
			String authorEmail = "emails".equals(category) ? authorEmails.get(lineNum) : null;
			if (authorEmail != null) {
				// Blank the address instead of deleting it: source offsets must stay
				// intact for adjacent ownership contacts in flow YAML.
				scanLine = blankFirstOccurrence(line, authorEmail);
			}

			List<Cand> matches = allMatches(compiled.regex(), scanLine);
			if (!exceptedCardDigits.isEmpty()) {
				Set<String> digits = exceptedCardDigits;
				matches = matches.stream()
					.filter((match) -> !digits.contains(CARD_SEPARATORS.matcher(match.value()).replaceAll("")))
					.toList();
			}
			if (matches.isEmpty() || containsAny(line, lineExceptions)) {
				continue;
			}
			if ("credit_cards".equals(category) || "phone_numbers".equals(category)) {
				String numericCategory = category;
				matches = matches.stream()
					.filter((match) -> !isNumericPiiFalsePositive(numericCategory, match, line))
					.toList();
			}
			if ("gps_coordinates".equals(category)) {
				// A position inside a GeoJSON geometry's "coordinates" array is cued
				// however far its rings sit below the "type" line.
				if (geojsonLines == null) {
					geojsonLines = geojsonCoordinateLines(lines);
				}
				if (!geojsonLines.contains(lineNum)) {
					String context = filePath.getFileName().toString() + "\n"
							+ String.join("\n", lines.subList(Math.max(0, lineNum - 3), lineNum));
					int previousEnd = 0;
					List<Cand> kept = new ArrayList<>();
					for (Cand pair : matches) {
						if (!lacksGeospatialContext(line, context, pair.start(), previousEnd)) {
							kept.add(pair);
						}
						previousEnd = pair.end();
					}
					matches = kept;
				}
			}
			else if ("ip_addresses".equals(category)) {
				matches = matches.stream().filter((match) -> !isVersionLiteral(match, line)).toList();
			}
			else if ("personal_paths".equals(category)) {
				matches = matches.stream().filter((match) -> !isProtonVirtualProfile(match, line)).toList();
			}
			else if ("home_paths".equals(category)) {
				// /home/<root>/ is PII only when <root> is the author/submitter.
				matches = matches.stream().filter((match) -> isPersonalHomePath(match, protectedUsernames)).toList();
			}
			else if ("credit_cards".equals(category)) {
				// Check every candidate: a GUID or non-Luhn number must not hide a later
				// card.
				String previousLine = (lineNum > 1) ? lines.get(lineNum - 2) : "";
				matches = matches.stream().filter((match) -> isCardNumberCandidate(match, line, previousLine)).toList();
			}
			if (matches.isEmpty()) {
				continue;
			}
			Cand match = matches.get(0);

			String confidence = estimateConfidence(category, line);
			if ("gps_coordinates".equals(category) && isNearZeroGps(line)) {
				confidence = "low";
			}

			// Preserve the first occurrence of each email class on a line: an
			// ownership contact must not hide an ordinary address beside it.
			Map<String, Cand> candidates = new LinkedHashMap<>();
			if ("emails".equals(category)) {
				List<int[]> spans = ownershipSpans.getOrDefault(lineNum, List.of());
				for (Cand candidate : matches) {
					String check = spans.stream()
						.anyMatch((span) -> span[0] == candidate.start() && span[1] == candidate.end())
								? "ownership_emails" : "emails";
					candidates.putIfAbsent(check, candidate);
				}
			}
			else {
				candidates.put(category, match);
			}

			for (Map.Entry<String, Cand> candidate : candidates.entrySet()) {
				String check = candidate.getKey();
				String description = stringOr(compiled.definition().get("description"), category);
				String suggestion = optionalString(compiled.definition().get("suggestion"));
				if ("ownership_emails".equals(check)) {
					description = "Ownership contact email in skill metadata";
					suggestion = "Review whether ownership contacts are permitted for this repository. "
							+ "If approved, set PII.ownership_emails to info in an explicit --policy file; "
							+ "otherwise remove the address or use a placeholder.";
				}
				findings.add(new Candidate(filePath.getFileName().toString(), lineNum, line,
						stringOr(compiled.definition().get("severity"), "medium"), description, suggestion, check,
						confidence, candidate.getValue().value()));
			}
		}
	}

	// ------------------------------------------------------- false-positive guards

	/**
	 * Python {@code _estimate_confidence}.
	 * @param category the check being reported
	 * @param line the matched line, searched for the technical hint words
	 * @return {@code high} for an always-high category, otherwise {@code low} at two
	 * hints, {@code medium} at one and {@code high} at none
	 */
	String estimateConfidence(String category, String line) {
		if (HIGH_CONFIDENCE_CATEGORIES.contains(category)) {
			return "high";
		}
		String lineLower = line.toLowerCase(Locale.ROOT);
		int hints = 0;
		for (String hint : LOW_CONFIDENCE_HINTS) {
			if (lineLower.contains(hint.toLowerCase(Locale.ROOT))) {
				hints++;
			}
		}
		if (hints >= 2) {
			return "low";
		}
		return (hints == 1) ? "medium" : "high";
	}

	/**
	 * Python {@code _is_personal_home_path}: anchored to the captured first component
	 * after {@code /home/}.
	 * @param match the home-path candidate, whose first capture group holds the user
	 * component
	 * @param protectedUsernames identities that count as personal
	 * @return {@code true} when the captured component is one of them; a missing or empty
	 * capture, or no protected identity, is never PII
	 */
	static boolean isPersonalHomePath(Cand match, Set<String> protectedUsernames) {
		// Python bails when the pattern has no group or no group participated in the
		// match; a null group(1) is the same condition here.
		String root = match.group();
		if (root == null) {
			return false;
		}
		String trimmed = root.strip();
		if (trimmed.isEmpty() || protectedUsernames.isEmpty()) {
			return false;
		}
		return protectedUsernames.contains(trimmed.toLowerCase(Locale.ROOT));
	}

	/**
	 * Python {@code _is_near_zero_gps}.
	 * @param line the matched line
	 * @return {@code true} when it holds a {@code 0.0, 0.0} pair, the null-island
	 * position
	 */
	static boolean isNearZeroGps(String line) {
		return GPS_ZERO_PATTERN.matcher(line).find();
	}

	/**
	 * Python {@code _is_proton_virtual_profile}.
	 * @param match the personal-path candidate, expected to be {@code /users/steamuser/}
	 * @param line the matched line, inspected for the preceding prefix
	 * @return {@code true} when {@code pfx/drive_c} reaches the match at a boundary and
	 * no {@code ..} segment follows it, so the Proton mount is not a traversal
	 */
	static boolean isProtonVirtualProfile(Cand match, String line) {
		String value = match.value();
		if (!isAscii(value) || !value.toLowerCase(Locale.ROOT).equals("/users/steamuser/")) {
			return false;
		}
		int start = match.start() - PROTON_PREFIX.length();
		String prefix = line.substring(Math.max(start, 0), match.start());
		if (start < 0 || !isAscii(prefix)
				|| !prefix.toLowerCase(Locale.ROOT).replace('\\', '/').equals(PROTON_PREFIX)) {
			return false;
		}
		if (start > 0 && PROTON_PREFIX_BOUNDARIES.indexOf(line.charAt(start - 1)) < 0) {
			return false;
		}
		// A profile reached through ".." is a traversal, not Proton's own layout.
		return !DOT_DOT_SEGMENT_PATTERN.matcher(line.substring(match.end())).find();
	}

	/**
	 * Python {@code _is_numeric_pii_false_positive}.
	 * @param category the check being reported; only {@code credit_cards} and
	 * {@code phone_numbers} have guards
	 * @param match the candidate whose offsets anchor each context window
	 * @param line the matched line, searched backwards for a label and around the match
	 * for an exponent
	 * @return {@code true} when the number is technical — a timestamp, hash or identifier
	 * component for a card, a scientific-notation exponent for a phone number
	 */
	static boolean isNumericPiiFalsePositive(String category, Cand match, String line) {
		if ("credit_cards".equals(category)) {
			// A Luhn-valid timestamp or digest is not payment data. Only a label
			// directly before this number counts, so a timestamp field does not hide
			// a card number in a later field on the same line.
			int labelStart = Math.max(0, match.start() - 100);
			if (searchWithin(NON_PAYMENT_NUMBER_CONTEXT_PATTERN, line, labelStart, match.start()) != null) {
				return true;
			}
			// An underscore joining the number to a letter or digit makes it part of
			// an identifier, unless a word in that identifier is a payment label.
			Cand joined = searchWithin(UNDERSCORE_JOINED_PREFIX_PATTERN, line, labelStart, match.start());
			if (joined == null) {
				return false;
			}
			String[] words = CAMEL_CASE_BOUNDARY.matcher(joined.group())
				.replaceAll("_")
				.toLowerCase(Locale.ROOT)
				.split("_");
			for (String word : words) {
				if (PAYMENT_LABEL_WORDS.contains(word)) {
					return false;
				}
			}
			return true;
		}
		if ("phone_numbers".equals(category)) {
			// International phone syntax can begin at the ``+00`` exponent of a
			// scientific-notation value and run into the next column.
			int from = Math.max(0, match.start() - 64);
			for (Cand literal : findAllWithin(SCIENTIFIC_LITERAL_PATTERN, line, from, match.start() + 64)) {
				if (literal.start() <= match.start() && match.start() < literal.end()) {
					return true;
				}
			}
			return false;
		}
		return false;
	}

	/**
	 * Python {@code _cue_text_variants}.
	 * @param text the label or context to search for a cue
	 * @return the text with underscores spaced, plus the camel-case split variant when
	 * splitting changes it
	 */
	static List<String> cueTextVariants(String text) {
		String spaced = text.replace('_', ' ');
		String split = CAMEL_CASE_BOUNDARY.matcher(spaced).replaceAll(" ");
		return split.equals(spaced) ? List.of(spaced) : List.of(spaced, split);
	}

	/**
	 * Python {@code _search_cue}.
	 * @param pattern the cue to look for
	 * @param variants the spellings to try, underscores and camel case alike
	 * @return {@code true} when any variant contains the cue
	 */
	private static boolean searchCue(Pattern pattern, List<String> variants) {
		for (String variant : variants) {
			if (pattern.matcher(variant).find()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Python {@code _lacks_geospatial_context}.
	 * @param line the matched line holding the coordinate pair
	 * @param context the file name plus the lines above it, searched for a far cue
	 * @param pairStart offset where the pair starts, or {@code null} for the whole line
	 * @param labelStart offset the preceding label begins at, the end of the previous
	 * pair
	 * @return {@code true} when no geospatial cue explains the pair, so it is a technical
	 * coordinate and not PII
	 */
	static boolean lacksGeospatialContext(String line, String context, Integer pairStart, int labelStart) {
		List<String> local = cueTextVariants(line);
		// A technical label labels the pair only when it comes before it.
		int labelEnd = (pairStart != null) ? pairStart : line.length();
		String labelSegment = line.substring(labelStart, labelEnd);
		int lastSeparator = Math.max(labelSegment.lastIndexOf(';'), labelSegment.lastIndexOf('|'));
		String tail = labelSegment.substring(lastSeparator + 1);
		List<String> tailVariants = cueTextVariants(tail);
		if (searchCue(GPS_SOFT_CONTEXT_PATTERN, tailVariants) || searchCue(GPS_STRONG_CONTEXT_PATTERN, tailVariants)) {
			labelSegment = tail;
		}
		List<String> label = cueTextVariants(labelSegment);
		List<String> nearby = cueTextVariants(context);
		if (searchCue(GPS_STRONG_CONTEXT_PATTERN, local)) {
			return false;
		}
		boolean inheritsTechnicalLabel = false;
		if (labelStart > 0 && !line.substring(labelStart, labelEnd).contains(";")
				&& !line.substring(labelStart, labelEnd).contains("|")) {
			int previousSeparator = Math.max(line.lastIndexOf(';', labelStart - 1),
					line.lastIndexOf('|', labelStart - 1));
			List<String> previousLabel = cueTextVariants(line.substring(previousSeparator + 1, labelStart));
			inheritsTechnicalLabel = searchCue(TECHNICAL_COORDINATE_CONTEXT_PATTERN, previousLabel)
					|| searchCue(TECHNICAL_VECTOR_CONTEXT_PATTERN, previousLabel);
		}
		if (searchCue(TECHNICAL_COORDINATE_CONTEXT_PATTERN, label) || searchCue(TECHNICAL_VECTOR_CONTEXT_PATTERN, label)
				|| inheritsTechnicalLabel) {
			return !searchCue(GPS_STRONG_CONTEXT_PATTERN, nearby);
		}
		if (searchCue(GPS_SOFT_CONTEXT_PATTERN, local)) {
			return false;
		}
		if (searchCue(GPS_STRONG_CONTEXT_PATTERN, nearby)) {
			return false;
		}
		if (searchCue(TECHNICAL_COORDINATE_CONTEXT_PATTERN, nearby)) {
			return true;
		}
		return !searchCue(GPS_SOFT_CONTEXT_PATTERN, nearby);
	}

	/**
	 * Python {@code _geojson_coordinate_lines}: 1-based lines inside a geometry's
	 * coordinates array.
	 * @param lines the file split on newlines, rejoined to scan its braces and arrays
	 * @return the 1-based lines inside a GeoJSON geometry's coordinates, empty when the
	 * text mentions no {@code coordinates} key
	 */
	static Set<Integer> geojsonCoordinateLines(List<String> lines) {
		Set<Integer> numbers = new LinkedHashSet<>();
		String text = String.join("\n", lines);
		if (!text.contains("coordinates")) {
			return numbers;
		}
		int[] lineStarts = new int[lines.size() + 1];
		lineStarts[0] = 0;
		for (int i = 0; i < lines.size(); i++) {
			lineStarts[i + 1] = lineStarts[i] + lines.get(i).length() + 1;
		}

		List<Frame> stack = new ArrayList<>();
		List<int[]> spans = new ArrayList<>();
		String lastString = null;
		String key = null;
		Matcher tokens = JSON_TOKEN_PATTERN.matcher(text);
		while (tokens.find()) {
			String bare = tokens.group(3);
			String value = (tokens.group(1) != null) ? tokens.group(1) : tokens.group(2);
			if (value == null) {
				value = bare;
			}
			String punct = tokens.group(4);
			Frame top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
			if (value != null) {
				Pattern typePattern = (bare != null) ? GEOJSON_BARE_GEOMETRY_TYPE : GEOJSON_GEOMETRY_TYPE;
				if ("type".equals(key) && top != null && "{".equals(top.kind) && typePattern.matcher(value).matches()) {
					top.geojson = true;
				}
				lastString = value;
				key = null;
			}
			else if (":".equals(punct)) {
				key = lastString;
				lastString = null;
			}
			else if ("{".equals(punct) || "[".equals(punct)) {
				Frame owner = ("[".equals(punct) && "coordinates".equals(key) && top != null && "{".equals(top.kind))
						? top : null;
				stack.add(new Frame(punct, "{".equals(punct) && "geometry".equals(key), owner, tokens.start()));
				key = null;
				lastString = null;
			}
			else if ("}".equals(punct) || "]".equals(punct)) {
				if (stack.isEmpty()) {
					continue;
				}
				Frame frame = stack.remove(stack.size() - 1);
				if (frame.owner != null) {
					frame.owner.spans.add(new int[] { frame.start, tokens.start() });
				}
				if ("{".equals(frame.kind) && frame.geojson) {
					spans.addAll(frame.spans);
				}
				key = null;
				lastString = null;
			}
			else {
				key = null;
				lastString = null;
			}
		}

		// Nested arrays yield nested spans; keep only the outermost of each group.
		spans.sort((a, b) -> (a[0] != b[0]) ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
		int keptEnd = -1;
		for (int[] span : spans) {
			if (span[1] <= keptEnd) {
				continue;
			}
			int first = bisectRight(lineStarts, Math.max(span[0], keptEnd + 1));
			int last = bisectRight(lineStarts, span[1]);
			for (int number = first; number <= last; number++) {
				numbers.add(number);
			}
			keptEnd = span[1];
		}
		return numbers;
	}

	/**
	 * Python {@code bisect.bisect_right}: count of entries {@code <= value}.
	 * @param sorted ascending offsets, here the start offset of each line
	 * @param value the offset to locate
	 * @return the number of entries not exceeding {@code value}, which is the 1-based
	 * line containing it
	 */
	private static int bisectRight(int[] sorted, int value) {
		int low = 0;
		int high = sorted.length;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (sorted[mid] <= value) {
				low = mid + 1;
			}
			else {
				high = mid;
			}
		}
		return low;
	}

	/**
	 * Python {@code _is_version_literal}.
	 * @param match the IPv4-shaped candidate
	 * @param line the matched line, searched for artifact names, user-agent versions and
	 * version labels
	 * @return {@code true} when the number is a package version, a reduced user-agent
	 * version, or the value of a version or tag label rather than an address
	 */
	static boolean isVersionLiteral(Cand match, String line) {
		String prefix = line.substring(0, match.start());
		if (URL_AUTHORITY_PREFIX_PATTERN.matcher(prefix).find()) {
			return false;
		}

		Pattern artifactPattern = Pattern.compile("(?i)[a-z0-9_./+\\\\-]*" + Pattern.quote(match.value())
				+ "[a-z0-9_.+\\\\-]*\\." + PACKAGE_ARTIFACT_SUFFIX_PATTERN + "(?:[?#][^\\s\"']*)?",
				Pattern.UNICODE_CHARACTER_CLASS);
		Matcher artifacts = artifactPattern.matcher(line);
		while (artifacts.find()) {
			if (artifacts.start() <= match.start() && artifacts.end() >= match.end()) {
				return true;
			}
		}

		if (REDUCED_USER_AGENT_VERSION_PATTERN.matcher(match.value()).matches()
				&& USER_AGENT_PRODUCT_PREFIX_PATTERN.matcher(prefix).find() && !line.startsWith("/", match.end())) {
			return true;
		}

		String quotedValue = null;
		int quoteStart = Math.max(line.lastIndexOf('"', match.start()), line.lastIndexOf('\'', match.start()));
		if (quoteStart >= 0) {
			char quote = line.charAt(quoteStart);
			int quoteEnd = line.indexOf(quote, match.end());
			if (quoteEnd >= 0) {
				quotedValue = line.substring(quoteStart + 1, quoteEnd);
			}
		}

		int contextStart = Math.max(0, match.start() - 80);
		int contextEnd = Math.min(line.length(), match.end() + 80);
		String context = line.substring(contextStart, contextEnd);
		if (NETWORK_ADDRESS_PATTERN.matcher(context).find()) {
			return false;
		}
		String prefixContext = line.substring(contextStart, match.start());
		Matcher labels = VERSION_LABEL_PATTERN.matcher(prefixContext);
		int nearestLabelEnd = -1;
		while (labels.find()) {
			nearestLabelEnd = labels.end();
		}
		if (nearestLabelEnd >= 0) {
			String betweenLabelAndValue = prefixContext.substring(nearestLabelEnd);
			boolean assignmentPrefix = Pattern.compile("\\s*(?::|=)\\s*[\\[({'\"]*\\s*")
				.matcher(betweenLabelAndValue)
				.matches();
			if (assignmentPrefix && !IPV4_LITERAL_PATTERN.matcher(betweenLabelAndValue).find()) {
				return true;
			}
		}

		return quotedValue != null && quotedValue.equals(match.value())
				&& PACKAGE_VERSION_CALL_PATTERN.matcher(prefix).find();
	}

	/**
	 * Python {@code _passes_luhn}.
	 * @param digits the candidate with separators already removed
	 * @return {@code true} when the checksum is a nonzero multiple of ten
	 */
	static boolean passesLuhn(String digits) {
		int total = 0;
		for (int i = 0; i < digits.length(); i++) {
			int n = digits.charAt(digits.length() - 1 - i) - '0';
			if (i % 2 == 1) {
				n *= 2;
				if (n > 9) {
					n -= 9;
				}
			}
			total += n;
		}
		return total > 0 && total % 10 == 0;
	}

	/**
	 * Python {@code _is_card_number_candidate}.
	 * @param match the credit-card candidate
	 * @param line the matched line, searched for a GUID containing the match
	 * @param previousLine the line above, whose tail joins the label window
	 * @return {@code true} when the digits pass Luhn and either no GUID contains them or
	 * a payment label precedes that GUID
	 */
	static boolean isCardNumberCandidate(Cand match, String line, String previousLine) {
		String digits = CARD_SEPARATORS.matcher(match.value()).replaceAll("");
		if (!passesLuhn(digits)) {
			return false;
		}
		// A GUID is 36 characters, so one that contains the match lies in this window.
		Cand guid = null;
		for (Cand found : findAllWithin(UUID_PATTERN, line, Math.max(0, match.start() - 36), match.end() + 36)) {
			if (found.start() <= match.start() && match.end() <= found.end()) {
				guid = found;
				break;
			}
		}
		if (guid == null) {
			return true;
		}
		int valueStart = guid.start();
		// A GUID in braces is still an identifier; take the braces into the prefix.
		if (valueStart > 0 && line.charAt(valueStart - 1) == '{' && guid.end() < line.length()
				&& line.charAt(guid.end()) == '}') {
			valueStart -= 1;
		}
		String previousTail = (previousLine.length() > 100) ? previousLine.substring(previousLine.length() - 100)
				: previousLine;
		String prefix = previousTail + "\n" + line.substring(Math.max(0, valueStart - 100), valueStart);
		return PAYMENT_LABEL_PATTERN.matcher(prefix).find();
	}

	// ------------------------------------------------------------- identity

	/**
	 * Python {@code _normalize_usernames}.
	 * @param values the identities as configured, in order; may be {@code null}
	 * @return trimmed lowercase non-blank identities, duplicates dropped, empty for a
	 * {@code null} input
	 */
	static Set<String> normalizeUsernames(Collection<String> values) {
		Set<String> names = new LinkedHashSet<>();
		if (values == null) {
			return names;
		}
		for (String value : values) {
			if (value != null && !value.strip().isEmpty()) {
				names.add(value.strip().toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	/**
	 * Python {@code _usernames_from_identity}.
	 * @param identity an author or submitter string, an email, a {@code Name <email>}
	 * pair or a bare name; may be {@code null}
	 * @return the email's local part plus the bare name when it has no space or angle
	 * bracket, empty for nothing usable
	 */
	static Set<String> usernamesFromIdentity(String identity) {
		Set<String> names = new LinkedHashSet<>();
		if (identity == null || identity.isEmpty()) {
			return names;
		}
		String text = identity.strip();
		Matcher email = EMAIL_LOCAL_PART.matcher(text);
		if (email.find()) {
			names.add(email.group(1).strip().toLowerCase(Locale.ROOT));
		}
		// A display name with a space but no email is not a filesystem username;
		// matching it would re-introduce the false positives this rule removed.
		if (!text.isEmpty() && !text.contains("@") && !text.contains("<") && !text.contains(">")
				&& !text.contains(" ")) {
			names.add(text.toLowerCase(Locale.ROOT));
		}
		names.removeIf((name) -> name.isEmpty());
		return names;
	}

	/**
	 * Python {@code _submitter_usernames}.
	 * @return the constructor override when set, else the identities read from the
	 * submitter environment variables and the {@code user.name} property, cached after
	 * the first call
	 */
	Set<String> submitterUsernames() {
		if (this.submitterOverride != null) {
			return this.submitterOverride;
		}
		if (this.submitterCache != null) {
			return this.submitterCache;
		}
		Set<String> names = new LinkedHashSet<>();
		for (String variable : Constants.HOME_PATH_SUBMITTER_ENV_VARS) {
			names.addAll(usernamesFromIdentity(this.environment.apply(variable)));
		}
		// Upstream falls back to getpass.getuser(); the JVM's equivalent is user.name.
		names.addAll(usernamesFromIdentity(System.getProperty("user.name")));
		this.submitterCache = names;
		return names;
	}

	/**
	 * Python {@code _author_identity}.
	 * @param skillPath the skill directory holding the manifest; may be {@code null}
	 * @return the declared {@code author} as a stripped string, from frontmatter or
	 * {@code metadata}, or {@code null} when absent, unreadable or unparsable
	 */
	String authorIdentity(Path skillPath) {
		if (skillPath == null || !Files.isDirectory(skillPath)) {
			return null;
		}
		Path manifest = findSkillManifest(skillPath);
		if (manifest == null) {
			return null;
		}
		Frontmatter.FrontmatterOutcome parsed = Frontmatter.parse(manifest);
		if (!parsed.isOk()) {
			return null;
		}
		Map<String, Object> data = parsed.parsed().yamlData();
		Object author = data.get("author");
		if (!truthy(author) && data.get("metadata") instanceof Map<?, ?> metadata) {
			author = metadata.get("author");
		}
		return truthy(author) ? String.valueOf(author).strip() : null;
	}

	/**
	 * Python {@code _author_usernames}.
	 * @param skillPath the skill directory whose manifest declares the author
	 * @return the usernames derivable from that author, empty when none is declared
	 */
	Set<String> authorUsernames(Path skillPath) {
		return usernamesFromIdentity(authorIdentity(skillPath));
	}

	/**
	 * Python {@code _protected_home_usernames}.
	 * @param skillPath the skill directory whose manifest may declare an author
	 * @return submitter and author identities merged, the set a home path is tested
	 * against
	 */
	Set<String> protectedHomeUsernames(Path skillPath) {
		Set<String> names = new LinkedHashSet<>(submitterUsernames());
		names.addAll(authorUsernames(skillPath));
		return names;
	}

	// -------------------------------------------------------------- plumbing

	/**
	 * Python {@code _reported_value}: the value a finding identifies, before display
	 * redaction.
	 * @param match the match a finding reports
	 * @return the matched text, before any {@code [REDACTED]} display masking
	 */
	static String reportedValue(Cand match) {
		return match.value();
	}

	/**
	 * Python {@code pii_evidence_sha256}.
	 * @param category the check that matched, mixed in so the same value under two checks
	 * digests differently
	 * @param value the matched value, case-folded before hashing
	 * @return the lowercase hex SHA-256 digest identifying the value for a waiver
	 */
	static String evidenceSha256(String category, String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest((category + "\u0000" + casefold(value)).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	/**
	 * Python's {@code str.casefold()}: the Unicode full folds Java's simple lowercasing
	 * does not perform, plus {@code toLowerCase}. The supplement below is the part of
	 * {@code CaseFolding.txt} (status F) that matters here — the multichar Latin folds
	 * ({@code ß}, long s, the {@code FB0x} ligatures), the Greek accented and
	 * iota-subscript forms, and the Cyrillic and Armenian letter variants. The pass runs
	 * twice, around the lowercase step: before it so a titlecase {@code ᾼ} expands the
	 * way Python expands it rather than precomposing, after it because Java's lowercase
	 * mapping applies the contextual final-sigma rule that case folding has no equivalent
	 * of.
	 *
	 * <p>
	 * Not covered: Cherokee, where Java folds <em>farther</em> than Python does (into a
	 * different code-point block). See the class javadoc bullet.
	 * @param value the string to fold
	 * @return the case-folded string, the key values are grouped and digested under
	 */
	static String casefold(String value) {
		return applyFullFolds(applyFullFolds(value).toLowerCase(Locale.ROOT));
	}

	/**
	 * Replace each character {@link #fullFold} recognizes with its full-fold value.
	 * @param value the string to fold one pass over
	 * @return the folded string, or {@code value} itself when no character needed folding
	 */
	private static String applyFullFolds(String value) {
		StringBuilder folded = null;
		for (int i = 0; i < value.length(); i++) {
			String full = fullFold(value.charAt(i));
			if (full == null) {
				if (folded != null) {
					folded.append(value.charAt(i));
				}
				continue;
			}
			if (folded == null) {
				folded = new StringBuilder(value.length() + 4).append(value, 0, i);
			}
			folded.append(full);
		}
		return (folded != null) ? folded.toString() : value;
	}

	/**
	 * The full fold of a character, or null when Java's lowercase mapping already matches
	 * Python's {@code casefold()}. Values are the full-fold mappings from Unicode's
	 * {@code CaseFolding.txt} (status F); each one is stable under {@code toLowerCase}.
	 * @param c the character to fold
	 * @return its multichar or divergent fold, or {@code null} when plain lowercasing
	 * already agrees with Python
	 */
	private static String fullFold(char c) {
		return switch (c) {
			case '\u00B5' -> "\u03BC";
			case '\u00DF' -> "ss";
			case '\u0149' -> "\u02BCn";
			case '\u017F' -> "s";
			case '\u01F0' -> "j\u030C";
			case '\u0345' -> "\u03B9";
			case '\u0390' -> "\u03B9\u0308\u0301";
			case '\u03B0' -> "\u03C5\u0308\u0301";
			case '\u03C2' -> "\u03C3";
			case '\u03D0' -> "\u03B2";
			case '\u03D1' -> "\u03B8";
			case '\u03D5' -> "\u03C6";
			case '\u03D6' -> "\u03C0";
			case '\u03F0' -> "\u03BA";
			case '\u03F1' -> "\u03C1";
			case '\u03F5' -> "\u03B5";
			case '\u0587' -> "\u0565\u0582";
			case '\u1C80' -> "\u0432";
			case '\u1C81' -> "\u0434";
			case '\u1C82' -> "\u043E";
			case '\u1C83' -> "\u0441";
			case '\u1C84' -> "\u0442";
			case '\u1C85' -> "\u0442";
			case '\u1C86' -> "\u044A";
			case '\u1C87' -> "\u0463";
			case '\u1C88' -> "\uA64B";
			case '\u1E96' -> "h\u0331";
			case '\u1E97' -> "t\u0308";
			case '\u1E98' -> "w\u030A";
			case '\u1E99' -> "y\u030A";
			case '\u1E9A' -> "a\u02BE";
			case '\u1E9B' -> "\u1E61";
			case '\u1E9E' -> "ss";
			case '\u1F50' -> "\u03C5\u0313";
			case '\u1F52' -> "\u03C5\u0313\u0300";
			case '\u1F54' -> "\u03C5\u0313\u0301";
			case '\u1F56' -> "\u03C5\u0313\u0342";
			case '\u1F80' -> "\u1F00\u03B9";
			case '\u1F81' -> "\u1F01\u03B9";
			case '\u1F82' -> "\u1F02\u03B9";
			case '\u1F83' -> "\u1F03\u03B9";
			case '\u1F84' -> "\u1F04\u03B9";
			case '\u1F85' -> "\u1F05\u03B9";
			case '\u1F86' -> "\u1F06\u03B9";
			case '\u1F87' -> "\u1F07\u03B9";
			case '\u1F88' -> "\u1F00\u03B9";
			case '\u1F89' -> "\u1F01\u03B9";
			case '\u1F8A' -> "\u1F02\u03B9";
			case '\u1F8B' -> "\u1F03\u03B9";
			case '\u1F8C' -> "\u1F04\u03B9";
			case '\u1F8D' -> "\u1F05\u03B9";
			case '\u1F8E' -> "\u1F06\u03B9";
			case '\u1F8F' -> "\u1F07\u03B9";
			case '\u1F90' -> "\u1F20\u03B9";
			case '\u1F91' -> "\u1F21\u03B9";
			case '\u1F92' -> "\u1F22\u03B9";
			case '\u1F93' -> "\u1F23\u03B9";
			case '\u1F94' -> "\u1F24\u03B9";
			case '\u1F95' -> "\u1F25\u03B9";
			case '\u1F96' -> "\u1F26\u03B9";
			case '\u1F97' -> "\u1F27\u03B9";
			case '\u1F98' -> "\u1F20\u03B9";
			case '\u1F99' -> "\u1F21\u03B9";
			case '\u1F9A' -> "\u1F22\u03B9";
			case '\u1F9B' -> "\u1F23\u03B9";
			case '\u1F9C' -> "\u1F24\u03B9";
			case '\u1F9D' -> "\u1F25\u03B9";
			case '\u1F9E' -> "\u1F26\u03B9";
			case '\u1F9F' -> "\u1F27\u03B9";
			case '\u1FA0' -> "\u1F60\u03B9";
			case '\u1FA1' -> "\u1F61\u03B9";
			case '\u1FA2' -> "\u1F62\u03B9";
			case '\u1FA3' -> "\u1F63\u03B9";
			case '\u1FA4' -> "\u1F64\u03B9";
			case '\u1FA5' -> "\u1F65\u03B9";
			case '\u1FA6' -> "\u1F66\u03B9";
			case '\u1FA7' -> "\u1F67\u03B9";
			case '\u1FA8' -> "\u1F60\u03B9";
			case '\u1FA9' -> "\u1F61\u03B9";
			case '\u1FAA' -> "\u1F62\u03B9";
			case '\u1FAB' -> "\u1F63\u03B9";
			case '\u1FAC' -> "\u1F64\u03B9";
			case '\u1FAD' -> "\u1F65\u03B9";
			case '\u1FAE' -> "\u1F66\u03B9";
			case '\u1FAF' -> "\u1F67\u03B9";
			case '\u1FB2' -> "\u1F70\u03B9";
			case '\u1FB3' -> "\u03B1\u03B9";
			case '\u1FB4' -> "\u03AC\u03B9";
			case '\u1FB6' -> "\u03B1\u0342";
			case '\u1FB7' -> "\u03B1\u0342\u03B9";
			case '\u1FBC' -> "\u03B1\u03B9";
			case '\u1FC2' -> "\u1F74\u03B9";
			case '\u1FC3' -> "\u03B7\u03B9";
			case '\u1FC4' -> "\u03AE\u03B9";
			case '\u1FC6' -> "\u03B7\u0342";
			case '\u1FC7' -> "\u03B7\u0342\u03B9";
			case '\u1FCC' -> "\u03B7\u03B9";
			case '\u1FD2' -> "\u03B9\u0308\u0300";
			case '\u1FD3' -> "\u03B9\u0308\u0301";
			case '\u1FD6' -> "\u03B9\u0342";
			case '\u1FD7' -> "\u03B9\u0308\u0342";
			case '\u1FE2' -> "\u03C5\u0308\u0300";
			case '\u1FE3' -> "\u03C5\u0308\u0301";
			case '\u1FE4' -> "\u03C1\u0313";
			case '\u1FE6' -> "\u03C5\u0342";
			case '\u1FE7' -> "\u03C5\u0308\u0342";
			case '\u1FF2' -> "\u1F7C\u03B9";
			case '\u1FF3' -> "\u03C9\u03B9";
			case '\u1FF4' -> "\u03CE\u03B9";
			case '\u1FF6' -> "\u03C9\u0342";
			case '\u1FF7' -> "\u03C9\u0342\u03B9";
			case '\u1FFC' -> "\u03C9\u03B9";
			case '\u1FBE' -> "\u03B9";
			case '\uFB00' -> "ff";
			case '\uFB01' -> "fi";
			case '\uFB02' -> "fl";
			case '\uFB03' -> "ffi";
			case '\uFB04' -> "ffl";
			case '\uFB05' -> "st";
			case '\uFB06' -> "st";
			case '\uFB13' -> "\u0574\u0576";
			case '\uFB14' -> "\u0574\u0565";
			case '\uFB15' -> "\u0574\u056B";
			case '\uFB16' -> "\u057E\u0576";
			case '\uFB17' -> "\u0574\u056D";
			default -> null;
		};
	}

	/**
	 * All non-overlapping matches of {@code regex} in {@code text}.
	 * @param regex the pattern to search with
	 * @param text the line to search
	 * @return each match with its offsets, its text and its first capture group when the
	 * pattern has one, empty when nothing matches
	 */
	static List<Cand> allMatches(Pattern regex, String text) {
		List<Cand> out = new ArrayList<>();
		Matcher matcher = regex.matcher(text);
		while (matcher.find()) {
			out.add(new Cand(matcher.start(), matcher.end(), matcher.group(),
					(matcher.groupCount() >= 1) ? matcher.group(1) : null));
		}
		return out;
	}

	/**
	 * Python {@code pattern.search(text, pos, endpos)}: the first match starting at or
	 * after {@code pos} and ending at or before {@code endpos}. The search runs on a
	 * slice one character wider so a leading lookbehind still sees the character before
	 * {@code pos}, which is what Python does; the extra character can only start a match
	 * at {@code from - 1}, which the {@code find(skip)} bound rejects.
	 * @param pattern the pattern to search for
	 * @param text the line being searched
	 * @param from offset the match must start at or after
	 * @param to offset the match must end at or before, clamped to the text
	 * @return the first match in the window, or {@code null} when there is none
	 */
	static Cand searchWithin(Pattern pattern, String text, int from, int to) {
		int left = Math.max(0, from - 1);
		int skip = from - left;
		Matcher matcher = pattern.matcher(text.substring(left, Math.min(to, text.length())));
		if (matcher.find(skip)) {
			return new Cand(matcher.start() + left, matcher.end() + left, matcher.group(),
					(matcher.groupCount() >= 1) ? matcher.group(1) : null);
		}
		return null;
	}

	/**
	 * Python {@code pattern.finditer(text, pos, endpos)}; same one-character window rule.
	 * @param pattern the pattern to search for
	 * @param text the line being searched
	 * @param from offset matches must start at or after
	 * @param to offset matches must end at or before, clamped to the text
	 * @return every non-overlapping match in the window, empty when there are none
	 */
	static List<Cand> findAllWithin(Pattern pattern, String text, int from, int to) {
		List<Cand> out = new ArrayList<>();
		int left = Math.max(0, from - 1);
		int skip = from - left;
		Matcher matcher = pattern.matcher(text.substring(left, Math.min(to, text.length())));
		if (!matcher.find(skip)) {
			return out;
		}
		do {
			out.add(new Cand(matcher.start() + left, matcher.end() + left, matcher.group(),
					(matcher.groupCount() >= 1) ? matcher.group(1) : null));
		}
		while (matcher.find());
		return out;
	}

	/**
	 * Python {@code re.sub(re.escape(value), spaces, line, count=1, flags=IGNORECASE)}.
	 * @param line the line to scan
	 * @param value the literal to blank, matched case-insensitively
	 * @return the line with the first occurrence replaced by spaces of the same length,
	 * so later offsets hold; unchanged when the value is absent
	 */
	static String blankFirstOccurrence(String line, String value) {
		Matcher matcher = Pattern.compile(Pattern.quote(value), PATTERN_FLAGS).matcher(line);
		if (!matcher.find()) {
			return line;
		}
		return line.substring(0, matcher.start()) + " ".repeat(matcher.end() - matcher.start())
				+ line.substring(matcher.end());
	}

	/**
	 * Python {@code any(exc in line for exc in exceptions)}.
	 * @param line the matched line
	 * @param exceptions substrings that exclude this line from the check
	 * @return {@code true} when the line contains any exception
	 */
	private static boolean containsAny(String line, List<String> exceptions) {
		for (String exception : exceptions) {
			if (line.contains(exception)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Python {@code max(confidences, key=rank.get(c, 1))}: the first strongest wins.
	 * @param confidences the values across a value's occurrences, unknown labels ranked
	 * as {@code medium}
	 * @return the strongest of them, or {@code "low"} for an empty list
	 */
	static String strongestConfidence(List<String> confidences) {
		String best = "low";
		int bestRank = -1;
		for (String confidence : confidences) {
			int rank = switch ((confidence != null) ? confidence : "") {
				case "low" -> 0;
				case "high" -> 2;
				default -> 1;
			};
			if (rank > bestRank) {
				bestRank = rank;
				best = confidence;
			}
		}
		return best;
	}

	private static boolean isErrorSeverity(String severity) {
		String upper = (severity != null) ? severity.toUpperCase(Locale.ROOT) : "";
		return upper.equals("CRITICAL") || upper.equals("HIGH");
	}

	/**
	 * Python {@code str(file_path.relative_to(root))}, falling back to the basename.
	 * @param file the scanned file
	 * @param root the scanned root it is reported against
	 * @return the path relative to the root, or just the file's name when the two share
	 * no root
	 */
	static String relativize(Path file, Path root) {
		try {
			return root.relativize(file).toString();
		}
		catch (IllegalArgumentException ex) {
			return file.getFileName().toString();
		}
	}

	/**
	 * Python {@code content.split("\n")} — trailing empty field kept, {@code \r}
	 * preserved.
	 * @param content the file text
	 * @return the lines, a trailing newline producing a final empty entry
	 */
	static List<String> splitOnNewline(String content) {
		return List.of(content.split("\n", -1));
	}

	/**
	 * Python {@code read_text(encoding="utf-8-sig", errors="ignore")}. The
	 * {@code errors="ignore"} half is the lenient decoder below; the text-mode half is
	 * the newline translation, because {@code Path.read_text} opens in universal newline
	 * mode and hands Python a string with no carriage returns at all. Without it a CRLF
	 * file's {@code line_content} would end in {@code \r}.
	 * @param path the file to read
	 * @return its text without a byte-order mark and with newlines unified, or
	 * {@code null} when it cannot be read
	 */
	static String readUtf8Lenient(Path path) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			int offset = 0;
			if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
					&& (bytes[2] & 0xFF) == 0xBF) {
				offset = 3; // utf-8-sig strips a byte-order mark
			}
			CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.IGNORE)
				.onUnmappableCharacter(CodingErrorAction.IGNORE);
			String text = decoder.decode(java.nio.ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
			return text.replace("\r\n", "\n").replace('\r', '\n');
		}
		catch (IOException ex) {
			return null;
		}
	}

	private static boolean isAscii(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) > 0x7F) {
				return false;
			}
		}
		return true;
	}

	private static boolean truthy(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof Number number) {
			return number.doubleValue() != 0.0;
		}
		if (value instanceof Collection<?> collection) {
			return !collection.isEmpty();
		}
		if (value instanceof Map<?, ?> map) {
			return !map.isEmpty();
		}
		return !String.valueOf(value).isEmpty();
	}

	private static String stringOr(Object value, String fallback) {
		return (value != null) ? String.valueOf(value) : fallback;
	}

	private static String optionalString(Object value) {
		return (value != null) ? String.valueOf(value) : null;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asStringMap(Map<?, ?> map) {
		Map<String, Object> out = new LinkedHashMap<>();
		map.forEach((key, value) -> out.put(String.valueOf(key), value));
		return out;
	}

	// ---------------------------------------------------------------- records

	/**
	 * One compiled config entry: Python's
	 * {@code (category, regex, exceptions, pattern_def)}.
	 *
	 * @param category the config key the pattern is listed under, also the reported
	 * category
	 * @param regex the compiled pattern
	 * @param exceptions substrings that exclude a line from this check
	 * @param definition the raw config entry, source of severity, description and
	 * suggestion
	 * @author Ivan Rodriguez
	 */
	record CompiledPattern(String category, Pattern regex, List<String> exceptions, Map<String, Object> definition) {
	}

	/**
	 * A candidate match with the offsets and text the guards need.
	 *
	 * @param start offset where the match begins in the scanned text
	 * @param end offset just past the match
	 * @param value the matched text
	 * @param group the first capturing group, or {@code null} when the pattern has none
	 * or it did not participate
	 * @author Ivan Rodriguez
	 */
	record Cand(int start, int end, String value, String group) {
	}

	/**
	 * Python's per-line {@code finding_data} dict.
	 *
	 * @param file the scanned file's name
	 * @param line 1-based line number of the match
	 * @param lineContent the whole source line the match came from
	 * @param severity the config entry's severity, {@code medium} when it has none
	 * @param description the config entry's description, or the ownership-contact wording
	 * @param suggestion remediation hint from the config entry, {@code null} when it has
	 * none
	 * @param category the check to report, {@code ownership_emails} for a declared
	 * contact
	 * @param confidence {@code low}, {@code medium} or {@code high}
	 * @param matchedValue the matched text, which drives grouping and the evidence digest
	 * @author Ivan Rodriguez
	 */
	record Candidate(String file, int line, String lineContent, String severity, String description, String suggestion,
			String category, String confidence, String matchedValue) {
	}

	/**
	 * One {@code (file, line)} place a value was seen.
	 *
	 * @param file path relative to the scanned root
	 * @param line 1-based line number it was seen on
	 * @author Ivan Rodriguez
	 */
	record Occurrence(String file, int line) {
	}

	/**
	 * Python's grouping value: first finding, its relative file, occurrence list,
	 * confidences.
	 */
	static final class Group {

		private final Candidate first;

		private final String firstFile;

		private final List<Occurrence> occurrences = new ArrayList<>();

		private final List<String> confidences = new ArrayList<>();

		Group(Candidate first, String firstFile) {
			this.first = first;
			this.firstFile = firstFile;
		}

		void add(String file, int line, String confidence) {
			this.occurrences.add(new Occurrence(file, line));
			this.confidences.add(confidence);
		}

		Candidate first() {
			return this.first;
		}

		String firstFile() {
			return this.firstFile;
		}

		List<Occurrence> occurrences() {
			return this.occurrences;
		}

		List<String> confidences() {
			return this.confidences;
		}

	}

	/**
	 * GeoJSON brace/array frame from Python's {@code _geojson_coordinate_lines} stack.
	 */
	private static final class Frame {

		final String kind;

		final Frame owner;

		final int start;

		final List<int[]> spans = new ArrayList<>();

		boolean geojson;

		Frame(String kind, boolean geojson, Frame owner, int start) {
			this.kind = kind;
			this.geojson = geojson;
			this.owner = owner;
			this.start = start;
		}

	}

	/**
	 * Loads {@code pii_patterns.yaml} from the classpath with snakeyaml SafeConstructor.
	 */
	private static final class PiiConfig {

		private PiiConfig() {
		}

		@SuppressWarnings("unchecked")
		static Map<String, Object> load() {
			ClassLoader loader = PiiScanner.class.getClassLoader();
			InputStream in = (loader != null) ? loader.getResourceAsStream(PATTERNS_RESOURCE) : null;
			if (in == null) {
				in = ClassLoader.getSystemResourceAsStream(PATTERNS_RESOURCE);
			}
			if (in == null) {
				throw new IllegalStateException("Missing classpath resource " + PATTERNS_RESOURCE);
			}
			try (InputStream stream = in) {
				Object data = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
				if (!(data instanceof Map)) {
					throw new IllegalStateException("pii_patterns.yaml must contain a mapping");
				}
				return (Map<String, Object>) data;
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

}
