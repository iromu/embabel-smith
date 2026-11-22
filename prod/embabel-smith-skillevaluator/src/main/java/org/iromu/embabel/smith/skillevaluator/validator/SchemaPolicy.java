/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The severity and identity policy a validator applies, ported from the subset of
 * {@code validators/policy.py} that the schema check consumes.
 * <p>
 * Deviation: upstream resolves the active policy from CLI flags and the
 * {@code SKILLEVALUATOR_PROFILE} environment variable and can overlay custom policy
 * files. No policy plumbing exists in the Java port yet, so this class loads only the
 * bundled {@code default} profile from the classpath (with the same in-memory fallback
 * the Python loader uses when the YAML is missing). Severity overrides therefore only
 * ever carry the default profile's values.
 *
 * @author Ivan Rodriguez
 */
final class SchemaPolicy {

	static final String DEFAULT_PROFILE_NAME = "default";

	/**
	 * Bundled profile YAML, copied from
	 * {@code src/skillevaluator/config/profiles/default.yaml}.
	 */
	private static final String DEFAULT_PROFILE_RESOURCE = "/org/iromu/embabel/smith/skillevaluator/config/profiles/default.yaml";

	/** Stable, profile-agnostic email-shape pattern: {@code "Name <local@domain>"}. */
	static final Pattern AUTHOR_SHAPE_PATTERN = Pattern.compile("\\S[^<>\n]* <[^<>@\\s]+@[^<>\\s]+>",
			Pattern.UNICODE_CHARACTER_CLASS);

	static final String DEFAULT_METADATA_ID_REQUIREMENT = "ignore";

	private final String profile;

	private final Pattern authorEmailRegex;

	private final Map<String, Severity> severityOverrides;

	private final String metadataIdRequirement;

	SchemaPolicy(String profile, Pattern authorEmailRegex, Map<String, Severity> severityOverrides,
			String metadataIdRequirement) {
		this.profile = profile;
		this.authorEmailRegex = authorEmailRegex;
		this.severityOverrides = Map.copyOf(severityOverrides);
		this.metadataIdRequirement = metadataIdRequirement;
	}

	/**
	 * The default policy for both public and private repositories: the bundled
	 * {@code default} profile, falling back to the in-memory default when the resource
	 * cannot be read or parsed.
	 * @return the loaded {@code default} profile, or the in-memory fallback when loading
	 * it failed
	 */
	static SchemaPolicy defaultPolicy() {
		SchemaPolicy loaded = loadProfile(DEFAULT_PROFILE_NAME);
		return (loaded != null) ? loaded : fallback();
	}

	private static SchemaPolicy fallback() {
		Map<String, Severity> overrides = new LinkedHashMap<>();
		overrides.put("SCHEMA.author_missing", Severity.LOW);
		overrides.put("SCHEMA.author_without_email", Severity.INFO);
		overrides.put("SCHEMA.author_format", Severity.HIGH);
		overrides.put("WORKFLOWS.author_format", Severity.HIGH);
		overrides.put("LICENSE.*", Severity.CRITICAL);
		overrides.put("LICENSE.frontmatter_license_mismatch", Severity.CRITICAL);
		return new SchemaPolicy(DEFAULT_PROFILE_NAME, null, overrides, DEFAULT_METADATA_ID_REQUIREMENT);
	}

	/**
	 * Load a bundled profile YAML, or {@code null} when it is absent or unreadable.
	 * @param name profile name, looked up as {@code <name>.yaml} under the bundled
	 * profiles directory
	 * @return the parsed policy, or {@code null} when the resource is missing, is not a
	 * mapping, or fails to load
	 */
	private static SchemaPolicy loadProfile(String name) {
		try (InputStream in = SchemaPolicy.class
			.getResourceAsStream("/org/skillevaluator/config/profiles/" + name + ".yaml")) {
			if (in == null) {
				return null;
			}
			LoaderOptions options = new LoaderOptions();
			Object data = new Yaml(new SafeConstructor(options)).load(in);
			if (!(data instanceof Map<?, ?> root)) {
				return null;
			}
			return fromData(root, name);
		}
		catch (Exception ex) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static SchemaPolicy fromData(Map<?, ?> data, String fallbackProfile) {
		String profileName = String.valueOf(orDefault(data.get("profile"), fallbackProfile));

		Object identityRaw = data.get("identity");
		Map<String, Object> identity = (identityRaw instanceof Map<?, ?> m) ? (Map<String, Object>) m : Map.of();

		Pattern emailRegex = null;
		Object emailValue = identity.get("author_email_regex");
		if (emailValue instanceof String pattern && !pattern.isEmpty()) {
			try {
				emailRegex = Pattern.compile(pattern);
			}
			catch (RuntimeException ignored) {
				// Mirrors _coerce_email_regex: an invalid pattern disables the domain
				// check.
				emailRegex = null;
			}
		}
		String requirement = (identity.get("metadata_id_requirement") instanceof String s) ? s : null;

		Map<String, Severity> overrides = new LinkedHashMap<>();
		if (data.get("severity_overrides") instanceof Map<?, ?> rawOverrides) {
			for (Map.Entry<?, ?> entry : rawOverrides.entrySet()) {
				String key = String.valueOf(entry.getKey());
				if (!key.contains(".") || entry.getValue() == null) {
					continue;
				}
				overrides.put(key, Severity.fromString(String.valueOf(entry.getValue())));
			}
		}
		return new SchemaPolicy(profileName, emailRegex, overrides,
				(requirement != null) ? requirement : DEFAULT_METADATA_ID_REQUIREMENT);
	}

	private static Object orDefault(Object value, Object fallbackValue) {
		return (value == null || String.valueOf(value).isEmpty()) ? fallbackValue : value;
	}

	String getProfile() {
		return this.profile;
	}

	Pattern getAuthorEmailRegex() {
		return this.authorEmailRegex;
	}

	String getMetadataIdRequirement() {
		return this.metadataIdRequirement;
	}

	/**
	 * Return the severity for {@code CATEGORY.check_name} or {@code CATEGORY.*}, else
	 * {@code default}.
	 * @param category finding category, such as {@code SCHEMA}
	 * @param checkName check identifier, such as {@code author_format}
	 * @param defaultValue severity to use when the profile overrides neither the check
	 * nor its category
	 * @return the exact-check override, else the category wildcard override, else
	 * {@code defaultValue}
	 */
	Severity severityFor(String category, String checkName, Severity defaultValue) {
		Severity exact = this.severityOverrides.get(category + "." + checkName);
		if (exact != null) {
			return exact;
		}
		Severity wildcard = this.severityOverrides.get(category + ".*");
		return (wildcard != null) ? wildcard : defaultValue;
	}

	/**
	 * Always true when no domain regex is configured; independent of the shape check.
	 * @param author the author value to test against the configured domain regex
	 * @return {@code true} when no domain regex is set, or it matches somewhere in the
	 * value
	 */
	boolean isAuthorEmailAcceptable(String author) {
		return this.authorEmailRegex == null || this.authorEmailRegex.matcher(author).find();
	}

	/** Profile name in lower case, for diagnostics only. */
	@Override
	public String toString() {
		return "SchemaPolicy[" + this.profile.toLowerCase(Locale.ROOT) + "]";
	}

}
