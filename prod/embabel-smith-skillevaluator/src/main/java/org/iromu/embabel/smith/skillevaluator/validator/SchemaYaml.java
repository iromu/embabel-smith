/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * YAML-level helpers for the schema check: repeated frontmatter keys, the
 * {@code metadata.id} contract, and Python-flavoured value rendering.
 * <p>
 * Ports {@code _duplicate_frontmatter_keys}, {@code metadata_id_problem},
 * {@code _display_metadata_id} and the type-name rendering the Python messages get from
 * {@code type(value).__name__}.
 *
 * @author Ivan Rodriguez
 */
final class SchemaYaml {

	/** {@code metadata.id} contract: an exact (unnormalized) lowercase ASCII slug. */
	static final int METADATA_ID_MAX_LENGTH = 64;

	private static final Pattern METADATA_ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	/** Longest raw {@code metadata.id} text repeated in a finding. */
	private static final int METADATA_ID_DISPLAY_LIMIT = 80;

	private SchemaYaml() {
	}

	/**
	 * Return every repeated mapping key in a frontmatter document (the YAML parser keeps
	 * the last value silently), as dotted paths with 1-based file line numbers.
	 *
	 * <p>
	 * The {@code +2} line offset accounts for the opening {@code ---} line plus the
	 * 0-based mark, matching the Python implementation.
	 * @param frontmatterYaml the frontmatter text between the {@code ---} markers
	 * @return each repeated key as a dotted path with the 1-based file line it appears
	 * on, sorted by line; empty when the text does not parse
	 */
	static List<DuplicateKey> duplicateFrontmatterKeys(String frontmatterYaml) {
		Node root;
		try {
			Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
			// UTF-8 bytes rather than a String, see SchemaValidator.validateFrontmatter:
			// SnakeYAML's String reader chokes on supplementary characters that
			// straddle its internal buffer, which would silently drop duplicate-key
			// findings for long emoji-heavy frontmatter.
			root = yaml.compose(new org.yaml.snakeyaml.reader.UnicodeReader(new java.io.ByteArrayInputStream(
					frontmatterYaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
		}
		catch (RuntimeException ex) {
			return List.of();
		}
		List<DuplicateKey> duplicates = new ArrayList<>();
		Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Object[]> stack = new ArrayList<>();
		if (root != null) {
			stack.add(new Object[] { root, "" });
		}
		while (!stack.isEmpty()) {
			Object[] frame = stack.remove(stack.size() - 1);
			Node node = (Node) frame[0];
			String prefix = (String) frame[1];
			if (!visited.add(node)) {
				continue;
			}
			if (node instanceof MappingNode mapping) {
				Set<String> seen = new java.util.HashSet<>();
				for (NodeTuple tuple : mapping.getValue()) {
					Node keyNode = tuple.getKeyNode();
					if (!(keyNode instanceof ScalarNode scalar) || "<<".equals(scalar.getValue())) {
						continue;
					}
					String key = prefix + scalar.getValue();
					if (seen.contains(scalar.getValue())) {
						duplicates.add(new DuplicateKey(key, scalar.getStartMark().getLine() + 2));
					}
					seen.add(scalar.getValue());
					stack.add(new Object[] { tuple.getValueNode(), key + "." });
				}
			}
			else if (node instanceof SequenceNode sequence) {
				for (Node item : sequence.getValue()) {
					stack.add(new Object[] { item, prefix });
				}
			}
		}
		duplicates.sort((a, b) -> Integer.compare(a.line(), b.line()));
		return duplicates;
	}

	/**
	 * Return why {@code value} breaks the {@code metadata.id} contract, or {@code null}.
	 * @param value the {@code metadata.id} value as parsed
	 * @return the clause describing how the value breaks the contract, or {@code null}
	 * when it satisfies it
	 */
	static String metadataIdProblem(Object value) {
		if (!(value instanceof String text)) {
			return "must be a string (got " + pythonTypeName(value) + ")";
		}
		if (text.isEmpty()) {
			return "must not be empty";
		}
		int length = text.codePointCount(0, text.length());
		if (length > METADATA_ID_MAX_LENGTH) {
			return "must be at most " + METADATA_ID_MAX_LENGTH + " characters (got " + length + ")";
		}
		if (!METADATA_ID_PATTERN.matcher(text).matches()) {
			return "must be lowercase ASCII letters and digits, with single hyphens between them "
					+ "(for example 'my-skill'), and no surrounding whitespace";
		}
		return null;
	}

	/**
	 * Render a bounded preview of a {@code metadata.id} value for a finding. A non-string
	 * value is described by its type alone and never rendered: YAML accepts an alias
	 * graph here, and stringifying it would expand it eagerly.
	 * @param value the {@code metadata.id} value as parsed
	 * @return the text, ellipsis-truncated past the display limit, or a
	 * {@code <typename>} placeholder for a non-string
	 */
	static String displayMetadataId(Object value) {
		if (!(value instanceof String text)) {
			return "<" + pythonTypeName(value) + ">";
		}
		int length = text.codePointCount(0, text.length());
		if (length > METADATA_ID_DISPLAY_LIMIT) {
			return text.substring(0, text.offsetByCodePoints(0, METADATA_ID_DISPLAY_LIMIT)) + "\u2026";
		}
		return text;
	}

	/**
	 * The Python {@code type(value).__name__} for a value produced by a YAML parser.
	 * @param value the parsed value to name
	 * @return {@code NoneType}, {@code str}, {@code bool}, {@code int}, {@code float},
	 * {@code list}, {@code dict}, {@code set} or {@code date} as the Java type maps to
	 * Python, else the lowercased simple class name
	 */
	static String pythonTypeName(Object value) {
		if (value == null) {
			return "NoneType";
		}
		if (value instanceof String) {
			return "str";
		}
		if (value instanceof Boolean) {
			return "bool";
		}
		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
				|| value instanceof java.math.BigInteger) {
			return "int";
		}
		if (value instanceof Double || value instanceof Float || value instanceof java.math.BigDecimal) {
			return "float";
		}
		if (value instanceof List || value instanceof Object[]) {
			return "list";
		}
		if (value instanceof Map) {
			return "dict";
		}
		if (value instanceof Set) {
			return "set";
		}
		if (value instanceof java.util.Date) {
			return "date";
		}
		return value.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * Renders a YAML mapping key the way {@code format_validation_location} does, so
	 * boolean, null and date keys stay recognizable in a finding's field path.
	 * @param key a mapping key as parsed, which YAML permits to be a non-string
	 * @return {@code null}, {@code true}/{@code false} or the UTC date text for those
	 * keys, anything else through {@code String.valueOf}
	 */
	static String renderKey(Object key) {
		if (key == null) {
			return "null";
		}
		if (key instanceof Boolean bool) {
			return bool ? "true" : "false";
		}
		if (key instanceof java.util.Date date) {
			// SnakeYAML parses a YAML date as UTC midnight; render it in UTC so the
			// key text matches the value as written, whatever the host timezone is.
			java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd");
			format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
			return format.format(date);
		}
		return String.valueOf(key);
	}

	/**
	 * A repeated mapping key and the 1-based SKILL.md line it appears on.
	 *
	 * @param key the repeated key as a dotted path from the document root
	 * @param line the 1-based line of the file the repeat appears on
	 * @author Ivan Rodriguez
	 */
	record DuplicateKey(String key, int line) {
	}

}
