/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.security;

/**
 * The severity of a single security finding. A {@link Severity#CRITICAL} finding means
 * the spec should not be published at all (verdict {@link ScanVerdict#BLOCK}); a
 * {@link Severity#WARN} finding means it may publish under scrutiny (verdict
 * {@link ScanVerdict#REVIEW}).
 *
 * @author Ivan Rodriguez
 */
public enum Severity {

	/** Informational finding that does not affect the verdict. */
	INFO,

	/** Finding that allows publication only under review. */
	WARN,

	/** Finding that blocks publication of the spec. */
	CRITICAL

}
