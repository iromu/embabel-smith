/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Summary statistics for a validation run. */
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
	 */
	public void tallySeverity(Severity severity) {
		if (severity == null) {
			return;
		}
		switch (severity) {
			case CRITICAL -> criticalCount++;
			case HIGH -> highCount++;
			case MEDIUM -> mediumCount++;
			case LOW -> lowCount++;
			case INFO -> {
			}
		}
	}

	/** Zero the error/warning totals and per-severity counters. */
	public void resetFindingCounts() {
		errors = 0;
		warnings = 0;
		criticalCount = 0;
		highCount = 0;
		mediumCount = 0;
		lowCount = 0;
	}

	public void addFrom(ValidationSummary other) {
		filesScanned += other.filesScanned;
		checksPerformed += other.checksPerformed;
		errors += other.errors;
		warnings += other.warnings;
		criticalCount += other.criticalCount;
		highCount += other.highCount;
		mediumCount += other.mediumCount;
		lowCount += other.lowCount;
	}

	public int getFilesScanned() {
		return filesScanned;
	}

	public void setFilesScanned(int value) {
		filesScanned = value;
	}

	public int getChecksPerformed() {
		return checksPerformed;
	}

	public void setChecksPerformed(int value) {
		checksPerformed = value;
	}

	public int getErrors() {
		return errors;
	}

	public void setErrors(int value) {
		errors = value;
	}

	public int getWarnings() {
		return warnings;
	}

	public void setWarnings(int value) {
		warnings = value;
	}

	public int getCriticalCount() {
		return criticalCount;
	}

	public int getHighCount() {
		return highCount;
	}

	public int getMediumCount() {
		return mediumCount;
	}

	public int getLowCount() {
		return lowCount;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("files_scanned", filesScanned);
		map.put("checks_performed", checksPerformed);
		map.put("errors", errors);
		map.put("warnings", warnings);
		map.put("critical_count", criticalCount);
		map.put("high_count", highCount);
		map.put("medium_count", mediumCount);
		map.put("low_count", lowCount);
		return map;
	}

}
