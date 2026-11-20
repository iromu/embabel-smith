/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import java.nio.file.Path;

import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/**
 * Pluggable security scanning (the {@code security} Tier 1 check).
 * <p>
 * Upstream SkillEvaluator shells out to the SkillSpector CLI for the vulnerability scan;
 * the deterministic PII half of that check is ported natively. Register an engine here to
 * replace the built-in SkillSpector shell-out; unavailable engines must mark their scan
 * incomplete.
 *
 * @author Ivan Rodriguez
 */
public interface SecurityScanner {

	String getName();

	boolean isAvailable();

	/**
	 * Scan a single skill directory for vulnerability patterns.
	 * @param skillPath the skill directory to scan
	 * @return the engine's findings and warnings; an unavailable engine marks the scan
	 * incomplete
	 */
	ValidationResult scanSecurity(Path skillPath);

}
