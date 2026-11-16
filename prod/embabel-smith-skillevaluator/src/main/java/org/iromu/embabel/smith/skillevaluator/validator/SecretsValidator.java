/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.nio.file.Path;
import java.util.Objects;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.spi.GitleaksSecretsScanner;
import org.iromu.embabel.smith.skillevaluator.spi.ScannerRegistry;
import org.iromu.embabel.smith.skillevaluator.spi.SecretsScanner;

/**
 * Secrets detection, ported from the Python {@code validators/secrets.py}.
 *
 * <p>
 * Upstream entropy-detects hardcoded secrets, API keys, tokens and credentials with 200+
 * pre-built Gitleaks patterns; this class is the validator shell — the
 * folder/single-skill expansion plus the delegation to a pluggable engine — and the
 * detection itself lives in {@link GitleaksSecretsScanner} (or whatever engine is
 * injected).
 *
 * <h2>Deviations from upstream</h2>
 *
 * <ul>
 * <li><b>Engine injection.</b> Python has no injection seam; it calls
 * {@code Tools.gitleaks} directly and its tests monkeypatch that object. Here the engine
 * is an injected {@link SecretsScanner} (default: a {@link GitleaksSecretsScanner} after
 * any {@link ServiceLoader} registration), which is what lets the tests run without the
 * binary.</li>
 * <li><b>{@code scan_git_history}.</b> Python takes it on the validator constructor; here
 * it belongs to the engine, so it is selected with
 * {@code new SecretsValidator(new GitleaksSecretsScanner(true))}. The validator's own
 * default is the Python default: no git-history scanning.</li>
 * </ul>
 */
public class SecretsValidator extends ValidatorBase {

	private final SecretsScanner scanner;

	public SecretsValidator() {
		this(ScannerRegistry.secrets().load(new GitleaksSecretsScanner()));
	}

	/**
	 * Test/integration seam: run the same folder logic against a given secrets engine.
	 */
	public SecretsValidator(SecretsScanner scanner) {
		this.scanner = Objects.requireNonNull(scanner, "scanner");
	}

	/** The engine this validator scans with. */
	public SecretsScanner getScanner() {
		return scanner;
	}

	@Override
	public String getName() {
		return "Secrets Detection";
	}

	@Override
	public String getDescription() {
		return "Detect hardcoded secrets, API keys, and credentials using Gitleaks";
	}

	@Override
	public ValidationResult validate(Path skillPath) {
		ValidationResult result = validateFolderOrSkill(skillPath, scanner::scan, "Scanning for secrets in");
		result.setValidatorName(getName());
		result.setValidatorDescription(getDescription());
		return result;
	}

}
