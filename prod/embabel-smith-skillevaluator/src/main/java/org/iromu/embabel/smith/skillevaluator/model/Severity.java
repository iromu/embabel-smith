/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.model;

import java.util.Locale;

/**
 * Standardized severity levels for validation findings.
 *
 * @author Ivan Rodriguez
 */
public enum Severity {

	/** Blocking: an unresolved finding at this level fails the run. */
	CRITICAL,

	/** Fails the run unless the finding is waived. */
	HIGH,

	/** Warning by default; an error only when {@code failOnMedium} is set. */
	MEDIUM,

	/** Warning by default; an error only when {@code failOnLow} is set. */
	LOW,

	/** Purely informational; never an error. */
	INFO;

	private static final Severity[] ERROR_LEVELS = { CRITICAL, HIGH };

	/**
	 * Parse a severity name case-insensitively, defaulting to {@link #MEDIUM} for unknown
	 * values.
	 * @param value the severity name to parse; {@code null} and unrecognised names both
	 * yield {@link #MEDIUM}
	 * @return the matching constant, or {@link #MEDIUM} when there is none
	 */
	public static Severity fromString(String value) {
		if (value != null) {
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException ignored) {
				// fall through to default, mirroring the Python __post_init__ mapping
			}
		}
		return MEDIUM;
	}

	/**
	 * Whether this severity fails validation under the given thresholds.
	 * @param failOnMedium treat {@link #MEDIUM} as an error
	 * @param failOnLow treat {@link #LOW} as an error
	 * @return {@code true} when this level fails the run under those thresholds
	 */
	public boolean isError(boolean failOnMedium, boolean failOnLow) {
		for (Severity level : ERROR_LEVELS) {
			if (this == level) {
				return true;
			}
		}
		if (this == MEDIUM && failOnMedium) {
			return true;
		}
		return this == LOW && failOnLow;
	}

	public boolean isError() {
		return isError(false, false);
	}

	/**
	 * Text indicator such as {@code [CRIT]}.
	 * @return the bracketed short label for this level
	 */
	public String indicator() {
		return switch (this) {
			case CRITICAL -> "[CRIT]";
			case HIGH -> "[HIGH]";
			case MEDIUM -> "[MED]";
			case LOW -> "[LOW]";
			case INFO -> "[INFO]";
		};
	}

}
