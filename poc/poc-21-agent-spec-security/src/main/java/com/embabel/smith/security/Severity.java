/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.security;

/**
 * The severity of a single security finding. A {@link Severity#CRITICAL} finding means
 * the spec should not be loaded at all (verdict {@link ScanVerdict#BLOCK}); a
 * {@link Severity#WARN} finding means it may load under scrutiny at a higher trust tier
 * (verdict {@link ScanVerdict#REVIEW}).
 *
 * @author Ivan Rodriguez
 */
public enum Severity {

	/** Informational finding — no action required. */
	INFO,

	/** Warning-level finding — load under scrutiny at a higher tier. */
	WARN,

	/** Critical finding — do not load at all. */
	CRITICAL

}
