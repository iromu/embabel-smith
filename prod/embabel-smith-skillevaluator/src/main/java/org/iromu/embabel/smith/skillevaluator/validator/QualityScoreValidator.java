/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;
import org.iromu.embabel.smith.skillevaluator.util.Frontmatter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 4-dimension skill quality analysis with a 0-100 composite score and A-F grades.
 * <p>
 * Port of the Python {@code validators/quality_score.py}: Correctness (35%),
 * Discoverability (25%), Reliability (25%) and Efficiency (15%), scored by deterministic
 * deductions and recorded as {@code QUALITY} findings plus the {@code quality_scores}
 * metadata digest.
 *
 * @author Ivan Rodriguez
 */
public class QualityScoreValidator extends ValidatorBase {

	/** Python {@code QUALITY_SCORE_WEIGHTS}. */
	static final double WEIGHT_CORRECTNESS = 0.35;
	static final double WEIGHT_DISCOVERABILITY = 0.25;
	static final double WEIGHT_RELIABILITY = 0.25;
	static final double WEIGHT_EFFICIENCY = 0.15;

	private static final List<GradeThreshold> GRADE_THRESHOLDS = List.of(new GradeThreshold(90, "A"),
			new GradeThreshold(80, "B"), new GradeThreshold(70, "C"), new GradeThreshold(60, "D"),
			new GradeThreshold(0, "F"));

	private static final String RESOURCE_PATH = "org/iromu/embabel/smith/skillevaluator/config/quality-default.yaml";

	// --- Python-text primitives -------------------------------------------------

	/**
	 * Python's Unicode {@code \s} for str patterns (and {@code str.isspace()}). Java's
	 * {@code \s} omits U+0085 and U+00A0, so the class is spelled out.
	 */
	private static final String WS_CHARS = "\\t\\n\\x0B\\f\\r\\x1C-\\x1F\\x20\\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000";

	private static final String WS_CLASS = "[" + WS_CHARS + "]";

	private static final String NOT_WS_CLASS = "[^" + WS_CHARS + "]";

	/**
	 * {@code $} in a non-MULTILINE Python pattern also matches before a trailing newline.
	 */
	private static final String PY_DOLLAR = "(?=\\n?\\z)";

	/**
	 * Python's {@code str.isspace()} for a single code point.
	 * @param codePoint the character to test
	 * @return {@code true} when it is one of the whitespace code points Python accepts
	 */
	static boolean pyIsSpace(int codePoint) {
		if (codePoint >= 0x1C && codePoint <= 0x1F) {
			return true;
		}
		if (codePoint >= 0x2000 && codePoint <= 0x200A) {
			return true;
		}
		return switch (codePoint) {
			case 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x85, 0xA0, 0x1680, 0x2028, 0x2029, 0x202F,
					0x205F, 0x3000 ->
				true;
			default -> false;
		};
	}

	/**
	 * Python's {@code str.strip()} over Unicode whitespace.
	 * @param value the string to trim
	 * @return the string without its leading and trailing whitespace
	 */
	static String pyStrip(String value) {
		int start = 0;
		int end = value.length();
		while (start < end) {
			int cp = value.codePointAt(start);
			if (!pyIsSpace(cp)) {
				break;
			}
			start += Character.charCount(cp);
		}
		while (end > start) {
			int cp = value.codePointBefore(end);
			if (!pyIsSpace(cp)) {
				break;
			}
			end -= Character.charCount(cp);
		}
		return value.substring(start, end);
	}

	/**
	 * Python's {@code str.split()} with no separator: runs of Unicode whitespace, no
	 * empty tokens.
	 * @param value the string to split
	 * @return the runs of non-whitespace, in order, never empty
	 */
	static List<String> pySplitWords(String value) {
		List<String> words = new ArrayList<>();
		int i = 0;
		int n = value.length();
		while (i < n) {
			int cp = value.codePointAt(i);
			if (pyIsSpace(cp)) {
				i += Character.charCount(cp);
				continue;
			}
			int start = i;
			while (i < n) {
				int inner = value.codePointAt(i);
				if (pyIsSpace(inner)) {
					break;
				}
				i += Character.charCount(inner);
			}
			words.add(value.substring(start, i));
		}
		return words;
	}

	/**
	 * Python's {@code str.split(sep, maxsplit)}: literal separator, never drops empty
	 * parts.
	 * @param text the string to split
	 * @param separator literal separator to split on, never a regex
	 * @param maxParts maximum number of parts produced, so Python's {@code maxsplit + 1}
	 * @return the parts, empty ones included for adjacent separators
	 */
	private static String[] literalSplit(String text, String separator, int maxParts) {
		List<String> parts = new ArrayList<>();
		int from = 0;
		while (parts.size() < maxParts - 1) {
			int index = text.indexOf(separator, from);
			if (index < 0) {
				break;
			}
			parts.add(text.substring(from, index));
			from = index + separator.length();
		}
		parts.add(text.substring(from));
		return parts.toArray(new String[0]);
	}

	/**
	 * Python's {@code round(float, ndigits)}: correctly-rounded half-to-even on the exact
	 * binary value of the double (Java's {@code Math.round} and {@code %.1f} are
	 * half-up).
	 * @param value the number to round
	 * @param digits decimal places to keep
	 * @return the half-to-even rounded value; NaN and infinities unchanged
	 */
	static double pyRound(double value, int digits) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return value;
		}
		return new BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
	}

	/**
	 * Python's {@code f"{value:.1f}"}: same half-to-even rounding, fixed one-decimal
	 * text.
	 * @param value the number to render
	 * @return one decimal place always, and Python's {@code nan}/{@code inf} spellings
	 * for non-finite values
	 */
	static String pyFormat1(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return String.valueOf(value);
		}
		return new BigDecimal(value).setScale(1, RoundingMode.HALF_EVEN).toPlainString();
	}

	/**
	 * Python's {@code str(float)}: the shortest decimal that round-trips, written in
	 * {@code e} notation only when the decimal exponent falls outside {@code [-4, 16)}.
	 * {@code Double
	 * .toString} produces the same digits but switches to {@code E} notation on its own
	 * bounds (1e-3 and 1e7) and always keeps a digit after the point, so the digits are
	 * re-placed here.
	 * @param value the number to render
	 * @return the digits in Python's notation, lower-case {@code e} exponent only outside
	 * {@code [-4, 16)}
	 */
	static String pyFloatStr(double value) {
		if (Double.isNaN(value)) {
			return "nan";
		}
		if (Double.isInfinite(value)) {
			return (value > 0) ? "inf" : "-inf";
		}
		if (value == 0.0) {
			return (Double.doubleToRawLongBits(value) < 0) ? "-0.0" : "0.0";
		}
		String sign = (value < 0) ? "-" : "";
		String text = String.valueOf(Math.abs(value));
		int mark = text.indexOf('E');
		String mantissa = (mark < 0) ? text : text.substring(0, mark);
		int exponent = (mark < 0) ? 0 : Integer.parseInt(text.substring(mark + 1));
		int dot = mantissa.indexOf('.');
		int beforeDot = (dot < 0) ? mantissa.length() : dot;
		String digits = (dot < 0) ? mantissa : mantissa.substring(0, dot) + mantissa.substring(dot + 1);
		int leading = 0;
		while (leading < digits.length() - 1 && digits.charAt(leading) == '0') {
			leading++;
		}
		// Python's exponent: value == 0.d1d2... written as d1.d2... x 10^power.
		int power = exponent + beforeDot - leading - 1;
		digits = digits.substring(leading).replaceFirst("0+$", "");
		if (power < -4 || power >= 16) {
			String head = digits.substring(0, 1);
			String tail = (digits.length() > 1) ? "." + digits.substring(1) : "";
			return sign + head + tail + "e" + ((power >= 0) ? "+" : "-")
					+ String.format(Locale.ROOT, "%02d", Math.abs(power));
		}
		if (power < 0) {
			return sign + "0." + "0".repeat(-power - 1) + digits;
		}
		int whole = power + 1;
		if (digits.length() > whole) {
			return sign + digits.substring(0, whole) + "." + digits.substring(whole);
		}
		return sign + digits + "0".repeat(whole - digits.length()) + ".0";
	}

	/**
	 * Python's {@code f"{value:.1f}"} for a value already narrowed to one decimal.
	 * @param value the number to render
	 * @return the value rounded and formatted at one decimal place
	 */
	private static String oneDecimal(double value) {
		return pyFormat1(pyRound(value, 1));
	}

	/**
	 * Python's {@code len(str)} for a message or metadata value.
	 * @param value the value to spell as Python would
	 * @return {@code None}, {@code True}/{@code False}, a round-tripped float, or the
	 * string itself
	 */
	private static String pyStr(Object value) {
		if (value == null) {
			return "None";
		}
		if (value instanceof String s) {
			return s;
		}
		if (value instanceof Boolean b) {
			return b ? "True" : "False";
		}
		if (value instanceof Double d) {
			return pyFloatStr(d);
		}
		return String.valueOf(value);
	}

	private static int pyLen(String value) {
		return value.codePointCount(0, value.length());
	}

	// --- argument-hint masking (Python module-level helpers) --------------------

	private static final String CONTROL_TAG_BODY = "(?:system|developer|assistant|user|tool|context|instructions?|prompt|override|rules|policy)"
			+ "(?:[_-]?(?:prompt|message|call|use|result|instruction)s?)?|script";

	private static final Pattern ARGUMENT_CONTROL_TAG = Pattern
		.compile("<(?:" + CONTROL_TAG_BODY + ")(?=[" + WS_CHARS + "<>:])", Pattern.CASE_INSENSITIVE);

	private static final Pattern ARGUMENT_NESTED_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

	private static final Pattern ARGUMENT_PLACEHOLDER = Pattern
		.compile("<(?![^<>]*/" + WS_CLASS + "*>)([A-Za-z][^<>=\\r\\n]*)>");

	private static final Pattern ARGUMENT_COMMAND_SYNTAX = Pattern.compile("[;&`]|\\$[({]");

	private static final Pattern ARGUMENT_CHOICE_LABEL = Pattern.compile(
			"^(?:[^|:]+:|" + NOT_WS_CLASS + "+" + WS_CLASS + "+(?:e\\.g\\.|i\\.e\\.),?)" + WS_CLASS + "+",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern ARGUMENT_CHOICE = Pattern.compile(NOT_WS_CLASS + "+|\"[^\"]*\"|'[^']*'");

	private static final Pattern ARGUMENT_ARROW_OR_COMPARISON = Pattern
		.compile(
				"(?<!" + NOT_WS_CLASS + ")(?:->|=>|>=)(?!" + NOT_WS_CLASS + ")|(?<![^" + WS_CHARS
						+ "(\\[])>=? ?\\d+(?:\\.\\d+)?" + "(?![^" + WS_CHARS + ")\\],])",
				Pattern.UNICODE_CHARACTER_CLASS);

	private static final int ARGUMENT_PLACEHOLDER_MAX_WORDS = 4;

	/**
	 * Python {@code _is_argument_placeholder}.
	 * @param body the text between a pair of angle brackets
	 * @return {@code true} when it reads as a short CLI placeholder rather than an XML
	 * tag
	 */
	static boolean isArgumentPlaceholder(String body) {
		int lastCodePoint = body.isEmpty() ? -1 : body.codePointBefore(body.length());
		if (lastCodePoint < 0 || pyIsSpace(lastCodePoint) || ARGUMENT_COMMAND_SYNTAX.matcher(body).find()) {
			return false;
		}
		List<String> words = new ArrayList<>();
		for (String token : pySplitWords(body)) {
			for (int i = 0; i < token.length(); i++) {
				char c = token.charAt(i);
				if (Character.isLetterOrDigit(c)) {
					words.add(token);
					break;
				}
			}
		}
		if (words.size() > ARGUMENT_PLACEHOLDER_MAX_WORDS) {
			return false;
		}
		if (!body.contains("|")) {
			return true;
		}
		String[] choices = body.split("\\|", -1);
		Matcher label = ARGUMENT_CHOICE_LABEL.matcher(pyStrip(choices[0]));
		if (label.find()) {
			choices[0] = new StringBuilder(choices[0]).replace(label.start(), label.end(), "").toString();
		}
		for (String choice : choices) {
			if (!ARGUMENT_CHOICE.matcher(pyStrip(choice)).matches()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Python {@code _masking_hides_text}: whether masking left a {@code >} that could
	 * hide text inside a group. Walks the masked string tracking open groups, masked
	 * placeholders and arrow/comparison operators.
	 * @param masked the hint after placeholder masking
	 * @return {@code true} when a {@code >} survives that could conceal text inside a
	 * group
	 */
	static boolean maskingHidesText(String masked) {
		Set<Integer> operators = new HashSet<>();
		Matcher arrows = ARGUMENT_ARROW_OR_COMPARISON.matcher(masked);
		while (arrows.find()) {
			operators.add(arrows.start() + arrows.group().indexOf('>'));
		}
		List<Integer> opened = new ArrayList<>();
		boolean afterMask = false;
		int index = 0;
		while (index < masked.length()) {
			char ch = masked.charAt(index);
			if (afterMask && ch != '>') {
				return true;
			}
			afterMask = false;
			if (ch == '<') {
				opened.add(index);
			}
			else if (ch == '>') {
				if (opened.isEmpty()) {
					if (!operators.contains(index)) {
						return true;
					}
				}
				else {
					opened.remove(opened.size() - 1);
				}
			}
			else if (!opened.isEmpty() && masked.startsWith("[]", index)) {
				Matcher prefix = ARGUMENT_NESTED_PREFIX.matcher(masked);
				prefix.region(opened.get(opened.size() - 1) + 1, index);
				if (!prefix.matches()) {
					return true;
				}
				afterMask = true;
				index += 1;
			}
			index += 1;
		}
		return afterMask;
	}

	/**
	 * Python {@code _mask_argument_hint}: mask short CLI placeholders for the XML tag
	 * check.
	 * @param hint the {@code argument-hint} value to mask
	 * @return the hint with short placeholders collapsed to {@code []}, unchanged when it
	 * holds a control tag or masking would hide text
	 */
	static String maskArgumentHint(String hint) {
		if (ARGUMENT_CONTROL_TAG.matcher(hint).find()) {
			return hint;
		}
		Matcher matcher = ARGUMENT_PLACEHOLDER.matcher(hint);
		StringBuilder masked = new StringBuilder();
		int last = 0;
		while (matcher.find()) {
			masked.append(hint, last, matcher.start());
			masked.append(isArgumentPlaceholder(matcher.group(1)) ? "[]" : matcher.group());
			last = matcher.end();
		}
		masked.append(hint, last, hint.length());
		if (maskingHidesText(masked.toString())) {
			return hint;
		}
		return masked.toString();
	}

	// --- term matching ---------------------------------------------------------

	private static final Pattern WORD_EDGE_BEFORE = Pattern.compile("(?<![A-Za-z0-9_])");

	/**
	 * Python {@code _contains_term}: whole word or phrase, never inside another word.
	 * @param text the text to search
	 * @param term the word or phrase to find, stripped and matched case-insensitively
	 * @return {@code true} when the term occurs at a word boundary
	 */
	static boolean containsTerm(String text, String term) {
		String normalized = pyStrip(term);
		String regex = "(?<![A-Za-z0-9_])" + Pattern.quote(normalized) + "(?![A-Za-z0-9_])";
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
	}

	static boolean containsAnyTerm(String text, List<String> terms) {
		for (String term : terms) {
			if (containsTerm(text, term)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Python {@code _contains_any_word}: whitespace inside a term matches any whitespace
	 * run.
	 * @param text the text to search
	 * @param words terms to look for, matched case-insensitively at word boundaries
	 * @return {@code true} when at least one of them occurs
	 */
	static boolean containsAnyWord(String text, List<String> words) {
		List<String> alternatives = new ArrayList<>();
		for (String word : words) {
			List<String> parts = new ArrayList<>();
			for (String piece : pySplitWords(word)) {
				parts.add(Pattern.quote(piece));
			}
			alternatives.add(String.join(WS_CLASS + "+", parts));
		}
		String regex = "(?<!\\w)(?:" + String.join("|", alternatives) + ")(?!\\w)";
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS)
			.matcher(text)
			.find();
	}

	private static final List<Pattern> API_PATTERNS = List.of(
			Pattern.compile("\\bimport\\s+[A-Za-z_][A-Za-z0-9_.]*",
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
			Pattern.compile("\\bfrom\\s+[A-Za-z_][A-Za-z0-9_.]*\\s+import\\b",
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
			Pattern.compile("\\bapi\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
			Pattern.compile("\\bmodules?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
			Pattern.compile("\\blibrar(?:y|ies)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
			Pattern.compile("\\bpackages?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
			Pattern.compile("\\bclasses?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
			Pattern.compile("\\bfunctions?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));

	/**
	 * Python {@code _has_api_documentation}.
	 * @param content the SKILL.md body
	 * @return {@code true} when it mentions imports, APIs, modules, libraries, packages,
	 * classes or functions
	 */
	static boolean hasApiDocumentation(String content) {
		for (Pattern pattern : API_PATTERNS) {
			if (pattern.matcher(content).find()) {
				return true;
			}
		}
		return false;
	}

	/** Python {@code XML_TAG_RE} from {@code models/skill.py}. */
	private static final Pattern XML_TAG_RE = Pattern.compile("</?[A-Za-z][A-Za-z0-9_-]*(?:" + WS_CLASS
			+ "[^<>]*)?/?>|</?[A-Za-z][A-Za-z0-9_-]*" + WS_CLASS + "[^<>]*" + PY_DOLLAR);

	private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
			"^\\ufeff?---[^\\S\\r\\n]*\\r?\\n(.*?)\\r?\\n---[^\\S\\r\\n]*(?=\\r?\\n|\\z)(?:\\r?\\n)?(.*)",
			Pattern.DOTALL);

	// --- legacy local-path classification ---------------------------------------

	private static final Pattern URL_EDGE = Pattern.compile("[\\x00-\\x20]+");

	/**
	 * Python {@code _normalized_quality_local_path}: Quality Score's legacy
	 * classification order, intentionally looser than Hygiene's stricter path policy
	 * (issue #108).
	 * @param href the link target as written in SKILL.md
	 * @return the normalized relative path, or {@code null} when it is not a plain local
	 * path
	 */
	static String normalizedQualityLocalPath(String href) {
		String trimmed = URL_EDGE.matcher(href).replaceAll("");
		int schemeCut = schemeEnd(trimmed);
		if (schemeCut >= 0) {
			return null;
		}
		if (trimmed.startsWith("//")) {
			return null;
		}
		String rawPath = pathPart(trimmed);
		if (rawPath.startsWith("/")) {
			return null;
		}
		String path = percentDecode(rawPath).replace('\\', '/');
		if (schemeEnd(path) >= 0) {
			return null;
		}
		if (path.endsWith("/") || path.endsWith("/.") || path.endsWith("/..")) {
			return null;
		}
		return posixNormpath(path);
	}

	/**
	 * Whether CPython's {@code urlsplit} would read a scheme here: the first character
	 * outside the scheme character set must be a {@code :} at index 2 or later, and the
	 * URL must start with a letter (so {@code C:/x} stays a path, not a scheme).
	 * @param url the text to inspect
	 * @return the index just past the scheme, {@code -1} when it reads as a path
	 */
	private static int schemeEnd(String url) {
		int i = 0;
		while (i < url.length() && isSchemeChar(url.charAt(i))) {
			i++;
		}
		if (i == 0 || i >= url.length() || url.charAt(i) != ':' || i < 2) {
			return -1;
		}
		char first = url.charAt(0);
		boolean letter = (first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z');
		return letter ? i + 1 : -1;
	}

	/**
	 * CPython's {@code urlsplit._scheme_chars}.
	 * @param c the character to test
	 * @return {@code true} when it may appear in a URL scheme
	 */
	private static boolean isSchemeChar(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '+' || c == '-'
				|| c == '.';
	}

	/**
	 * URL path component: everything before the first {@code ?} or {@code #}.
	 * @param url the URL to cut
	 * @return the part with no query or fragment on it
	 */
	private static String pathPart(String url) {
		int cut = url.length();
		int query = url.indexOf('?');
		int hash = url.indexOf('#');
		if (query >= 0) {
			cut = Math.min(cut, query);
		}
		if (hash >= 0) {
			cut = Math.min(cut, hash);
		}
		return url.substring(0, cut);
	}

	/**
	 * Python {@code urllib.parse.unquote}: decode percent-escaped bytes per run as UTF-8,
	 * replacing malformed sequences (CPython's {@code errors="replace"}), and leave every
	 * other character untouched.
	 * @param value the possibly percent-escaped text
	 * @return the decoded text, malformed escapes replaced as CPython's {@code replace}
	 * error handler does
	 */
	private static String percentDecode(String value) {
		StringBuilder out = new StringBuilder();
		java.io.ByteArrayOutputStream pending = new java.io.ByteArrayOutputStream();
		int i = 0;
		while (i < value.length()) {
			char c = value.charAt(i);
			if (c == '%' && i + 2 < value.length() && isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2))) {
				pending.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));
				i += 3;
				continue;
			}
			flushPercentRun(pending, out);
			out.append(c);
			i++;
		}
		flushPercentRun(pending, out);
		return out.toString();
	}

	private static void flushPercentRun(java.io.ByteArrayOutputStream pending, StringBuilder out) {
		if (pending.size() == 0) {
			return;
		}
		out.append(new String(pending.toByteArray(), StandardCharsets.UTF_8));
		pending.reset();
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	/**
	 * POSIX {@code normpath} for a relative path.
	 * @param path the path to collapse, {@code .} and {@code ..} segments resolved
	 * @return the collapsed path, keeping a trailing slash and never escaping upwards
	 */
	private static String posixNormpath(String path) {
		if (path.isEmpty()) {
			return ".";
		}
		boolean absolute = path.startsWith("/");
		boolean trailingSlash = path.endsWith("/");
		List<String> out = new ArrayList<>();
		for (String part : path.split("/", -1)) {
			if (part.isEmpty() || part.equals(".")) {
				continue;
			}
			if (part.equals("..")) {
				if (!out.isEmpty() && !out.get(out.size() - 1).equals("..")) {
					out.remove(out.size() - 1);
				}
				else if (!absolute) {
					out.add("..");
				}
				continue;
			}
			out.add(part);
		}
		String joined = String.join("/", out);
		if (joined.isEmpty()) {
			return absolute ? "/" : (trailingSlash ? "./" : ".");
		}
		return (absolute ? "/" : "") + joined + (trailingSlash ? "/" : "");
	}

	/**
	 * Python {@code _has_nested_markdown_reference}.
	 * @param content a reference document's body
	 * @return {@code true} when it links to a local {@code .md} file other than its own
	 * {@code ../skill.md}
	 */
	static boolean hasNestedMarkdownReference(String content) {
		for (String target : HygieneValidator.MarkdownLinks.linkTargets(content, false)) {
			String path = normalizedQualityLocalPath(target);
			if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(".md")) {
				continue;
			}
			if (path.toLowerCase(Locale.ROOT).equals("../skill.md")) {
				continue;
			}
			return true;
		}
		return false;
	}

	/**
	 * Python {@code score_to_grade}.
	 * @param score the composite 0-100 score
	 * @return the letter of the highest threshold it reaches, {@code F} below 60
	 */
	static String scoreToGrade(double score) {
		for (GradeThreshold entry : GRADE_THRESHOLDS) {
			if (score >= entry.threshold()) {
				return entry.grade();
			}
		}
		return "F";
	}

	/**
	 * Python's int-vs-float JSON shape: integral values serialize without a decimal
	 * point.
	 * @param value the score or deduction to emit
	 * @return an {@code int} when the value is whole, otherwise the original double
	 */
	private static Object integral(double value) {
		if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < Integer.MAX_VALUE) {
			return (int) value;
		}
		return value;
	}

	private final int minScore;

	private final QualityConfig qualityConfig;

	private final Map<String, Object> qualityConfigProvenance;

	public QualityScoreValidator() {
		this(Constants.QUALITY_DEFAULT_MIN_SCORE);
	}

	public QualityScoreValidator(int minScore) {
		this.minScore = minScore;
		this.qualityConfig = loadQualityConfig();
		this.qualityConfigProvenance = packagedProvenance(this.qualityConfig);
	}

	@Override
	public String getName() {
		return "Quality Score (4-Dimension Analysis)";
	}

	@Override
	public String getDescription() {
		return "Skill quality scoring across Correctness (35%), Discoverability (25%),"
				+ " Reliability (25%), and Efficiency (15%)";
	}

	/**
	 * Loads the packaged config through the classloader with snakeyaml SafeConstructor.
	 * @return the parsed expected headers and error-handling rules
	 */
	@SuppressWarnings("unchecked")
	private static QualityConfig loadQualityConfig() {
		ClassLoader loader = QualityScoreValidator.class.getClassLoader();
		try (InputStream in = (loader != null) ? loader.getResourceAsStream(RESOURCE_PATH) : null) {
			if (in == null) {
				throw new IllegalStateException("Missing packaged quality config: " + RESOURCE_PATH);
			}
			Object raw = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
			if (!(raw instanceof Map)) {
				throw new IllegalStateException("Quality config must be a mapping");
			}
			Map<String, Object> root = (Map<String, Object>) raw;

			List<ExpectedHeader> headers = new ArrayList<>();
			Object rawHeaders = root.get("expected_headers");
			if (rawHeaders instanceof List<?> listed) {
				for (Object item : listed) {
					if (!(item instanceof Map)) {
						continue;
					}
					Map<String, Object> rule = (Map<String, Object>) item;
					List<String> alternatives = new ArrayList<>();
					if (rule.get("alternatives") instanceof List<?> alts) {
						for (Object alt : alts) {
							alternatives.add(String.valueOf(alt));
						}
					}
					headers.add(new ExpectedHeader(String.valueOf(rule.get("heading")), List.copyOf(alternatives),
							asNumber(rule.get("points")), String.valueOf(rule.get("message")),
							String.valueOf(rule.get("suggestion"))));
				}
			}

			Map<String, Object> errorHandling = (Map<String, Object>) root.getOrDefault("error_handling", Map.of());
			Map<String, ErrorRule> parsed = new LinkedHashMap<>();
			for (String name : List.of("mentioned", "scripts", "lib")) {
				Map<String, Object> block = (Map<String, Object>) errorHandling.getOrDefault(name, Map.of());
				String listKey = "mentioned".equals(name) ? "terms" : "markers";
				List<String> entries = new ArrayList<>();
				if (block.get(listKey) instanceof List<?> listed) {
					for (Object entry : listed) {
						entries.add(String.valueOf(entry));
					}
				}
				parsed.put(name, new ErrorRule(asNumber(block.get("points")), String.valueOf(block.get("message")),
						String.valueOf(block.get("suggestion")), listKey, List.copyOf(entries)));
			}
			return new QualityConfig(List.copyOf(headers), Map.copyOf(parsed));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot read packaged quality config", ex);
		}
	}

	private static Number asNumber(Object value) {
		if (value instanceof Number number) {
			return number;
		}
		return 0;
	}

	/**
	 * Python {@code load_quality_config_with_provenance} for the packaged file.
	 * @param config the config that was loaded from the classpath
	 * @return the provenance map: source {@code packaged}, a null file, and the digest of
	 * the declared rules
	 */
	private static Map<String, Object> packagedProvenance(QualityConfig config) {
		Map<String, Object> declared = new LinkedHashMap<>();
		declared.put("expected_headers", config.expectedHeaders());
		Map<String, Object> errorHandling = new LinkedHashMap<>();
		for (Map.Entry<String, ErrorRule> entry : config.errorHandling().entrySet()) {
			errorHandling.put(entry.getKey(), entry.getValue());
		}
		declared.put("error_handling", errorHandling);
		Map<String, Object> provenance = new LinkedHashMap<>();
		provenance.put("source", "packaged");
		provenance.put("file", null);
		provenance.put("digest", qualityConfigDigest(declared));
		return provenance;
	}

	/**
	 * Python {@code quality_config_digest}: sha256 over canonical, key-sorted JSON.
	 * @param config the declared config values to hash
	 * @return the digest prefixed with {@code sha256:}
	 */
	static String qualityConfigDigest(Map<String, Object> config) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest(canonicalJson(config).getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return "sha256:" + hex;
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	/**
	 * Python
	 * {@code json.dumps(config, sort_keys=True, separators=(",", ":"), ensure_ascii=True)}.
	 * @param value maps, lists, numbers, booleans, strings, or the config records to
	 * serialize
	 * @return compact JSON with sorted keys and non-ASCII escaped
	 */
	static String canonicalJson(Object value) {
		StringBuilder sb = new StringBuilder();
		writeCanonical(value, sb);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private static void writeCanonical(Object value, StringBuilder sb) {
		if (value == null) {
			sb.append("null");
		}
		else if (value instanceof Integer || value instanceof Long) {
			sb.append(value);
		}
		else if (value instanceof Number number) {
			double d = number.doubleValue();
			if (d == Math.rint(d) && !Double.isInfinite(d)) {
				sb.append((long) d).append(".0");
			}
			else {
				sb.append(pyFloatStr(d));
			}
		}
		else if (value instanceof Boolean flag) {
			sb.append(flag ? "true" : "false");
		}
		else if (value instanceof String text) {
			writeCanonicalString(text, sb);
		}
		else if (value instanceof List<?> list) {
			sb.append('[');
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				writeCanonical(list.get(i), sb);
			}
			sb.append(']');
		}
		else if (value instanceof Map<?, ?> map) {
			List<String> keys = new ArrayList<>();
			for (Object key : map.keySet()) {
				keys.add(String.valueOf(key));
			}
			keys.sort(Comparator.naturalOrder());
			sb.append('{');
			boolean first = true;
			for (String key : keys) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				writeCanonicalString(key, sb);
				sb.append(':');
				writeCanonical(((Map<String, Object>) map).get(key), sb);
			}
			sb.append('}');
		}
		else if (value instanceof ExpectedHeader header) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("heading", header.heading());
			map.put("alternatives", header.alternatives());
			map.put("points", header.points());
			map.put("message", header.message());
			map.put("suggestion", header.suggestion());
			writeCanonical(map, sb);
		}
		else if (value instanceof ErrorRule rule) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("points", rule.points());
			map.put("message", rule.message());
			map.put("suggestion", rule.suggestion());
			map.put(rule.listKey(), rule.entries());
			writeCanonical(map, sb);
		}
		else {
			throw new IllegalArgumentException("Cannot canonicalize " + value.getClass());
		}
	}

	private static void writeCanonicalString(String text, StringBuilder sb) {
		sb.append('"');
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			switch (ch) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				default -> {
					if (ch < 0x20) {
						sb.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
					}
					else if (ch < 0x80) {
						sb.append(ch);
					}
					else {
						sb.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
					}
				}
			}
		}
		sb.append('"');
	}

	// --- skill type detection --------------------------------------------------

	/**
	 * Python {@code _executable_files}: sorted glob matches per directory, in pattern
	 * order.
	 * @param skillPath the skill directory whose executable subdirectories are searched
	 * @param patterns glob patterns matched against entry names
	 * @return the matching files, grouped by pattern and sorted by path within each
	 * directory
	 */
	static List<Path> executableFiles(Path skillPath, List<String> patterns) {
		List<Path> files = new ArrayList<>();
		for (String dirname : Constants.EXECUTABLE_SKILL_DIRS) {
			Path directory = skillPath.resolve(dirname);
			if (!Files.isDirectory(directory)) {
				continue;
			}
			for (String pattern : patterns) {
				List<Path> matches = new ArrayList<>();
				try (var stream = Files.list(directory)) {
					for (Path candidate : stream.toList()) {
						if (globNameMatches(candidate.getFileName().toString(), pattern)) {
							matches.add(candidate);
						}
					}
				}
				catch (IOException ex) {
					continue;
				}
				matches.sort(Comparator.comparing(Path::toString));
				files.addAll(matches);
			}
		}
		return files;
	}

	/**
	 * fnmatch {@code Path.match} on the entry name.
	 * @param name the entry basename
	 * @param pattern shell glob in which only {@code *} and {@code ?} are wildcards
	 * @return {@code true} when the whole name matches the pattern
	 */
	private static boolean globNameMatches(String name, String pattern) {
		StringBuilder regex = new StringBuilder();
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			switch (c) {
				case '*' -> regex.append(".*");
				case '?' -> regex.append('.');
				case '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' ->
					regex.append(Pattern.quote(String.valueOf(c)));
				default -> regex.append(c);
			}
		}
		return Pattern.compile(regex.toString()).matcher(name).matches();
	}

	/**
	 * Python {@code _runs_only_node_scripts}.
	 * @param scriptFiles the skill's executable scripts
	 * @return {@code false} for an empty list, otherwise {@code true} only when every
	 * script is a Node one
	 */
	static boolean runsOnlyNodeScripts(List<Path> scriptFiles) {
		if (scriptFiles.isEmpty()) {
			return false;
		}
		for (Path script : scriptFiles) {
			boolean node = false;
			for (String pattern : Constants.NODE_SCRIPT_PATTERNS) {
				if (globNameMatches(script.getFileName().toString(), pattern)) {
					node = true;
					break;
				}
			}
			if (!node) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasExecutableDirectory(Path skillPath) {
		for (String dirname : Constants.EXECUTABLE_SKILL_DIRS) {
			if (Files.isDirectory(skillPath.resolve(dirname))) {
				return true;
			}
		}
		return false;
	}

	private static List<Path> childDirectories(Path skillPath) {
		List<Path> dirs = new ArrayList<>();
		try (var stream = Files.list(skillPath)) {
			for (Path child : stream.toList()) {
				if (Files.isDirectory(child)) {
					dirs.add(child);
				}
			}
		}
		catch (IOException ex) {
			return List.of();
		}
		dirs.sort(Comparator.comparing(Path::toString));
		return dirs;
	}

	/**
	 * Python {@code find_lib_module}: first non-excluded child directory holding
	 * {@code __init__.py}.
	 * @param skillPath the skill directory whose children are inspected
	 * @return that module directory, {@code null} when no child qualifies
	 */
	static Path findLibModule(Path skillPath) {
		for (Path child : childDirectories(skillPath)) {
			String name = child.getFileName().toString();
			if (Constants.QUALITY_EXCLUDED_DIRS.contains(name) || name.startsWith(".")) {
				continue;
			}
			if (Files.isRegularFile(child.resolve("__init__.py"))) {
				return child;
			}
		}
		return null;
	}

	/**
	 * Python {@code detect_skill_type}.
	 * @param skillPath the skill directory to classify
	 * @return {@code hybrid}, {@code script-based}, {@code lib-based},
	 * {@code resource-based} or {@code guide-only}
	 */
	public static String detectSkillType(Path skillPath) {
		boolean hasScripts = !executableFiles(skillPath, Constants.EXECUTABLE_SCRIPT_PATTERNS).isEmpty();
		boolean hasLib = findLibModule(skillPath) != null;
		boolean hasResources = false;
		for (String dirname : Constants.QUALITY_RESOURCE_DIRS) {
			if (Files.exists(skillPath.resolve(dirname))) {
				hasResources = true;
				break;
			}
		}
		if (hasScripts && (hasLib || hasResources)) {
			return "hybrid";
		}
		if (hasScripts) {
			return "script-based";
		}
		if (hasLib) {
			return "lib-based";
		}
		if (hasResources) {
			return "resource-based";
		}
		return "guide-only";
	}

	// --- entry points ----------------------------------------------------------

	@Override
	public ValidationResult validate(Path skillPath) {
		ValidationResult result = isSkillDirectory(skillPath) ? validateSingleSkill(skillPath)
				: validateFolder(skillPath);
		// min_score sits beside the digest rather than in it: the digest names the rules,
		// min_score the pass bar.
		Map<String, Object> config = new LinkedHashMap<>(this.qualityConfigProvenance);
		config.put("min_score", this.minScore);
		result.getMetadata().put("quality_config", config);
		return result;
	}

	/**
	 * Python {@code _validate_folder}: aggregate one quality result per skill.
	 * @param root the folder to scan for skills
	 * @return the merged per-skill results plus the averaged {@code quality_scores}
	 * digest, or one HIGH finding when the folder holds no skills
	 */
	ValidationResult validateFolder(Path root) {
		List<Path> skillDirs = findAllSkills(root);
		if (skillDirs.isEmpty()) {
			ValidationResult empty = new ValidationResult();
			empty.setValidatorName("QUALITY");
			empty.addFinding(new Finding("QUALITY", Severity.HIGH, "skill_discovery",
					"No skills found in target directory", root.toString()));
			return empty;
		}

		ValidationResult result = new ValidationResult();
		result.setValidatorName("QUALITY");
		result.setValidatorDescription(getDescription());
		List<Map<String, Object>> allQuality = new ArrayList<>();
		for (Path skillDir : skillDirs) {
			ValidationResult sub = validateSingleSkill(skillDir);
			result.mergeWithPrefix(sub, skillDir.getFileName().toString());
			Object scores = sub.getMetadata().get("quality_scores");
			if (scores instanceof Map) {
				allQuality.add(asMap(scores));
			}
		}

		if (!allQuality.isEmpty()) {
			result.getMetadata().put("quality_scores_all", allQuality);
			double sum = 0;
			for (Map<String, Object> scores : allQuality) {
				sum += ((Number) scores.get("overall_score")).doubleValue();
			}
			double avgScore = sum / allQuality.size();
			Map<String, Object> avgDims = new LinkedHashMap<>();
			for (String dimension : List.of("correctness", "discoverability", "reliability", "efficiency")) {
				List<Map<String, Object>> dimScores = new ArrayList<>();
				for (Map<String, Object> scores : allQuality) {
					Map<String, Object> dims = asMap(scores.get("dimensions"));
					if (dims != null && dims.get(dimension) instanceof Map) {
						dimScores.add(asMap(dims.get(dimension)));
					}
				}
				if (!dimScores.isEmpty()) {
					double scoreSum = 0;
					int issuesCount = 0;
					for (Map<String, Object> dim : dimScores) {
						scoreSum += ((Number) dim.get("score")).doubleValue();
						issuesCount += ((Number) dim.get("issues_count")).intValue();
					}
					Map<String, Object> aggregated = new LinkedHashMap<>();
					aggregated.put("score", pyRound(scoreSum / dimScores.size(), 1));
					aggregated.put("weight", dimScores.get(0).get("weight"));
					aggregated.put("issues_count", issuesCount);
					avgDims.put(dimension, aggregated);
				}
			}
			Map<String, Object> aggregated = new LinkedHashMap<>();
			aggregated.put("overall_score", pyRound(avgScore, 1));
			aggregated.put("grade", scoreToGrade(avgScore));
			aggregated.put("skill_count", allQuality.size());
			aggregated.put("dimensions", avgDims);
			result.getMetadata().put("quality_scores", aggregated);
		}
		return result;
	}

	/**
	 * Python {@code _validate_single_skill}: the full 4-dimension analysis on one skill.
	 * @param skillPath the skill directory to score
	 * @return one finding per deduction, a failed gate when the score is under the
	 * minimum, and the {@code quality_scores} metadata
	 */
	ValidationResult validateSingleSkill(Path skillPath) {
		ValidationResult result = new ValidationResult();
		result.setValidatorName("QUALITY");
		result.setValidatorDescription(getDescription());
		QualityScores qs = new QualityScores(
				(skillPath.getFileName() == null) ? "" : skillPath.getFileName().toString());

		Path manifest = findSkillManifest(skillPath);
		if (manifest == null) {
			result.addFinding(new Finding("QUALITY", Severity.HIGH, "skill_manifest", "SKILL.md not found",
					skillPath.toString()));
			result.getMetadata().put("quality_scores", qs.toMap());
			return result;
		}

		String content = Frontmatter.readUtf8(manifest);
		if (content == null) {
			content = "";
		}
		List<String> lines = List.of(content.split("\n", -1));

		Map<String, Object> frontmatterData = parseFrontmatter(content);
		if (frontmatterData != null && !frontmatterData.isEmpty()) {
			qs.hasFrontmatter = true;
		}

		qs.skillType = detectSkillType(skillPath);

		checkCorrectness(qs, content, skillPath, frontmatterData);
		checkDiscoverability(qs, content, frontmatterData);
		checkReliability(qs, content, skillPath);
		checkEfficiency(qs, content, lines, skillPath);
		checkSpecFields(qs, frontmatterData);

		for (QualityIssue issue : qs.allIssues()) {
			Severity severity = switch (issue.severity()) {
				case "error" -> Severity.HIGH;
				case "warning" -> Severity.MEDIUM;
				default -> Severity.LOW;
			};
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("dimension", issue.dimension());
			metadata.put("deduction", integral(issue.deduction()));
			result.addFinding(new Finding("QUALITY", severity, "quality_" + issue.dimension(), issue.message(),
					manifest.toString(), null, null, issue.suggestion(), metadata));
		}

		if (qs.overallScore() < this.minScore) {
			result.failGate("Quality score " + oneDecimal(qs.overallScore()) + "/100 is below the required "
					+ this.minScore + "/100 in " + manifest);
		}

		result.getMetadata().put("quality_scores", qs.toMap());

		result.addSuccess("quality_score",
				"Score: " + oneDecimal(qs.overallScore()) + "/100 (Grade: " + qs.grade() + ")", Map.of("overall_score",
						pyRound(qs.overallScore(), 1), "grade", qs.grade(), "skill_type", qs.skillType));
		return result;
	}

	/**
	 * Python {@code _parse_frontmatter}.
	 * @param content the raw SKILL.md text
	 * @return the delimited YAML mapping, or {@code null} without one, when it is not a
	 * mapping, or when the YAML fails to parse
	 */
	@SuppressWarnings("unchecked")
	static Map<String, Object> parseFrontmatter(String content) {
		Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
		if (!matcher.lookingAt()) {
			return null;
		}
		try {
			Object data = new Yaml(new SafeConstructor(new LoaderOptions())).load(matcher.group(1));
			return (data instanceof Map) ? (Map<String, Object>) data : null;
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	// --- correctness (0.35) ----------------------------------------------------

	void checkCorrectness(QualityScores qs, String content, Path skillPath, Map<String, Object> frontmatter) {
		QualityDimension dim = qs.correctness;

		if (frontmatter != null && !frontmatter.isEmpty()) {
			checkFrontmatterCorrectness(dim, frontmatter);
		}

		// Instructions — presence is enforced by SchemaValidator; quality only tracks the
		// flag for downstream heuristics (action verbs, list format).
		qs.hasInstructions = content.contains("## Instructions") || content.contains("## Usage");

		checkTypeSpecific(qs, content, skillPath);

		// Examples — quality checks for actual example *content*, not the heading.
		if (content.contains("```") || content.contains("Example:") || content.contains("**Example:**")) {
			qs.hasExamples = true;
		}
		else if (!content.contains("## Examples")) {
			dim.deduct(5, "info", "No examples provided", "Add example usage with code blocks");
		}

		if (WINDOWS_PATH.matcher(content).find()) {
			dim.deduct(10, "warning", "Windows-style paths detected",
					"Use forward slashes for cross-platform compatibility");
		}

		// An unreferenced README.md is an allowed human-facing supporting file; only a
		// SKILL.md link to it pulls human docs into the agent context (Anthropic H2).
		if (Files.exists(skillPath.resolve("README.md")) && referencesReadme(content)) {
			dim.deduct(5, "warning", "SKILL.md references README.md (pulls human-facing docs into agent context)",
					"Keep README.md human-facing and unreferenced; move any agent-facing "
							+ "content into SKILL.md or a references/ file");
		}
	}

	private static final Pattern WINDOWS_PATH = Pattern.compile("(?:scripts|references|assets)\\\\[\\w\\\\]+",
			Pattern.UNICODE_CHARACTER_CLASS);

	/**
	 * Python {@code _references_readme}.
	 * @param content the SKILL.md body
	 * @return {@code true} when a link target normalizes to {@code readme.md}
	 */
	static boolean referencesReadme(String content) {
		for (String linkTarget : HygieneValidator.MarkdownLinks.linkTargets(content, false)) {
			String path = normalizedQualityLocalPath(linkTarget);
			if (path != null && path.toLowerCase(Locale.ROOT).equals("readme.md")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Python {@code _check_frontmatter_correctness}.
	 * @param dim the Correctness dimension that receives the deductions
	 * @param fm the parsed frontmatter mapping
	 */
	void checkFrontmatterCorrectness(QualityDimension dim, Map<String, Object> fm) {
		// XML tags in non-name/description fields (Anthropic H3)
		for (Map.Entry<String, Object> entry : fm.entrySet()) {
			String key = entry.getKey();
			if ("name".equals(key) || "description".equals(key)) {
				continue;
			}
			Object val = entry.getValue();
			List<String> values = List.of(pyStr(val));
			// A string argument-hint documents CLI placeholders (``<file> [--strict]``).
			if ("argument-hint".equals(key) && val instanceof String) {
				values = List.of(maskArgumentHint((String) val));
			}
			else if ("argument-hint".equals(key) && val instanceof List<?> list && !list.isEmpty()) {
				boolean allStrings = list.stream().allMatch((item) -> item instanceof String);
				if (allStrings) {
					// YAML reads an unquoted ``[<file>]`` hint as a list. Check every
					// item and
					// the joined hint on its own so no tag forms across a boundary.
					List<String> checked = new ArrayList<>();
					List<String> joined = new ArrayList<>();
					for (Object item : list) {
						String text = (String) item;
						checked.add(maskArgumentHint(text));
						joined.add(text);
					}
					checked.add(maskArgumentHint(String.join(" ", joined)));
					values = checked;
				}
			}
			boolean tagged = false;
			for (String checked : values) {
				if (XML_TAG_RE.matcher(checked).find()) {
					tagged = true;
					break;
				}
			}
			if (tagged) {
				dim.deduct(15, "error", "XML tags in frontmatter field '" + key + "' (potential prompt injection)",
						"Remove XML angle brackets from all frontmatter fields");
				break;
			}
		}

		String name = pyStrip(pyStr(fm.getOrDefault("name", "")));
		String desc = pyStrip(pyStr(fm.getOrDefault("description", "")));

		if (!name.isEmpty()) {
			if (!NAME_FORMAT.matcher(name).matches()) {
				dim.deduct(15, "error", "Invalid name format: '" + name + "' (lowercase/numbers/hyphens only)",
						"Use only lowercase letters, numbers, and hyphens");
			}
			if (containsAnyTerm(name, Constants.QUALITY_RESERVED_NAMES)) {
				dim.deduct(15, "error", "Name contains reserved word (anthropic, claude)",
						"Remove reserved words from skill name");
			}
			if (XML_TAG_RE.matcher(name).find()) {
				dim.deduct(15, "error", "Name contains XML tags", "Remove XML tags from skill name");
			}
		}

		if (!desc.isEmpty() && XML_TAG_RE.matcher(desc).find()) {
			dim.deduct(15, "error", "Description contains XML tags", "Remove XML tags from description");
		}
	}

	private static final Pattern NAME_FORMAT = Pattern.compile("[a-z0-9-]+");

	/**
	 * Python {@code _check_type_specific}.
	 * @param qs the scores under construction, whose {@code skillType} picks the branch
	 * @param content the SKILL.md body
	 * @param skillPath the skill directory holding its scripts, library and resources
	 */
	void checkTypeSpecific(QualityScores qs, String content, Path skillPath) {
		String skillType = qs.skillType;

		if ("script-based".equals(skillType) || "hybrid".equals(skillType)) {
			List<Path> scriptFiles = executableFiles(skillPath, Constants.EXECUTABLE_SCRIPT_PATTERNS);
			if (hasExecutableDirectory(skillPath)) {
				qs.hasScripts = true;
				qs.scriptCount = scriptFiles.size();
				if (qs.scriptCount == 0) {
					qs.correctness.deduct(10, "warning",
							"scripts/ or tools/ exists but contains no .py, .sh, .mjs, .cjs or .js files");
				}
			}
			else {
				qs.correctness.deduct(25, "error",
						"No scripts/ or tools/ directory found (detected as script-based skill)",
						"Create scripts/ or tools/ with at least one executable script");
			}

			if (!content.contains("## Available Scripts") && !content.contains("| Script |")) {
				qs.correctness.deduct(10, "warning", "No documented scripts in table format",
						"Add '## Available Scripts' with table: | Script | Purpose | Arguments |");
			}
			if (!content.contains("run_script") && !runsOnlyNodeScripts(scriptFiles)) {
				qs.correctness.deduct(10, "warning", "Instructions don't mention 'run_script'",
						"Add explicit run_script() call examples");
			}
		}
		else if ("lib-based".equals(skillType)) {
			Path libDir = findLibModule(skillPath);
			if (libDir != null) {
				qs.hasLibModule = true;
				if (!Files.isRegularFile(skillPath.resolve("pyproject.toml"))) {
					qs.correctness.deduct(10, "warning", "Lib-based skill missing pyproject.toml",
							"Add pyproject.toml with package metadata and dependencies");
				}
				if (!hasApiDocumentation(content)) {
					qs.correctness.deduct(10, "warning", "Lib-based skill lacks API/import documentation",
							"Document how to import and use the library");
				}
			}
			else {
				qs.correctness.deduct(15, "warning", "Detected as lib-based but no Python module found",
						"Ensure module directory has __init__.py");
			}

			List<String> presentRes = new ArrayList<>();
			for (String dirname : Constants.QUALITY_RESOURCE_DIRS) {
				if (Files.exists(skillPath.resolve(dirname))) {
					presentRes.add(dirname);
				}
			}
			if (!presentRes.isEmpty()) {
				List<String> resKw = List.of("template", "asset", "design", "style", "css", "html", "resource");
				if (!containsAnyTerm(content, resKw)) {
					qs.correctness.deduct(5, "info", "Lib-based skill has resource directories ("
							+ String.join(", ", presentRes) + ") " + "but SKILL.md lacks resource documentation");
				}
			}
		}
		else if ("resource-based".equals(skillType)) {
			List<String> resKw = List.of("template", "asset", "design", "style", "css", "html", "resource");
			if (!containsAnyTerm(content, resKw)) {
				qs.correctness.deduct(10, "warning", "Resource-based skill lacks documentation of available resources",
						"Document available templates, assets, and design resources in SKILL.md");
			}
		}
		else if ("guide-only".equals(skillType)) {
			int bodyLines = 0;
			for (String line : content.split("\n", -1)) {
				if (!pyStrip(line).isEmpty()) {
					bodyLines++;
				}
			}
			if (bodyLines < 20) {
				qs.correctness.deduct(15, "warning",
						"Guide-only skill has very little content (" + bodyLines + " lines)",
						"Guide skills should have detailed instructions since they have no code");
			}
		}
	}

	// --- discoverability (0.25) ------------------------------------------------

	void checkDiscoverability(QualityScores qs, String content, Map<String, Object> frontmatter) {
		QualityDimension dim = qs.discoverability;

		String desc = "";
		if (frontmatter != null && !frontmatter.isEmpty()) {
			desc = pyStrip(pyStr(frontmatter.getOrDefault("description", "")));
		}

		if (!desc.isEmpty()) {
			int descLength = pyLen(desc);
			if (descLength < 20) {
				dim.deduct(20, "warning", "Description too short (" + descLength + " chars, recommend 50-150)",
						"Add more context: what tasks does this skill handle?");
			}
			else if (descLength > 200) {
				dim.deduct(5, "info", "Description very long (" + descLength + " chars, recommend 50-150)",
						"Keep descriptions concise for progressive disclosure");
			}

			if (!containsAnyTerm(desc, List.of("use", "when", "for", "helps", "allows"))) {
				dim.deduct(10, "info", "Description doesn't mention WHEN to use this skill",
						"Add trigger context: 'Use for...', 'When you need to...'");
			}

			if (containsAnyTerm(desc, List.of("something", "things", "stuff", "various", "general"))) {
				dim.deduct(15, "warning", "Description contains vague words", "Be specific about what this skill does");
			}

			if (containsAnyTerm(desc, List.of("i can", "i will", "you can", "you should", "your", "my", "we can"))) {
				dim.deduct(15, "warning", "Description uses first/second person",
						"Use third person: 'Processes files' not 'I can process'");
			}

			// Broad description without negative triggers (M1)
			List<String> generic = List.of("data", "files", "documents", "project", "manage", "handle", "process");
			List<String> negatives = List.of("not for", "do not use", "instead use", "except when", "not when");
			if (descLength > 100 && containsAnyTerm(desc, generic) && !containsAnyTerm(desc, negatives)) {
				dim.deduct(5, "info", "Broad description without negative triggers may cause over-triggering",
						"Add boundary phrases like 'Do NOT use for...'");
			}
		}

		if (!content.contains("## Purpose")) {
			dim.deduct(5, "info", "No '## Purpose' section", "Add purpose section to clarify use cases");
		}

		String sn = qs.skillName;
		if (pyLen(sn) < 5) {
			dim.deduct(10, "warning", "Skill name very short: '" + sn + "'",
					"Use descriptive names like 'crypto-utils' not 'crypto'");
		}
		if (!Pattern.matches("[a-z][a-z0-9_-]*", sn)) {
			dim.deduct(10, "warning", "Skill name not following convention: '" + sn + "'",
					"Use lowercase with hyphens: my-skill-name");
		}
	}

	// --- reliability (0.25) ----------------------------------------------------

	void checkReliability(QualityScores qs, String content, Path skillPath) {
		QualityDimension dim = qs.reliability;
		Map<String, ErrorRule> errorHandling = this.qualityConfig.errorHandling();

		ErrorRule mentioned = errorHandling.get("mentioned");
		qs.hasErrorHandling = containsAnyWord(content, mentioned.entries());
		if (!qs.hasErrorHandling && pointsOf(mentioned) != 0) {
			dim.deduct(pointsOf(mentioned), "info", mentioned.message(), mentioned.suggestion());
		}

		// Type-aware code quality
		if ("script-based".equals(qs.skillType) || "hybrid".equals(qs.skillType)) {
			checkCodeErrorHandling(dim, executableFiles(skillPath, Constants.PYTHON_SCRIPT_PATTERNS),
					errorHandling.get("scripts"));
		}
		else if ("lib-based".equals(qs.skillType)) {
			Path libDir = findLibModule(skillPath);
			if (libDir != null) {
				List<Path> modules = new ArrayList<>();
				try (var stream = Files.walk(libDir)) {
					for (Path file : stream.sorted(Comparator.comparing(Path::toString)).toList()) {
						String name = (file.getFileName() == null) ? "" : file.getFileName().toString();
						if (Files.isRegularFile(file) && name.endsWith(".py") && !"__init__.py".equals(name)) {
							modules.add(file);
						}
					}
				}
				catch (IOException ex) {
					modules = List.of();
				}
				checkCodeErrorHandling(dim, modules, errorHandling.get("lib"));
			}
		}

		for (ExpectedHeader expected : this.qualityConfig.expectedHeaders()) {
			List<String> acceptedHeadings = new ArrayList<>();
			acceptedHeadings.add(expected.heading());
			acceptedHeadings.addAll(expected.alternatives());
			boolean present = false;
			for (String heading : acceptedHeadings) {
				if (content.contains(heading)) {
					present = true;
					break;
				}
			}
			if (!present) {
				dim.deduct(pointsOf(expected), "info", expected.message(), expected.suggestion());
			}
		}
	}

	private static double pointsOf(ExpectedHeader rule) {
		return (rule.points() == null) ? 0 : rule.points().doubleValue();
	}

	private static double pointsOf(ErrorRule rule) {
		return (rule.points() == null) ? 0 : rule.points().doubleValue();
	}

	/**
	 * Python {@code _check_code_error_handling}.
	 * @param dim the Reliability dimension that receives the deduction
	 * @param files the Python sources to read
	 * @param rule the error-handling rule supplying points, markers, message and
	 * suggestion
	 */
	static void checkCodeErrorHandling(QualityDimension dim, List<Path> files, ErrorRule rule) {
		if (pointsOf(rule) == 0) {
			return;
		}
		List<String> unhandled = new ArrayList<>();
		for (Path path : files) {
			String code = Frontmatter.readUtf8(path);
			if (code == null) {
				continue;
			}
			boolean hasTry = code.contains("try:") && code.contains("except");
			boolean marker = false;
			for (String candidate : rule.entries()) {
				if (code.contains(candidate)) {
					marker = true;
					break;
				}
			}
			if (!hasTry && !marker) {
				unhandled.add(path.getFileName().toString());
			}
		}
		if (!unhandled.isEmpty()) {
			List<String> shown = unhandled.subList(0, Math.min(3, unhandled.size()));
			dim.deduct(pointsOf(rule), "info", rule.message() + ": " + String.join(", ", shown), rule.suggestion());
		}
	}

	// --- efficiency (0.15) -----------------------------------------------------

	void checkEfficiency(QualityScores qs, String content, List<String> lines, Path skillPath) {
		QualityDimension dim = qs.efficiency;

		// Token estimates (characters / QUALITY_CHARS_PER_TOKEN); not a tokenizer count
		qs.totalTokens = pyLen(content) / Constants.QUALITY_CHARS_PER_TOKEN;
		Matcher fmMatch = FRONTMATTER_PATTERN.matcher(content);
		boolean hasFrontmatterMatch = fmMatch.lookingAt();
		Map<String, Object> frontmatter = hasFrontmatterMatch ? parseFrontmatter(content) : null;
		if (hasFrontmatterMatch && frontmatter != null && !frontmatter.isEmpty()) {
			qs.frontmatterTokens = pyLen(fmMatch.group(1)) / Constants.QUALITY_CHARS_PER_TOKEN;
			qs.instructionsTokens = pyLen(fmMatch.group(2)) / Constants.QUALITY_CHARS_PER_TOKEN;
		}

		// The token budget is a recommendation: exceeding it is advisory (MEDIUM), and
		// the
		// deduction still counts toward the --min-score gate.
		if (qs.totalTokens > Constants.QUALITY_RECOMMENDED_MAX_TOKENS) {
			dim.deduct(15, "warning",
					"Large skill (~" + qs.totalTokens + " estimated tokens at " + Constants.QUALITY_CHARS_PER_TOKEN
							+ " characters per token, " + "recommended max <" + Constants.QUALITY_RECOMMENDED_MAX_TOKENS
							+ "; a real tokenizer may count more). "
							+ "Per agentskills.io, SKILL.md should be concise (~500 lines) — "
							+ "large skill bodies increase token cost after invocation; long or unfocused "
							+ "top-level descriptions can degrade agent routing accuracy",
					"Keep required sections concise; move detailed examples, reference material, "
							+ "and supporting docs to the references/ directory");
		}

		// Repetition (compare against non-empty lines to avoid false positives from
		// blanks)
		List<String> nonEmpty = new ArrayList<>();
		for (String line : lines) {
			if (!pyStrip(line).isEmpty()) {
				nonEmpty.add(line);
			}
		}
		Set<String> strippedLines = new LinkedHashSet<>();
		for (String line : nonEmpty) {
			strippedLines.add(pyStrip(line));
		}
		if (!strippedLines.isEmpty() && strippedLines.size() < nonEmpty.size() * 0.7) {
			dim.deduct(10, "info", "High line repetition detected", "Remove duplicate instructions or use references");
		}

		// Instruction clarity
		String section = "";
		for (String heading : List.of("## Instructions", "## Usage")) {
			if (!content.contains(heading)) {
				continue;
			}
			String[] parts = literalSplit(content, "\n" + heading + "\n", 2);
			section = (parts.length > 1) ? literalSplit(parts[1], "\n## ", 2)[0] : "";
			break;
		}
		if (!section.isEmpty()) {
			List<String> actionWords = List.of("use", "call", "run", "execute", "pass", "set", "add", "mark", "update",
					"keep");
			if (!containsAnyTerm(section, actionWords)) {
				dim.deduct(15, "warning", "Instructions lack clear action verbs",
						"Use imperative: 'Use run_script with...', 'Call activate_skill...'");
			}
			if (!(section.contains("- ") || section.contains("1.") || section.contains("* "))) {
				dim.deduct(5, "info", "Instructions not in list format",
						"Use bullet points or numbered steps for clarity");
			}
		}

		if (containsAnyTerm(content, List.of("utilize", "facilitate", "leverage", "paradigm", "synergy"))) {
			dim.deduct(5, "info", "Uses complex/corporate language",
					"Use simple, direct language: 'use' not 'utilize'");
		}

		Path refsDir = skillPath.resolve("references");
		if (Files.exists(refsDir)) {
			List<Path> refDocs = markdownChildren(refsDir);
			List<String> vague = List.of("doc", "file", "data", "info", "misc", "temp");
			for (Path ref : refDocs) {
				String stem = stemOf(ref).toLowerCase(Locale.ROOT);
				if (isAllDigits(stem) || vague.contains(stem) || stem.codePointCount(0, stem.length()) < 4) {
					dim.deduct(5, "info", "Non-descriptive filename: " + ref.getFileName(),
							"Use descriptive names: 'form_validation_rules.md' not 'doc2.md'");
					break;
				}
			}

			// Deeply nested references
			for (Path ref : refDocs) {
				String rc = Frontmatter.readUtf8(ref);
				if (rc == null) {
					continue;
				}
				if (hasNestedMarkdownReference(rc)) {
					dim.deduct(10, "warning", "Deeply nested references in " + ref.getFileName(),
							"Keep references one level deep from SKILL.md");
					break;
				}
			}

			// Non-doc files in references/
			Set<String> nonDoc = Set.of(".py", ".sh", ".json", ".csv", ".yaml", ".yml", ".toml");
			for (Path ref : sortedChildren(refsDir)) {
				String name = ref.getFileName().toString();
				int dot = name.lastIndexOf('.');
				String suffix = (dot > 0) ? name.substring(dot) : "";
				if (Files.isRegularFile(ref) && nonDoc.contains(suffix)) {
					dim.deduct(5, "info", "Non-doc file in references/: " + ref.getFileName(),
							"Code belongs in scripts/, data in assets/. references/ is for .md docs.");
					break;
				}
			}
		}
	}

	private static List<Path> markdownChildren(Path directory) {
		List<Path> out = new ArrayList<>();
		try (var stream = Files.list(directory)) {
			for (Path child : stream.toList()) {
				if (child.getFileName().toString().endsWith(".md")) {
					out.add(child);
				}
			}
		}
		catch (IOException ex) {
			return List.of();
		}
		out.sort(Comparator.comparing(Path::toString));
		return out;
	}

	private static List<Path> sortedChildren(Path directory) {
		List<Path> out = new ArrayList<>();
		try (var stream = Files.list(directory)) {
			out.addAll(stream.toList());
		}
		catch (IOException ex) {
			return List.of();
		}
		out.sort(Comparator.comparing(Path::toString));
		return out;
	}

	/**
	 * Python {@code Path.stem}: the name without its final suffix.
	 * @param path the file to name
	 * @return the basename up to its last dot, or the whole basename when it has none
	 */
	private static String stemOf(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return (dot > 0) ? name.substring(0, dot) : name;
	}

	private static boolean isAllDigits(String value) {
		if (value.isEmpty()) {
			return false;
		}
		return Pattern.matches("\\d+", value);
	}

	// --- SKILL_SPEC field checks (adds to correctness) -------------------------

	void checkSpecFields(QualityScores qs, Map<String, Object> frontmatter) {
		if (frontmatter == null || frontmatter.isEmpty()) {
			return;
		}
		Object metadataValue = frontmatter.get("metadata");
		Map<String, Object> metadata;
		if (metadataValue instanceof Map) {
			metadata = asMap(metadataValue);
		}
		else {
			// A list, string, or scalar is invalid metadata per the schema; score it as
			// absent metadata rather than indexing into it.
			metadata = Map.of();
		}

		Map<String, String> topLevelFields = Map.of("version", "Semantic version (e.g., \"1.0.0\")");
		for (Map.Entry<String, String> entry : topLevelFields.entrySet()) {
			String fieldName = entry.getKey();
			if (!frontmatter.containsKey(fieldName) || frontmatter.get(fieldName) == null) {
				qs.correctness.deduct(5, "warning", "SKILL_SPEC recommended field missing: '" + fieldName + "'",
						"Add '" + fieldName + "' to frontmatter — " + entry.getValue());
			}
		}

		Map<String, String> nestedFields = new LinkedHashMap<>();
		nestedFields.put("author", "Author name or team (under metadata:)");
		nestedFields.put("tags", "Categorization tags (under metadata:, list of 1-5 items)");
		for (Map.Entry<String, String> entry : nestedFields.entrySet()) {
			String fieldName = entry.getKey();
			if (!metadata.containsKey(fieldName) || metadata.get(fieldName) == null) {
				qs.correctness.deduct(5, "warning",
						"SKILL_SPEC recommended field missing: 'metadata." + fieldName + "'",
						"Add '" + fieldName + "' under metadata: — " + entry.getValue());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return (value instanceof Map) ? (Map<String, Object>) value : null;
	}

	/** Python {@code QUALITY_GRADE_THRESHOLDS}, highest threshold first. */
	private record GradeThreshold(int threshold, String grade) {
	}

	// --- scoring model ---------------------------------------------------------

	/**
	 * Python {@code QualityIssue}.
	 *
	 * @param severity the rule's own level: {@code error}, {@code warning} or
	 * {@code info}
	 * @param dimension the scored dimension the deduction came from
	 * @param message the finding text
	 * @param suggestion remediation advice, nullable
	 * @param deduction points taken off the dimension's score
	 * @author Ivan Rodriguez
	 */
	record QualityIssue(String severity, String dimension, String message, String suggestion, double deduction) {
		Map<String, Object> toMap() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("severity", this.severity);
			map.put("message", this.message);
			map.put("suggestion", this.suggestion);
			map.put("deduction", integral(this.deduction));
			return map;
		}
	}

	/** Python {@code QualityDimension}. */
	static final class QualityDimension {

		private final String name;

		private final double weight;

		private double score = 100.0;

		private final List<QualityIssue> issues = new ArrayList<>();

		QualityDimension(String name, double weight) {
			this.name = name;
			this.weight = weight;
		}

		double weightedScore() {
			return this.score * this.weight;
		}

		int issuesCount() {
			return this.issues.size();
		}

		double score() {
			return this.score;
		}

		List<QualityIssue> issues() {
			return this.issues;
		}

		/**
		 * Python {@code deduct}: record the issue, then floor the score at zero.
		 * @param amount points to take off this dimension
		 * @param severity the issue's own level, mapped to a finding severity later
		 * @param message the finding text
		 * @param suggestion remediation advice, {@code null} from the three-argument
		 * overload
		 */
		void deduct(double amount, String severity, String message, String suggestion) {
			this.issues.add(new QualityIssue(severity, this.name, message, suggestion, amount));
			this.score = Math.max(0.0, this.score - amount);
		}

		void deduct(double amount, String severity, String message) {
			deduct(amount, severity, message, null);
		}

		Map<String, Object> toMap() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("score", pyRound(this.score, 1));
			map.put("weight", this.weight);
			map.put("issues_count", issuesCount());
			List<Map<String, Object>> listed = new ArrayList<>();
			for (QualityIssue issue : this.issues) {
				listed.add(issue.toMap());
			}
			map.put("issues", listed);
			return map;
		}

	}

	/** Python {@code QualityScoreResult}. */
	static final class QualityScores {

		final String skillName;

		String skillType = "unknown";

		final QualityDimension correctness = new QualityDimension("correctness", WEIGHT_CORRECTNESS);

		final QualityDimension discoverability = new QualityDimension("discoverability", WEIGHT_DISCOVERABILITY);

		final QualityDimension reliability = new QualityDimension("reliability", WEIGHT_RELIABILITY);

		final QualityDimension efficiency = new QualityDimension("efficiency", WEIGHT_EFFICIENCY);

		int totalTokens;

		int frontmatterTokens;

		int instructionsTokens;

		int scriptCount;

		boolean hasExamples;

		boolean hasErrorHandling;

		boolean hasFrontmatter;

		boolean hasInstructions;

		boolean hasScripts;

		boolean hasLibModule;

		QualityScores(String skillName) {
			this.skillName = skillName;
		}

		double overallScore() {
			return this.correctness.weightedScore() + this.discoverability.weightedScore()
					+ this.reliability.weightedScore() + this.efficiency.weightedScore();
		}

		String grade() {
			return scoreToGrade(overallScore());
		}

		List<QualityIssue> allIssues() {
			List<QualityIssue> all = new ArrayList<>();
			all.addAll(this.correctness.issues());
			all.addAll(this.discoverability.issues());
			all.addAll(this.reliability.issues());
			all.addAll(this.efficiency.issues());
			return all;
		}

		Map<String, Object> toMap() {
			Map<String, Object> dimensions = new LinkedHashMap<>();
			dimensions.put("correctness", this.correctness.toMap());
			dimensions.put("discoverability", this.discoverability.toMap());
			dimensions.put("reliability", this.reliability.toMap());
			dimensions.put("efficiency", this.efficiency.toMap());

			Map<String, Object> metrics = new LinkedHashMap<>();
			metrics.put("total_tokens", this.totalTokens);
			metrics.put("estimated_tokens", this.totalTokens);
			metrics.put("estimate_method", "characters/" + Constants.QUALITY_CHARS_PER_TOKEN);
			metrics.put("recommended_max_tokens", Constants.QUALITY_RECOMMENDED_MAX_TOKENS);
			metrics.put("frontmatter_tokens", this.frontmatterTokens);
			metrics.put("instructions_tokens", this.instructionsTokens);
			metrics.put("script_count", this.scriptCount);
			metrics.put("has_examples", this.hasExamples);
			metrics.put("has_error_handling", this.hasErrorHandling);
			metrics.put("has_frontmatter", this.hasFrontmatter);
			metrics.put("has_instructions", this.hasInstructions);
			metrics.put("has_scripts", this.hasScripts);
			metrics.put("has_lib_module", this.hasLibModule);

			Map<String, Object> map = new LinkedHashMap<>();
			map.put("overall_score", pyRound(overallScore(), 1));
			map.put("grade", grade());
			map.put("skill_type", this.skillType);
			map.put("skill_name", this.skillName);
			map.put("dimensions", dimensions);
			map.put("metrics", metrics);
			return map;
		}

	}

	// --- configuration ---------------------------------------------------------

	/**
	 * One {@code expected_headers} rule.
	 *
	 * @param heading the preferred section heading
	 * @param alternatives other headings that also satisfy the rule
	 * @param points deduction applied when no accepted heading is present
	 * @param message the finding text
	 * @param suggestion remediation advice
	 * @author Ivan Rodriguez
	 */
	record ExpectedHeader(String heading, List<String> alternatives, Number points, String message, String suggestion) {
	}

	/**
	 * One {@code error_handling} rule (its list is {@code terms} or {@code markers}).
	 *
	 * @param points deduction applied when no marker is present
	 * @param message the finding text
	 * @param suggestion remediation advice
	 * @param listKey the config key the entries live under, {@code terms} or
	 * {@code markers}
	 * @param entries substrings that count as error handling in a source file
	 * @author Ivan Rodriguez
	 */
	record ErrorRule(Number points, String message, String suggestion, String listKey, List<String> entries) {
	}

	private record QualityConfig(List<ExpectedHeader> expectedHeaders, Map<String, ErrorRule> errorHandling) {
	}

}
