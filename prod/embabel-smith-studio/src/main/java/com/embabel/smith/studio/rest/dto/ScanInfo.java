/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The security scan of a agent spec (mirrors {@code ScanReport}).
 *
 * @param specName the spec that was scanned.
 * @param findings the security findings.
 * @param verdict the scan verdict.
 * @param recommendedTier the recommended trust tier.
 * @param summary the scan summary.
 * @author Ivan Rodriguez
 */
public record ScanInfo(String specName, List<SecurityFinding> findings, String verdict, String recommendedTier,
		String summary) {

	public static ScanInfo of(com.embabel.smith.builder.security.ScanReport report) {
		List<SecurityFinding> findings = report.findings()
			.stream()
			.map((f) -> new SecurityFinding(f.severity().name(), f.category(), f.message(), f.evidence()))
			.toList();
		return new ScanInfo(report.specName(), findings, report.verdict().name(),
				(report.recommendedTier() != null) ? report.recommendedTier().name() : null, report.summary());
	}
}
