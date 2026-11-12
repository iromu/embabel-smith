/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
