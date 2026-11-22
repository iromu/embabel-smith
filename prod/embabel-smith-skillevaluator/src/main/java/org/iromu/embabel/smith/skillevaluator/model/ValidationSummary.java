/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Summary statistics for a validation run.
 *
 * @author Ivan Rodriguez
 */
public final class ValidationSummary {

	private int filesScanned;

	private int checksPerformed;

	private int errors;

	private int warnings;

	private int criticalCount;

	private int highCount;

	private int mediumCount;

	private int lowCount;

	/**
	 * Increment the per-severity counter for a finding. INFO is a no-op, matching
	 * upstream.
	 * @param severity the finding's level; {@code null} and INFO change nothing
	 */
	public void tallySeverity(Severity severity) {
		if (severity == null) {
			return;
		}
		switch (severity) {
			case CRITICAL -> this.criticalCount++;
			case HIGH -> this.highCount++;
			case MEDIUM -> this.mediumCount++;
			case LOW -> this.lowCount++;
			case INFO -> {
			}
		}
	}

	/** Zero the error/warning totals and per-severity counters. */
	public void resetFindingCounts() {
		this.errors = 0;
		this.warnings = 0;
		this.criticalCount = 0;
		this.highCount = 0;
		this.mediumCount = 0;
		this.lowCount = 0;
	}

	public void addFrom(ValidationSummary other) {
		this.filesScanned += other.filesScanned;
		this.checksPerformed += other.checksPerformed;
		this.errors += other.errors;
		this.warnings += other.warnings;
		this.criticalCount += other.criticalCount;
		this.highCount += other.highCount;
		this.mediumCount += other.mediumCount;
		this.lowCount += other.lowCount;
	}

	public int getFilesScanned() {
		return this.filesScanned;
	}

	public void setFilesScanned(int value) {
		this.filesScanned = value;
	}

	public int getChecksPerformed() {
		return this.checksPerformed;
	}

	public void setChecksPerformed(int value) {
		this.checksPerformed = value;
	}

	public int getErrors() {
		return this.errors;
	}

	public void setErrors(int value) {
		this.errors = value;
	}

	public int getWarnings() {
		return this.warnings;
	}

	public void setWarnings(int value) {
		this.warnings = value;
	}

	public int getCriticalCount() {
		return this.criticalCount;
	}

	public int getHighCount() {
		return this.highCount;
	}

	public int getMediumCount() {
		return this.mediumCount;
	}

	public int getLowCount() {
		return this.lowCount;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("files_scanned", this.filesScanned);
		map.put("checks_performed", this.checksPerformed);
		map.put("errors", this.errors);
		map.put("warnings", this.warnings);
		map.put("critical_count", this.criticalCount);
		map.put("high_count", this.highCount);
		map.put("medium_count", this.mediumCount);
		map.put("low_count", this.lowCount);
		return map;
	}

}
