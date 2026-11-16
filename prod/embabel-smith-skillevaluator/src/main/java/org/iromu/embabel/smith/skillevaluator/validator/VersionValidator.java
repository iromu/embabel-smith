/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Frontmatter;

/**
 * Semantic version validation for skills. Port of {@code validators/version.py}.
 *
 * <p>
 * The previous-version bound is supplied via the {@code previousVersion} constructor
 * argument or the {@value #PREVIOUS_VERSION_ENV} environment variable.
 */
public class VersionValidator extends ValidatorBase {

	/** Environment variable consulted when no explicit previous version is supplied. */
	public static final String PREVIOUS_VERSION_ENV = "SKILLEVALUATOR_PREVIOUS_VERSION";

	/**
	 * Port of {@code models/skill.py}: {@code _SEMVER_COMPONENT} and the compiled
	 * {@code SEMVER_RE}. The Python pattern is compiled with {@code re.ASCII}, so the
	 * {@code [0-9]} classes stay ASCII-only and non-ASCII digits such as U+FF11 fail.
	 * Kept private to this class by design: the schema helpers own their own copies.
	 */
	private static final String SEMVER_COMPONENT = "(?:0|[1-9][0-9]{0,8})";

	private static final Pattern SEMVER_RE = Pattern
		.compile("^" + SEMVER_COMPONENT + "\\." + SEMVER_COMPONENT + "\\." + SEMVER_COMPONENT + "$");

	private final String previousVersion;

	/**
	 * @param previousVersion the bound to require a strict bump against; {@code null}
	 * falls back to {@value #PREVIOUS_VERSION_ENV}
	 */
	public VersionValidator(String previousVersion) {
		this.previousVersion = previousVersion != null ? previousVersion : System.getenv(PREVIOUS_VERSION_ENV);
	}

	public VersionValidator() {
		this(null);
	}

	/** The bound this validator will compare against, or {@code null} when unset. */
	public String getPreviousVersion() {
		return previousVersion;
	}

	@Override
	public String getName() {
		return "Semantic Version Validation";
	}

	@Override
	public String getDescription() {
		return "Validate optional metadata.version labels and require strict bumps";
	}

	@Override
	public ValidationResult validate(Path skillPath) {
		return validateFolderOrSkill(skillPath, this::validateSingleSkill, "Validating semantic versions for", false,
				false);
	}

	// --- internals ---

	/**
	 * Return {@code metadata.version} only.
	 *
	 * <p>
	 * The legacy top-level {@code version} field is intentionally ignored: prior to the
	 * optional-version proposal, {@code quality_score.py} recommended a top-level
	 * {@code version} without enforcing strict semver, so existing skills may carry
	 * values like {@code "1.0"} or {@code 1} that would now produce HIGH findings.
	 * Scoping extraction to {@code metadata.version} keeps the label itself optional and
	 * avoids breaking those skills when the validator runs by default.
	 */
	private static String extractVersion(Map<String, Object> frontmatter) {
		Object metadata = frontmatter.get("metadata");
		if (metadata instanceof Map<?, ?> map && map.containsKey("version")) {
			Object rawValue = map.get("version");
			if (rawValue == null) {
				return "";
			}
			String value = pythonStr(rawValue).strip();
			if (!value.isEmpty()) {
				return value;
			}
		}
		return "";
	}

	/**
	 * Render a YAML scalar the way Python's {@code str()} would for the values a
	 * frontmatter can carry. Numbers keep their plain form, so an unquoted {@code 1.0}
	 * still stringifies without a patch component and fails strict semver.
	 */
	private static String pythonStr(Object value) {
		if (value instanceof Boolean bool) {
			return bool ? "True" : "False";
		}
		if (value instanceof Double || value instanceof Float) {
			double d = ((Number) value).doubleValue();
			if (d == Math.rint(d) && !Double.isInfinite(d)) {
				// Python repr of a whole float keeps ".0"; the semver check rejects it
				// either way.
				return Double.toString(d);
			}
			return Double.toString(d);
		}
		return String.valueOf(value);
	}

	/** Parse a prevalidated version without allowing conversion failures to escape. */
	private static int[] versionTuple(String version) {
		String[] parts = version.split("\\.", -1);
		if (parts.length != 3) {
			return null;
		}
		try {
			return new int[] { Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()),
					Integer.parseInt(parts[2].strip()) };
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean matchesSemver(String value) {
		return SEMVER_RE.matcher(value).matches();
	}

	/**
	 * Validate the optional previous version bound.
	 * @return {@code true} when validation can continue
	 */
	private boolean validatePreviousVersion(String current, Path manifest, ValidationResult result) {
		if (previousVersion == null || previousVersion.isEmpty()) {
			return true;
		}

		String previous = previousVersion.strip();
		if (!matchesSemver(previous)) {
			result.addFinding(new Finding("VERSION", Severity.HIGH, "previous_version_semver",
					"Previous version '" + previous + "' is not strict semantic version x.y.z", manifest.toString(),
					null, null, "Pass --previous-version as major.minor.patch", null));
			return false;
		}

		if (current.isEmpty()) {
			result.addFinding(new Finding("VERSION", Severity.HIGH, "version_missing",
					"metadata.version is missing even though a previous version " + "('" + previous + "') was supplied",
					manifest.toString(), null, null, "Keep metadata.version and bump major, minor, or patch", null));
			return false;
		}

		int[] currentTuple = versionTuple(current);
		int[] previousTuple = versionTuple(previous);
		if (currentTuple == null || previousTuple == null) {
			String checkName = currentTuple == null ? "version_semver" : "previous_version_semver";
			String invalidValue = currentTuple == null ? current : previous;
			result.addFinding(new Finding("VERSION", Severity.HIGH, checkName,
					"Version '" + invalidValue + "' could not be parsed as strict semantic version x.y.z",
					manifest.toString(), null, null,
					"Use bounded ASCII major.minor.patch components without leading zeroes", null));
			return false;
		}

		if (compareTuples(currentTuple, previousTuple) <= 0) {
			result.addFinding(new Finding("VERSION", Severity.HIGH, "version_monotonic",
					"Version '" + current + "' must be greater than previous '" + previous + "'", manifest.toString(),
					null, null, "Bump major, minor, or patch before submitting the change; "
							+ "reusing the previous version label is not allowed",
					null));
			return false;
		}

		return true;
	}

	/** Python tuple comparison: lexicographic over (major, minor, patch). */
	private static int compareTuples(int[] left, int[] right) {
		for (int i = 0; i < left.length; i++) {
			int cmp = Integer.compare(left[i], right[i]);
			if (cmp != 0) {
				return cmp;
			}
		}
		return 0;
	}

	/** Validate one skill's optional {@code metadata.version} label. */
	private ValidationResult validateSingleSkill(Path skillPath) {
		ValidationResult result = new ValidationResult();
		result.setValidatorName(getName());
		result.setValidatorDescription(getDescription());

		Path manifest = findSkillManifest(skillPath);
		if (manifest == null) {
			result.addError("No SKILL.md found in " + skillPath);
			return result;
		}

		Frontmatter.FrontmatterOutcome outcome = Frontmatter.parse(manifest);
		result.merge(outcome.result());
		if (outcome.parsed() == null || outcome.parsed().yamlData() == null) {
			return result;
		}

		String current = extractVersion(outcome.parsed().yamlData());
		if (current.isEmpty()) {
			if (!validatePreviousVersion(current, manifest, result)) {
				return result;
			}
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("previous_version", previousVersion);
			result.addSuccess("version_optional",
					"No semantic version label present; resource will use commit-hash history", metadata);
			return result;
		}

		if (!matchesSemver(current)) {
			result.addFinding(new Finding("VERSION", Severity.HIGH, "version_semver",
					"Version '" + current + "' is not strict semantic version x.y.z", manifest.toString(), null, null,
					"Use a quoted string in major.minor.patch form, e.g. "
							+ "version: \"1.2.3\" (unquoted YAML values like 1.0 are "
							+ "parsed as numbers and stringified without a patch component)",
					null));
			return result;
		}

		if (!validatePreviousVersion(current, manifest, result)) {
			return result;
		}

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("version", current);
		metadata.put("previous_version", previousVersion);
		result.addSuccess("version_semver", "Valid semantic version: " + current, metadata);
		return result;
	}

}
