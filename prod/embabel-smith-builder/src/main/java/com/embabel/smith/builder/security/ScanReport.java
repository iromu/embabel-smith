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

package com.embabel.smith.builder.security;

import java.util.List;

/**
 * The result of scanning one agent spec: the ordered findings, the aggregated verdict,
 * and the minimum trust tier at which the spec's declared tool groups are all permitted.
 *
 * @param specName the name of the scanned agent spec.
 * @param findings the ordered findings from the scan.
 * @param verdict the aggregated risk verdict.
 * @param recommendedTier the minimum trust tier at which the spec is permitted.
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
