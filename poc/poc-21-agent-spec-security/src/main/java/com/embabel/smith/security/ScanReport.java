/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.security;

import java.util.List;

import com.embabel.smith.trust.TrustTier;

/**
 * The result of scanning one agent spec: the ordered findings, the aggregated verdict,
 * and the minimum POC 10 trust tier at which the spec's declared tool groups are all
 * permitted.
 *
 * @param specName the name of the scanned spec
 * @param findings the ordered findings
 * @param verdict the aggregated verdict
 * @param recommendedTier the minimum POC 10 trust tier at which the spec is safe to load
 * @author Ivan Rodriguez
 */
public record ScanReport(String specName, List<Finding> findings, ScanVerdict verdict, TrustTier recommendedTier) {

	public ScanReport {
		findings = (findings != null) ? List.copyOf(findings) : List.of();
	}

	public boolean hasCritical() {
		return this.findings.stream().anyMatch(Finding::isCritical);
	}

	public boolean hasWarning() {
		return this.findings.stream().anyMatch(Finding::isWarning);
	}

	public List<Finding> findingsIn(String category) {
		return this.findings.stream().filter((f) -> f.category().equals(category)).toList();
	}

	/**
	 * One-line human summary, e.g. {@code BLOCK (2 critical, 1 warning) at T3_PARTNER}.
	 */
	public String summary() {
		long critical = this.findings.stream().filter(Finding::isCritical).count();
		long warning = this.findings.stream().filter(Finding::isWarning).count();
		return this.verdict + " (" + critical + " critical, " + warning + " warning) at " + this.recommendedTier;
	}
}
