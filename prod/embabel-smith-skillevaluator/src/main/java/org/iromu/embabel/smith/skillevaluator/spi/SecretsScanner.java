/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import java.nio.file.Path;

import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/**
 * Pluggable secrets detection (the {@code secrets} Tier 1 check).
 * <p>
 * Upstream SkillEvaluator shells out to Gitleaks; this port keeps that behaviour as the
 * built-in default while letting callers register their own engine through
 * {@link java.util.ServiceLoader}. Implementations must degrade gracefully: when the
 * backing tool is unavailable, return a result carrying
 * {@link ValidationResult#markScanIncomplete(String)} rather than throwing.
 *
 * @author Ivan Rodriguez
 */
public interface SecretsScanner {

	/**
	 * Scanner name recorded via {@link ValidationResult#recordScanner}.
	 * @return the engine's display name
	 */
	String getName();

	/**
	 * Whether the backing engine can run at all.
	 * @return {@code true} when the engine has what it needs to scan
	 */
	boolean isAvailable();

	/**
	 * Scan a single skill directory (folder expansion is handled by the validator).
	 * @param skillPath the skill directory to scan
	 * @return the scan result; an unavailable engine marks the scan incomplete rather
	 * than throwing
	 */
	ValidationResult scan(Path skillPath);

}
