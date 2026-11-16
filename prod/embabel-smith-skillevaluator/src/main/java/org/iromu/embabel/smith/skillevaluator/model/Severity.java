/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.model;

import java.util.Locale;

/** Standardized severity levels for validation findings. */
public enum Severity {

	CRITICAL, HIGH, MEDIUM, LOW, INFO;

	private static final Severity[] ERROR_LEVELS = { CRITICAL, HIGH };

	/**
	 * Parse a severity name case-insensitively, defaulting to {@link #MEDIUM} for unknown
	 * values.
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

	/** Whether this severity fails validation under the given thresholds. */
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

	/** Text indicator such as {@code [CRIT]}. */
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
