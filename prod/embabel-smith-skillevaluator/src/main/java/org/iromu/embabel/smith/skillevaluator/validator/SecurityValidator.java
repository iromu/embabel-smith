/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.nio.file.Path;

import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.spi.ScannerRegistry;
import org.iromu.embabel.smith.skillevaluator.spi.SecurityScanner;
import org.iromu.embabel.smith.skillevaluator.spi.SkillspectorSecurityScanner;

/**
 * The {@code security} Tier 1 check: display name {@code Security Scan}, description
 * {@code Detect security vulnerabilities and PII/secrets} (upstream
 * {@code tier1/commands.py} pairs that name with
 * {@code SecurityValidator.validate_security_only} and the class's own
 * {@code description} property).
 * <p>
 * This class is the folder-or-skill driver plus a seam. The vulnerability scan itself
 * belongs to a {@link SecurityScanner}: {@link #SecurityValidator()} takes whatever
 * {@link ScannerRegistry} publishes and otherwise the built-in
 * {@link SkillspectorSecurityScanner} shell-out, while
 * {@link #SecurityValidator(SecurityScanner)} injects one directly, which is how a test —
 * or an embedding application with its own scanner — replaces the CLI.
 * <h2>Relationship to upstream</h2>
 * <p>
 * Upstream's {@code SecurityValidator} is one class doing three jobs: the skillspector
 * CLI stage, the deterministic PII scan, and a large
 * normalization/baseline/content-type-scoping/triage layer over the scanner's report.
 * This port splits them: the PII half lives in {@link PiiScanner} (the {@code pii}
 * check), and the skillspector half lives behind the SPI, because the engine is exactly
 * the part an integrator is most likely to want to swap. What upstream's
 * {@code validate()} does — both stages plus LLM verification — is not reproduced here:
 * the two checks are separate validators, matching the two Tier 1 commands that run them.
 * <p>
 * Fail-fast is not exempted for any check here. Upstream exempts only its
 * {@code skillspector_hook_unverified} gate, a report-shape check the minimal engine does
 * not produce; see {@link SkillspectorSecurityScanner} for what the engine leaves out.
 *
 * @author Ivan Rodriguez
 */
public class SecurityValidator extends ValidatorBase {

	private final SecurityScanner scanner;

	/** ServiceLoader-registered engine, else the built-in skillspector CLI. */
	public SecurityValidator() {
		this(ScannerRegistry.security().load(new SkillspectorSecurityScanner()));
	}

	/**
	 * Use {@code scanner} for every skill instead of discovering an engine.
	 * @param scanner the engine to scan every skill with
	 */
	public SecurityValidator(SecurityScanner scanner) {
		this.scanner = scanner;
	}

	/**
	 * The engine this validator scans with.
	 * @return the scanner this instance was built with
	 */
	public SecurityScanner getScanner() {
		return this.scanner;
	}

	@Override
	public String getName() {
		return "Security Scan";
	}

	@Override
	public String getDescription() {
		return "Detect security vulnerabilities and PII/secrets";
	}

	/**
	 * Port of {@code validate_security_only}: the scanner's vulnerability stage over a
	 * skill directory or every skill in a folder, with no PII detection.
	 * @param skillPath a skill directory, or a folder holding several skills
	 * @return the scanner's findings, with this validator's name and description recorded
	 * on the result
	 */
	public ValidationResult validateSecurityOnly(Path skillPath) {
		ValidationResult result = validateFolderOrSkill(skillPath, this::scanSecurity, "Security scanning");
		result.setValidatorName(getName());
		result.setValidatorDescription(getDescription());
		return result;
	}

	/**
	 * Upstream's {@code validate} runs the scanner and the PII scan together; here the
	 * {@code security} command maps to {@code validate_security_only}, so this entry
	 * point does the same and {@link PiiScanner} covers the other half.
	 */
	@Override
	public ValidationResult validate(Path skillPath) {
		return validateSecurityOnly(skillPath);
	}

	/**
	 * One skill's engine result, unchanged: the engine owns its own warnings and
	 * completeness.
	 * @param skillPath the single skill to scan
	 * @return the engine's result, with nothing added or removed
	 */
	ValidationResult scanSecurity(Path skillPath) {
		return this.scanner.scanSecurity(skillPath);
	}

}
