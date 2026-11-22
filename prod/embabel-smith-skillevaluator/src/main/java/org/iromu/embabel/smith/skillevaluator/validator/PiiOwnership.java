/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.iromu.embabel.smith.skillevaluator.util.Constants;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Port of {@code validators/pii_ownership.py}: locate the contact address a skill
 * explicitly declares as its owner, without granting it an exemption.
 * <p>
 * Ownership is a *check*, not a suppression: the address found here is reported as
 * {@code ownership_emails} (still blocking) instead of {@code emails}, so a repository
 * can decide by policy what to do with declared contacts while an ordinary address
 * sitting next to it on the same line stays independently blocking. Only literal,
 * unambiguous YAML scalars qualify — invalid or oversized YAML, anchors, aliases, merged
 * keys, block scalars and prose keep the ordinary email policy. Spans (not whole lines or
 * whole values) are returned so adjacent fields on one flow-mapping line stay
 * independent.
 * <h2>Documented deviations</h2>
 * <ul>
 * <li><b>snakeyaml instead of PyYAML.</b> {@code yaml.parse}/{@code yaml.compose} become
 * snakeyaml's event parser and {@link Yaml#compose}. Node marks carry the same 0-based
 * line/column/index triple, and the scalar style and tag tests map one-to-one
 * ({@code "|"/">"} become
 * {@link org.yaml.snakeyaml.nodes.ScalarStyle#LITERAL}/{@link org.yaml.snakeyaml.nodes.ScalarStyle#FOLDED}).</li>
 * <li><b>{@code load_bounded_yaml}.</b> Upstream's bounded loader (size, depth, alias and
 * recursion caps from {@code utils/structured_data.py}) is stood in for by a plain
 * {@code SafeConstructor} load plus the same 64 KiB frontmatter cap the caller already
 * applies. Both exist to refuse hostile input before it is walked; a document the bounded
 * loader rejects for exceeding a cap snakeyaml will generally still parse, which only
 * means the address is treated as a declared owner where upstream would have kept
 * ordinary email policy — the more conservative direction. The part of that loader which
 * changes an answer here — rejecting a mapping that repeats one of its own keys, so an
 * ambiguous {@code metadata.pic} claims no ownership — is ported explicitly in
 * {@link #hasDuplicateMappingKey}.</li>
 * <li><b>{@code re.ASCII}.</b> Upstream compiles the contact pattern with
 * {@code re.ASCII}, so {@code \s} is ASCII whitespace only; the port spells that class
 * out literally instead of relying on a flag Java does not have. Columns are counted in
 * UTF-16 units here and code points in Python, which only differs for contacts containing
 * astral characters.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
final class PiiOwnership {

	/**
	 * Python {@code _EMAIL} under {@code re.ASCII}: {@code \s} spelled out (no UNICODE
	 * flag in Java).
	 */
	private static final String ASCII_SPACE = "[ \\t\\n\\x0B\\f\\r]";

	private static final String EMAIL_BODY = "[^<>@" + ASCII_SPACE + "]+@[^<>@" + ASCII_SPACE + "]+\\.[A-Za-z]{2,}";

	/** Python {@code _CONTACT} compiled with {@code re.ASCII}. */
	private static final Pattern CONTACT = Pattern
		.compile("\\A(?:(?<email>" + EMAIL_BODY + ")|[^<>\n]+ <(?<namedEmail>" + EMAIL_BODY + ")>)\\z");

	/** Python {@code _FIELDS}. */
	private static final Set<String> FIELDS = Set.of("pic", "email");

	/**
	 * Python {@code FRONTMATTER_PATTERN} (same shape as {@code Frontmatter}'s private
	 * copy).
	 */
	private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
			"^\\ufeff?---[^\\S\\r\\n]*\\r?\\n(.*?)\\r?\\n---[^\\S\\r\\n]*(?=\\r?\\n|\\z)(?:\\r?\\n)?(.*)",
			Pattern.DOTALL);

	private static final int MAX_FRONTMATTER_CHARS = 65_536;

	private PiiOwnership() {
	}

	/**
	 * Python {@code ownership_email_spans}: 1-based file line to the source spans of the
	 * single-line {@code metadata.pic}/{@code metadata.email} contact values.
	 * @param filePath file being scanned; a name outside
	 * {@link Constants#SKILL_MANIFEST_VARIANTS} declares no owner
	 * @param content full text of that file, whose frontmatter is inspected
	 * @return file line number to the {@code [start, end)} character spans of the
	 * declared addresses, empty when no address qualifies as a declared owner
	 */
	static Map<Integer, List<int[]>> ownershipEmailSpans(Path filePath, String content) {
		Map<Integer, List<int[]>> spans = new LinkedHashMap<>();
		if (!Constants.SKILL_MANIFEST_VARIANTS.contains(filePath.getFileName().toString())) {
			return spans;
		}
		Matcher frontmatter = FRONTMATTER_PATTERN.matcher(content);
		if (!frontmatter.lookingAt() || frontmatter.group(1).length() > MAX_FRONTMATTER_CHARS) {
			return spans;
		}
		String raw = frontmatter.group(1);

		Yaml yaml = new Yaml(new SafeConstructor(duplicateTolerantOptions()));
		try {
			yaml.load(raw);
			for (Event event : yaml.parse(new StringReader(raw))) {
				if (event instanceof AliasEvent || hasAnchor(event)) {
					return spans;
				}
			}
			Node root = yaml.compose(new StringReader(raw));
			if (!(root instanceof MappingNode mapping)) {
				return spans;
			}
			if (hasDuplicateMappingKey(root)) {
				return spans;
			}
			for (NodeTuple entry : mapping.getValue()) {
				Node key = entry.getKeyNode();
				Node metadata = entry.getValueNode();
				if (!(key instanceof ScalarNode keyScalar) || !"metadata".equals(keyScalar.getValue())
						|| !(metadata instanceof MappingNode metadataMapping)) {
					continue;
				}
				for (NodeTuple field : metadataMapping.getValue()) {
					appendContactSpan(spans, raw, field);
				}
			}
		}
		catch (RuntimeException | StackOverflowError ex) {
			// StructuredDataError / YAMLError / RecursionError upstream: no ownership
			// claim.
			return spans;
		}
		return spans;
	}

	private static void appendContactSpan(Map<Integer, List<int[]>> spans, String raw, NodeTuple field) {
		if (!(field.getKeyNode() instanceof ScalarNode fieldScalar) || !FIELDS.contains(fieldScalar.getValue())
				|| !(field.getValueNode() instanceof ScalarNode value) || !Tag.STR.equals(value.getTag())
				|| isMultilineStyle(value.getScalarStyle())
				|| value.getStartMark().getLine() != value.getEndMark().getLine()) {
			return;
		}
		Matcher contact = CONTACT.matcher(value.getValue());
		if (!contact.matches()) {
			return;
		}
		Mark start = value.getStartMark();
		Mark end = value.getEndMark();
		String email = (contact.group("email") != null) ? contact.group("email") : contact.group("namedEmail");
		// Python slices the scalar's *source* text, quotes included: the column of
		// the opening quote is what the offset is added to, so the value text
		// (quotes stripped, escapes decoded) would place the span a character early.
		String source = raw.substring(start.getIndex(), end.getIndex());
		int offset = source.indexOf(email);
		if (offset >= 0) {
			int line = start.getLine() + 2; // YAML source begins on file line 2
			int from = start.getColumn() + offset;
			spans.computeIfAbsent(line, (ignored) -> new ArrayList<>()).add(new int[] { from, from + email.length() });
		}
	}

	/**
	 * Python {@code _UniqueKeySafeLoader.flatten_mapping}: a mapping that repeats one of
	 * its own keys is a syntax error for {@code load_bounded_yaml}, so an ambiguous
	 * {@code metadata.pic}/{@code metadata.email} claims no ownership and both addresses
	 * keep ordinary email policy. Merged keys ({@code <<}) are not duplicates — as in
	 * YAML 1.1 a mapping's own keys override merged ones — and a non-scalar key is
	 * unhashable, which upstream raises on as well and so likewise refuses the ownership
	 * claim.
	 *
	 * <p>
	 * Keys are identified by their scalar text rather than by constructed values, so two
	 * spellings of one Python value ({@code 1} next to {@code 1.0}, or {@code true} next
	 * to {@code True}) are not seen as a repeat here; quoted and unquoted forms of the
	 * same text do match, as they do upstream.
	 * @param node node to walk, from the document root down through sequences
	 * @return {@code true} when some mapping repeats a key or holds a non-scalar key,
	 * both of which upstream's loader rejects
	 */
	private static boolean hasDuplicateMappingKey(Node node) {
		if (node instanceof SequenceNode sequence) {
			for (Node child : sequence.getValue()) {
				if (hasDuplicateMappingKey(child)) {
					return true;
				}
			}
			return false;
		}
		if (!(node instanceof MappingNode mapping)) {
			return false;
		}
		Set<String> seen = new HashSet<>();
		for (NodeTuple entry : mapping.getValue()) {
			Node key = entry.getKeyNode();
			if (!(key instanceof ScalarNode keyScalar)) {
				return true; // Python: unhashable key, a constructor error
			}
			if (Tag.MERGE.equals(keyScalar.getTag())) {
				continue;
			}
			if (!seen.add(keyScalar.getValue())) {
				return true;
			}
			if (hasDuplicateMappingKey(entry.getValueNode())) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasAnchor(Event event) {
		return event instanceof NodeEvent nodeEvent && nodeEvent.getAnchor() != null
				&& !nodeEvent.getAnchor().isEmpty();
	}

	/**
	 * Python's {@code style in ('|', '>')}: a block scalar, whose value may span lines.
	 * @param style scalar style SnakeYAML assigned to the value
	 * @return {@code true} for a literal or folded block style
	 */
	private static boolean isMultilineStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle style) {
		return style == org.yaml.snakeyaml.DumperOptions.ScalarStyle.LITERAL
				|| style == org.yaml.snakeyaml.DumperOptions.ScalarStyle.FOLDED;
	}

	private static LoaderOptions duplicateTolerantOptions() {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(true);
		return options;
	}

}
