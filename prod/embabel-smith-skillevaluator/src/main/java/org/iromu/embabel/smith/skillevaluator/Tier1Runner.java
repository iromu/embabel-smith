/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.spi.SecretsScanner;
import org.iromu.embabel.smith.skillevaluator.spi.SecurityScanner;
import org.iromu.embabel.smith.skillevaluator.util.Constants;
import org.iromu.embabel.smith.skillevaluator.validator.CodeRiskValidator;
import org.iromu.embabel.smith.skillevaluator.validator.HygieneValidator;
import org.iromu.embabel.smith.skillevaluator.validator.LicenseValidator;
import org.iromu.embabel.smith.skillevaluator.validator.PiiScanner;
import org.iromu.embabel.smith.skillevaluator.validator.QualityScoreValidator;
import org.iromu.embabel.smith.skillevaluator.validator.SchemaValidator;
import org.iromu.embabel.smith.skillevaluator.validator.ScriptLintValidator;
import org.iromu.embabel.smith.skillevaluator.validator.SecretsValidator;
import org.iromu.embabel.smith.skillevaluator.validator.SecurityValidator;
import org.iromu.embabel.smith.skillevaluator.validator.UnicodeSmuggleValidator;
import org.iromu.embabel.smith.skillevaluator.validator.ValidatorBase;
import org.iromu.embabel.smith.skillevaluator.validator.VersionValidator;

/**
 * Tier 1 validation pipeline: runs the selected static checks over a skill (or a folder
 * of skills) and returns one {@link ValidationResult} per validator, in canonical
 * pipeline order.
 * <p>
 * Port of {@code tier1/commands.py#run_validation}, scoped to skill content. Deviations
 * from upstream, by design:
 * <ul>
 * <li>{@code harness} (advisory, profile-driven) and {@code dependency} (network CVE
 * audit) checks are not implemented; requesting them is an unknown-check error.</li>
 * <li>Publication evidence stamping ({@code stamp_publication_evidence} and the NVIDIA
 * Verified Skills lineup contract) is upstream CI plumbing and is not ported.</li>
 * <li>Content-type auto-detection (rules/workflows/plugins) is not ported; non-skill
 * targets simply produce no skill findings.</li>
 * </ul>
 * <p>
 * The {@code security} and {@code secrets} checks delegate to pluggable scanner engines
 * ({@link SecurityScanner} / {@link SecretsScanner}); the built-in defaults shell out to
 * the same external CLIs upstream uses and mark their scan incomplete when the tool is
 * missing.
 *
 * @author Ivan Rodriguez
 */
public final class Tier1Runner {

	/** Canonical check names run by default, in pipeline order. */
	public static final List<String> DEFAULT_CHECKS = List.of("schema", "version", "security", "pii", "license",
			"code-integrity", "unicode", "quality", "lint");

	/** Every canonical check name {@link #run} understands. */
	public static final Set<String> RECOGNIZED_CHECKS = Set.copyOf(DEFAULT_CHECKS);

	/**
	 * Names accepted by {@link Builder#skip} (the code-integrity secrets scan is
	 * skippable alone).
	 */
	public static final Set<String> SKIPPABLE_CHECKS = Set.copyOf(
			java.util.stream.Stream.concat(DEFAULT_CHECKS.stream(), java.util.stream.Stream.of("secrets")).toList());

	/** Description recorded on a check the caller asked to skip. */
	public static final String SKIP_REASON = "skipped with --skip";

	private static final Map<String, String> CHECK_ALIASES = Map.of("code", "code-integrity", "code-risk",
			"code-integrity", "scripts", "lint", "script-lint", "lint", "licence", "license", "license-check",
			"license");

	/**
	 * Validator names recorded for a skipped check, matching what its run would produce.
	 */
	private static final Map<String, List<String>> SKIPPED_VALIDATOR_NAMES = Map.of("schema",
			List.of("Schema & Repository Governance"), "version", List.of("Semantic Version Validation"), "security",
			List.of("Security Scan"), "pii", List.of("PII Scan"), "license", List.of("License Compliance"),
			"code-integrity", List.of("Code Risk Analysis", "Secrets Detection", "Code Integrity & Hygiene"), "unicode",
			List.of("Unicode Smuggling Detection"), "quality", List.of("Quality Score (4-Dimension Analysis)"), "lint",
			List.of("Script Lint (Advisory)"));

	public static Builder builder() {
		return new Builder();
	}

	private static Set<String> parse(String... names) {
		Set<String> out = new LinkedHashSet<>();
		for (String raw : names) {
			for (String part : raw.split(",")) {
				String name = part.trim().toLowerCase(Locale.ROOT);
				if (!name.isEmpty()) {
					out.add(CHECK_ALIASES.getOrDefault(name, name));
				}
			}
		}
		return out;
	}

	/**
	 * Resolve skip names (aliases included) to canonical check names.
	 * @param skip comma-separated check names, or {@code null} for none
	 * @return the canonical names to skip, empty when {@code skip} is blank
	 */
	public static Set<String> skippedChecks(String skip) {
		return (skip == null || skip.isBlank()) ? Set.of() : parse(skip);
	}

	/**
	 * Resolve a comma-separated check list to canonical names; empty input yields the
	 * default lineup.
	 * @param checks comma-separated check names, or {@code null} for the defaults
	 * @return the canonical names to run, in pipeline order
	 */
	public static Set<String> enabledChecks(String checks) {
		return (checks == null || checks.isBlank()) ? new LinkedHashSet<>(DEFAULT_CHECKS) : parse(checks);
	}

	/**
	 * Run the selected Tier 1 checks over {@code targetPath}.
	 * @param targetPath the skill file or folder of skills to validate
	 * @param options the run configuration from {@link #builder()}
	 * @return one result per validator (skipped checks included as skipped results), in
	 * canonical pipeline order
	 */
	public List<ValidationResult> run(Path targetPath, Options options) {
		Set<String> enabled = options.checks.isEmpty() ? new LinkedHashSet<>(DEFAULT_CHECKS) : options.checks;
		Set<String> skipped = options.skip;
		List<ValidationResult> results = new ArrayList<>();

		record Step(String check, Supplier<List<ValidationResult>> build) {
		}
		List<Step> steps = new ArrayList<>();
		steps.add(new Step("schema", () -> {
			SchemaValidator validator = new SchemaValidator();
			return List.of(asResult(validator.validate(targetPath), validator));
		}));
		steps.add(new Step("version",
				() -> List.of(named(new VersionValidator(options.previousVersion).validate(targetPath),
						"Semantic Version Validation",
						"Validate optional metadata.version labels and require strict bumps"))));
		steps.add(new Step("security", () -> {
			SecurityValidator validator = (options.securityScanner == null) ? new SecurityValidator()
					: new SecurityValidator(options.securityScanner);
			return List
				.of(named(validator.validateSecurityOnly(targetPath), "Security Scan", validator.getDescription()));
		}));
		steps.add(new Step("pii", () -> List
			.of(named(new PiiScanner().validate(targetPath), "PII Scan", "Detect PII and local identifiers"))));
		steps.add(new Step("license", () -> {
			LicenseValidator validator = new LicenseValidator();
			return List.of(asResult(validator.validate(targetPath), validator));
		}));
		steps.add(new Step("code-integrity", () -> {
			List<ValidationResult> out = new ArrayList<>();
			CodeRiskValidator codeRisk = new CodeRiskValidator();
			out.add(asResult(codeRisk.validate(targetPath), codeRisk));
			if (skipped.contains("secrets")) {
				out.add(skippedResult("Secrets Detection"));
			}
			else {
				SecretsValidator secrets = (options.secretsScanner == null) ? new SecretsValidator()
						: new SecretsValidator(options.secretsScanner);
				out.add(asResult(secrets.validate(targetPath), secrets));
			}
			HygieneValidator hygiene = new HygieneValidator();
			out.add(asResult(hygiene.validate(targetPath), hygiene));
			return out;
		}));
		steps.add(new Step("unicode", () -> {
			UnicodeSmuggleValidator validator = new UnicodeSmuggleValidator();
			return List.of(asResult(validator.validate(targetPath), validator));
		}));
		steps.add(new Step("quality", () -> {
			QualityScoreValidator validator = new QualityScoreValidator(options.minScore);
			return List.of(asResult(validator.validate(targetPath), validator));
		}));
		steps.add(new Step("lint", () -> {
			ScriptLintValidator validator = new ScriptLintValidator();
			return List.of(asResult(validator.validate(targetPath), validator));
		}));

		List<Step> active = new ArrayList<>();
		for (Step step : steps) {
			if (enabled.contains(step.check())) {
				active.add(step);
			}
		}
		for (Step step : new ArrayList<>(active)) {
			if (skipped.contains(step.check())) {
				active.remove(step);
				for (String name : SKIPPED_VALIDATOR_NAMES.getOrDefault(step.check(), List.of(step.check()))) {
					results.add(skippedResult(name));
				}
			}
		}

		Set<String> unknown = new LinkedHashSet<>(enabled);
		unknown.removeAll(RECOGNIZED_CHECKS);
		Set<String> unskippable = new LinkedHashSet<>(skipped);
		unskippable.removeAll(SKIPPABLE_CHECKS);
		unknown.addAll(unskippable);
		if (!unknown.isEmpty()) {
			ValidationResult optionResult = new ValidationResult();
			optionResult.setValidatorName("Tier 1 option validation");
			optionResult.setValidatorDescription("Validate requested check names");
			optionResult.addError("Unknown Tier 1 check(s): " + String.join(", ", new java.util.TreeSet<>(unknown)));
			results.add(0, optionResult);
		}

		try (AutoCloseable scope = ValidatorBase.continueOnFailureScope(options.continueOnFailure)) {
			for (Step step : active) {
				List<ValidationResult> stepResults = step.build().get();
				results.addAll(stepResults);
				if (options.failFast && !options.continueOnFailure && results.stream().anyMatch((r) -> !r.isPassed())) {
					return results;
				}
			}
		}
		catch (RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException("continue-on-failure scope failed to close", ex);
		}
		return results;
	}

	/**
	 * Convenience: run the default lineup with default options.
	 * @param targetPath the skill file or folder of skills to validate
	 * @return one result per validator, in canonical pipeline order
	 */
	public List<ValidationResult> run(Path targetPath) {
		return run(targetPath, builder().build());
	}

	private static ValidationResult asResult(ValidationResult result, ValidatorBase validator) {
		return named(result, validator.getName(), validator.getDescription());
	}

	private static ValidationResult named(ValidationResult result, String name, String description) {
		if (result.getValidatorName() == null || result.getValidatorName().isEmpty()) {
			result.setValidatorName(name);
		}
		if (result.getValidatorDescription() == null || result.getValidatorDescription().isEmpty()) {
			result.setValidatorDescription(description);
		}
		return result;
	}

	private static ValidationResult skippedResult(String validatorName) {
		ValidationResult result = new ValidationResult();
		result.setValidatorName(validatorName);
		result.setValidatorDescription(SKIP_REASON);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("skipped", true);
		metadata.put("skipped_by_user", true);
		metadata.put("skip_reason", SKIP_REASON);
		result.getMetadata().putAll(metadata);
		return result;
	}

	/** Immutable run configuration; obtain via {@link #builder()}. */
	public static final class Options {

		private final Set<String> checks;

		private final Set<String> skip;

		private final int minScore;

		private final String previousVersion;

		private final boolean failFast;

		private final boolean continueOnFailure;

		private final SecretsScanner secretsScanner;

		private final SecurityScanner securityScanner;

		private Options(Builder b) {
			this.checks = b.checks;
			this.skip = b.skip;
			this.minScore = b.minScore;
			this.previousVersion = b.previousVersion;
			this.failFast = b.failFast;
			this.continueOnFailure = b.continueOnFailure;
			this.secretsScanner = b.secretsScanner;
			this.securityScanner = b.securityScanner;
		}

		public Set<String> getChecks() {
			return this.checks;
		}

		public int getMinScore() {
			return this.minScore;
		}

		public boolean isFailFast() {
			return this.failFast;
		}

	}

	public static final class Builder {

		private Set<String> checks = Set.of();

		private Set<String> skip = Set.of();

		private int minScore = Constants.QUALITY_DEFAULT_MIN_SCORE;

		private String previousVersion;

		private boolean failFast;

		private boolean continueOnFailure;

		private SecretsScanner secretsScanner;

		private SecurityScanner securityScanner;

		/**
		 * Restrict the run to these check names (aliases accepted). Empty means the
		 * default lineup.
		 * @param names check names, comma-separated or as separate arguments
		 * @return this builder
		 */
		public Builder checks(String... names) {
			this.checks = parse(names);
			return this;
		}

		/**
		 * Skip these checks (aliases accepted; {@code secrets} alone skips only the
		 * Gitleaks scan).
		 * @param names check names, comma-separated or as separate arguments
		 * @return this builder
		 */
		public Builder skip(String... names) {
			this.skip = parse(names);
			return this;
		}

		public Builder minScore(int value) {
			this.minScore = value;
			return this;
		}

		public Builder previousVersion(String value) {
			this.previousVersion = value;
			return this;
		}

		public Builder failFast(boolean value) {
			this.failFast = value;
			return this;
		}

		/**
		 * Scan every skill past a CRITICAL finding instead of stopping the folder early.
		 * @param value {@code true} to keep scanning after a CRITICAL finding
		 * @return this builder
		 */
		public Builder continueOnFailure(boolean value) {
			this.continueOnFailure = value;
			return this;
		}

		public Builder secretsScanner(SecretsScanner value) {
			this.secretsScanner = value;
			return this;
		}

		public Builder securityScanner(SecurityScanner value) {
			this.securityScanner = value;
			return this;
		}

		public Options build() {
			return new Options(this);
		}

	}

}
