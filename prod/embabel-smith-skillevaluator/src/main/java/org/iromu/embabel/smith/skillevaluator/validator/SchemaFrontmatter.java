/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.iromu.embabel.smith.skillevaluator.util.Constants;

/**
 * Manual equivalent of the pydantic {@code SkillFrontmatter} model (plus its nested
 * {@code SkillMetadata}) for the skill frontmatter, ported from {@code models/skill.py}.
 *
 * <p>
 * {@link #validate} collects every field error the way pydantic does — one error per
 * failing field, in model field order — and returns the parsed value only when none were
 * produced. Error texts mirror pydantic's own wording so the {@code frontmatter_field}
 * findings read the same as upstream.
 */
final class SchemaFrontmatter {

	/**
	 * One rejected field: its dotted path, the pydantic-style message, and its error
	 * type.
	 */
	record FieldError(String field, String message, String errorType) {
	}

	private static final String FIELD_REQUIRED = "Field required";

	private static final String NOT_A_STRING = "Input should be a valid string";

	private static final String NOT_A_LIST = "Input should be a valid list";

	private static final String NOT_A_MAPPING = "Input should be a valid dictionary or instance of SkillMetadata";

	private static final String KEY_NOT_STRING = "Keys should be strings";

	/** {@code XML_TAG_RE} from models/skill.py; searched, not matched. */
	private static final Pattern XML_TAG_RE = Pattern.compile(
			"</?[A-Za-z][A-Za-z0-9_-]*(?:\\s[^<>]*)?/?>|</?[A-Za-z][A-Za-z0-9_-]*\\s[^<>]*$",
			Pattern.UNICODE_CHARACTER_CLASS);

	/**
	 * Bounds for {@code metadata.domain}, which accepts a single string or a list of
	 * strings.
	 */
	private static final int DOMAIN_MAX_ITEMS = 16;

	private static final int DOMAIN_MAX_LENGTH = 128;

	/**
	 * Field names the model knows: {@code allowed_tools} is accepted through both its
	 * alias and its name.
	 */
	private static final Set<String> MODEL_FIELDS = Set.of("name", "description", "license", "compatibility",
			"metadata", "allowed-tools", "allowed_tools");

	private final String name;

	private final String description;

	private final Map<String, Object> metadata;

	private final List<String> domain;

	private final Set<String> extras;

	private SchemaFrontmatter(String name, String description, Map<String, Object> metadata, List<String> domain,
			Set<String> extras) {
		this.name = name;
		this.description = description;
		this.metadata = metadata;
		this.domain = domain;
		this.extras = extras;
	}

	String getName() {
		return name;
	}

	String getDescription() {
		return description;
	}

	/**
	 * The raw {@code metadata} mapping, or {@code null} when absent (pydantic's nested
	 * model).
	 */
	Map<String, Object> getMetadata() {
		return metadata;
	}

	/** Normalized {@code metadata.domain} values (deduplicated, whitespace-stripped). */
	List<String> getDomain() {
		return domain;
	}

	/** Top-level keys outside the model's fields (pydantic's {@code model_extra}). */
	Set<String> getExtras() {
		return extras;
	}

	/**
	 * Validate the parsed frontmatter mapping, appending every field error to
	 * {@code errors}. Returns the parsed frontmatter, or {@code null} when anything
	 * failed.
	 */
	static SchemaFrontmatter validate(Map<String, Object> data, List<FieldError> errors) {
		List<String> forbidden = new ArrayList<>();
		for (String field : Constants.FORBIDDEN_SKILL_FIELDS) {
			if (data.containsKey(field)) {
				forbidden.add(field);
			}
		}
		if (!forbidden.isEmpty()) {
			// A mode="before" model validator short-circuits field validation entirely.
			errors.add(new FieldError("", "Value error, Forbidden fields detected: " + String.join(", ", forbidden)
					+ ". Skills should not use 'alwaysApply' or 'globs' fields.", "value_error"));
			return null;
		}

		String name = checkName(data, errors);
		String description = checkDescription(data, errors);
		checkOptionalString(data, errors, "license", 0);
		checkOptionalString(data, errors, "compatibility", Constants.COMPATIBILITY_MAX_LENGTH);

		Map<String, Object> metadata = null;
		List<String> domain = List.of();
		if (data.get("metadata") != null) {
			Object raw = data.get("metadata");
			if (raw instanceof Map<?, ?> mapping) {
				domain = checkMetadata(mapping, errors);
				metadata = asStringKeyMap(mapping);
			}
			else {
				errors.add(new FieldError("metadata", NOT_A_MAPPING, "model_attributes_type"));
			}
		}

		Object allowedTools = data.containsKey("allowed-tools") ? data.get("allowed-tools") : data.get("allowed_tools");
		if (allowedTools != null && !(allowedTools instanceof String)) {
			errors.add(new FieldError(data.containsKey("allowed-tools") ? "allowed-tools" : "allowed_tools",
					NOT_A_STRING, "string_type"));
		}

		// Extra keys are allowed, but only string keys are usable ones.
		Set<String> extras = new LinkedHashSet<>();
		for (Object key : data.keySet()) {
			if (!(key instanceof String text)) {
				errors.add(new FieldError(SchemaYaml.renderKey(key), KEY_NOT_STRING, "invalid_key"));
				continue;
			}
			if (!MODEL_FIELDS.contains(text)) {
				extras.add(text);
			}
		}

		if (!errors.isEmpty()) {
			return null;
		}
		return new SchemaFrontmatter(name, description, metadata, domain, extras);
	}

	// ------------------------------------------------------------------
	// Fields
	// ------------------------------------------------------------------

	private static String checkName(Map<String, Object> data, List<FieldError> errors) {
		if (!data.containsKey("name")) {
			errors.add(new FieldError("name", FIELD_REQUIRED, "missing"));
			return null;
		}
		Object raw = data.get("name");
		if (!(raw instanceof String value)) {
			// pydantic reports an explicit null for a required str as a type error, not a
			// missing field.
			errors.add(new FieldError("name", NOT_A_STRING, "string_type"));
			return null;
		}
		String stripped = value.strip();
		String lengthProblem = lengthProblem(stripped, Constants.NAME_MIN_LENGTH, Constants.NAME_MAX_LENGTH);
		if (lengthProblem != null) {
			errors.add(new FieldError("name", lengthProblem,
					"string_too_" + (isTooShort(stripped, Constants.NAME_MIN_LENGTH) ? "short" : "long")));
			return null;
		}
		String problem = nameFormatProblem(stripped);
		if (problem != null) {
			errors.add(new FieldError("name", "Value error, " + problem, "value_error"));
			return null;
		}
		return stripped;
	}

	private static String checkDescription(Map<String, Object> data, List<FieldError> errors) {
		if (!data.containsKey("description")) {
			errors.add(new FieldError("description", FIELD_REQUIRED, "missing"));
			return null;
		}
		Object raw = data.get("description");
		if (!(raw instanceof String value)) {
			errors.add(new FieldError("description", NOT_A_STRING, "string_type"));
			return null;
		}
		String stripped = value.strip();
		String lengthProblem = lengthProblem(stripped, Constants.DESCRIPTION_MIN_LENGTH,
				Constants.DESCRIPTION_MAX_LENGTH);
		if (lengthProblem != null) {
			errors.add(new FieldError("description", lengthProblem,
					"string_too_" + (isTooShort(stripped, Constants.DESCRIPTION_MIN_LENGTH) ? "short" : "long")));
			return null;
		}
		if (XML_TAG_RE.matcher(stripped).find()) {
			errors.add(new FieldError("description", "Value error, Skill description must not contain XML tags",
					"value_error"));
			return null;
		}
		return stripped;
	}

	private static void checkOptionalString(Map<String, Object> data, List<FieldError> errors, String field,
			int maxLength) {
		Object raw = data.get(field);
		if (raw == null) {
			return;
		}
		if (!(raw instanceof String value)) {
			errors.add(new FieldError(field, NOT_A_STRING, "string_type"));
			return;
		}
		String stripped = value.strip();
		String lengthProblem = lengthProblem(stripped, 0, maxLength);
		if (lengthProblem != null) {
			errors.add(new FieldError(field, lengthProblem, "string_too_long"));
		}
	}

	/**
	 * Normalizes and validates the nested {@code metadata} mapping; returns the domain
	 * list.
	 */
	private static List<String> checkMetadata(Map<?, ?> metadata, List<FieldError> errors) {
		Object author = metadata.get("author");
		if (author != null && !(author instanceof String)) {
			errors.add(new FieldError("metadata.author", NOT_A_STRING, "string_type"));
		}
		for (String field : List.of("tags", "languages", "frameworks")) {
			Object raw = metadata.get(field);
			if (raw == null || raw instanceof String || raw instanceof List) {
				continue;
			}
			errors.add(new FieldError("metadata." + field, NOT_A_LIST, "list_type"));
		}

		List<String> domain = List.of();
		if (metadata.containsKey("domain")) {
			List<String> normalized = new ArrayList<>();
			String problem = normalizeDomain(metadata.get("domain"), normalized);
			if (problem != null) {
				errors.add(new FieldError("metadata.domain", "Value error, " + problem, "value_error"));
			}
			else {
				domain = normalized;
			}
		}

		for (Object key : metadata.keySet()) {
			if (!(key instanceof String)) {
				errors.add(new FieldError("metadata." + SchemaYaml.renderKey(key), KEY_NOT_STRING, "invalid_key"));
			}
		}
		return domain;
	}

	/**
	 * Port of {@code SkillMetadata.normalize_domain}; returns the rejection reason, or
	 * {@code null}.
	 */
	private static String normalizeDomain(Object raw, List<String> out) {
		List<?> values;
		if (raw == null) {
			return null;
		}
		if (raw instanceof String text) {
			if (text.strip().isEmpty()) {
				return null; // a blank scalar such as `domain: ""` means "no domain"
			}
			values = List.of(text);
		}
		else if (raw instanceof List<?> list) {
			values = list;
		}
		else {
			return "domain must be a string or list of strings";
		}
		if (values.size() > DOMAIN_MAX_ITEMS) {
			return "domain must contain at most " + DOMAIN_MAX_ITEMS + " values";
		}
		Set<String> seen = new LinkedHashSet<>();
		for (Object item : values) {
			if (!(item instanceof String itemText)) {
				return "domain values must be strings";
			}
			String value = itemText.strip();
			if (value.isEmpty()) {
				return "domain values must not be blank";
			}
			if (codePoints(value) > DOMAIN_MAX_LENGTH) {
				return "domain values must be at most " + DOMAIN_MAX_LENGTH + " characters";
			}
			if (!seen.contains(value)) {
				seen.add(value);
				out.add(value);
			}
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Constraint helpers
	// ------------------------------------------------------------------

	/** Port of {@code SkillFrontmatter.validate_name_format}. */
	private static String nameFormatProblem(String value) {
		if (!Constants.KEBAB_CASE.matcher(value).lookingAt()) {
			return "Skill name '" + value + "' must be in kebab-case format "
					+ "(lowercase letters, numbers, and hyphens only, starting with a letter)";
		}
		if (value.contains("--")) {
			return "Skill name '" + value + "' cannot contain consecutive hyphens";
		}
		if (value.endsWith("-")) {
			return "Skill name '" + value + "' cannot end with a hyphen";
		}
		for (String segment : value.toLowerCase(java.util.Locale.ROOT).split("-")) {
			if (Constants.RESERVED_SKILL_NAMES.contains(segment)) {
				return "Skill name '" + value + "' must not contain reserved words " + reservedWordsLiteral();
			}
		}
		if (XML_TAG_RE.matcher(value).find()) {
			return "Skill name '" + value + "' must not contain XML tags";
		}
		return null;
	}

	/**
	 * Python renders the {@code RESERVED_SKILL_NAMES} set with its literal braces.
	 * Deviation: a Python set's iteration order is hash-based and not stable across runs;
	 * this renders one fixed order.
	 */
	private static String reservedWordsLiteral() {
		List<String> names = new ArrayList<>(Constants.RESERVED_SKILL_NAMES);
		java.util.Collections.sort(names);
		StringBuilder sb = new StringBuilder("{");
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append('\'').append(names.get(i)).append('\'');
		}
		return sb.append('}').toString();
	}

	/** pydantic's min/max length message, or {@code null} when the value fits. */
	private static String lengthProblem(String value, int minLength, int maxLength) {
		int length = codePoints(value);
		if (length < minLength) {
			return "String should have at least " + minLength + " characters";
		}
		if (maxLength > 0 && length > maxLength) {
			return "String should have at most " + maxLength + " characters";
		}
		return null;
	}

	private static boolean isTooShort(String value, int minLength) {
		return codePoints(value) < minLength;
	}

	/** Python's {@code len(str)}: code points, not UTF-16 units. */
	private static int codePoints(String value) {
		return value.codePointCount(0, value.length());
	}

	private static Map<String, Object> asStringKeyMap(Map<?, ?> raw) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : raw.entrySet()) {
			out.put(SchemaYaml.renderKey(entry.getKey()), entry.getValue());
		}
		return out;
	}

}
