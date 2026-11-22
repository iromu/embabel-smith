/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Aggregates validation outcomes from one validator.
 * <p>
 * Port of the Python {@code ValidationResult}: structured findings plus the legacy
 * error/warning/message string lists that reporters consume, the severity-tallied
 * {@link ValidationSummary}, and metadata channels for incomplete external scans and
 * severity-independent gate failures.
 *
 * @author Ivan Rodriguez
 */
public final class ValidationResult {

	private String validatorName = "";

	private String validatorDescription = "";

	private boolean passed = true;

	private final List<Finding> findings = new ArrayList<>();

	private final List<SuccessDetail> successDetails = new ArrayList<>();

	private final ValidationSummary summary = new ValidationSummary();

	private final List<String> errors = new ArrayList<>();

	private final List<String> warnings = new ArrayList<>();

	private final List<String> messages = new ArrayList<>();

	private final Map<String, Object> metadata = new LinkedHashMap<>();

	public String getValidatorName() {
		return this.validatorName;
	}

	public void setValidatorName(String value) {
		this.validatorName = value;
	}

	public String getValidatorDescription() {
		return this.validatorDescription;
	}

	public void setValidatorDescription(String value) {
		this.validatorDescription = value;
	}

	public boolean isPassed() {
		return this.passed;
	}

	public List<Finding> getFindings() {
		return this.findings;
	}

	public List<SuccessDetail> getSuccessDetails() {
		return this.successDetails;
	}

	public ValidationSummary getSummary() {
		return this.summary;
	}

	public List<String> getErrors() {
		return this.errors;
	}

	public List<String> getWarnings() {
		return this.warnings;
	}

	public List<String> getMessages() {
		return this.messages;
	}

	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	/**
	 * Canonical report status: {@code passed}, {@code failed}, or {@code incomplete}.
	 * @return {@code incomplete} when an external scan is incomplete, else {@code passed}
	 * or {@code failed}
	 */
	public String getStatus() {
		if (isIncomplete()) {
			return "incomplete";
		}
		return this.passed ? "passed" : "failed";
	}

	/**
	 * External scanners that did not produce complete trustworthy evidence.
	 * @return the recorded scanner names, or an empty list when the metadata key is
	 * missing or malformed
	 */
	public List<String> getIncompleteScans() {
		Object value = this.metadata.get("incomplete_scans");
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof String s && !s.isEmpty()) {
				names.add(s);
			}
		}
		return names;
	}

	/**
	 * Failed requirements independent of finding severity, such as a minimum score.
	 * @return the recorded gate-failure messages, or an empty list when the metadata key
	 * is missing or malformed
	 */
	@SuppressWarnings("unchecked")
	public List<String> getGateFailures() {
		Object value = this.metadata.get("gate_failures");
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof String s && !s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}

	public boolean isIncomplete() {
		return !getIncompleteScans().isEmpty();
	}

	/**
	 * External scanners that produced this result, mapped to their versions.
	 * @return the scanner-name to version map, or an empty map when the metadata key is
	 * missing or malformed
	 */
	public Map<String, String> getScanners() {
		Object value = this.metadata.get("scanners");
		if (!(value instanceof Map<?, ?> map)) {
			return Map.of();
		}
		Map<String, String> out = new LinkedHashMap<>();
		map.forEach((k, v) -> out.put(String.valueOf(k), (v != null) ? String.valueOf(v) : "unknown"));
		return out;
	}

	public void recordScanner(String name, String version) {
		Map<String, String> scanners = new LinkedHashMap<>(getScanners());
		scanners.put(name, (version == null || version.isEmpty()) ? "unknown" : version);
		this.metadata.put("scanners", scanners);
	}

	/**
	 * Add a structured finding and update the summary; error severities fail the run.
	 * @param finding the finding to record and tally
	 */
	public void addFinding(Finding finding) {
		addFinding(finding, false, false);
	}

	public void addFinding(Finding finding, boolean failOnMedium, boolean failOnLow) {
		this.findings.add(finding);
		this.summary.tallySeverity(finding.getSeverity());
		if (finding.getSeverity().isError(failOnMedium, failOnLow)) {
			this.passed = false;
			this.summary.setErrors(this.summary.getErrors() + 1);
			this.errors.add(finding.toLegacyString());
		}
		else {
			this.summary.setWarnings(this.summary.getWarnings() + 1);
			this.warnings.add(finding.toLegacyString());
		}
	}

	/**
	 * Legacy calling convention: {@code addFinding("CVE", Severity.HIGH, msg)}.
	 * @param tag the finding tag, upper-cased into the {@code [TAG-SEVERITY]} prefix
	 * @param severity the severity deciding error versus warning
	 * @param message the human-readable finding text
	 * @param failOnMedium treat {@link Severity#MEDIUM} findings as errors
	 * @param failOnLow treat {@link Severity#LOW} findings as errors
	 */
	public void addFinding(String tag, Severity severity, String message, boolean failOnMedium, boolean failOnLow) {
		String formatted = "[" + tag + "-" + severity.name().toUpperCase() + "] " + message;
		if (severity.isError(failOnMedium, failOnLow)) {
			addError(formatted);
		}
		else {
			addWarning(formatted);
		}
	}

	public void addFinding(String tag, Severity severity, String message) {
		addFinding(tag, severity, message, false, false);
	}

	/**
	 * Add a structured finding, forcing error/warning classification.
	 * @param finding the finding to record and tally
	 * @param isError {@code true} to count it as an error regardless of its severity
	 */
	public void addStructuredFinding(Finding finding, boolean isError) {
		this.findings.add(finding);
		this.summary.tallySeverity(finding.getSeverity());
		if (isError) {
			addError(finding.toLegacyString());
		}
		else {
			addWarning(finding.toLegacyString());
		}
	}

	public void addSuccess(String checkName, String message, Map<String, Object> metadata) {
		this.successDetails.add(new SuccessDetail(checkName, message, metadata));
		this.summary.setChecksPerformed(this.summary.getChecksPerformed() + 1);
		this.messages.add("[OK] " + checkName + ": " + message);
	}

	public void addSuccess(String checkName, String message) {
		addSuccess(checkName, message, Map.of());
	}

	public void addError(String message) {
		this.errors.add(message);
		this.passed = false;
		this.summary.setErrors(this.summary.getErrors() + 1);
	}

	public void addWarning(String message) {
		this.warnings.add(message);
		this.summary.setWarnings(this.summary.getWarnings() + 1);
	}

	public void addMessage(String message) {
		this.messages.add(message);
	}

	/**
	 * Record a failed requirement that severity overrides cannot waive.
	 * @param message the gate-failure text, added to the errors unless already recorded
	 */
	public void failGate(String message) {
		List<String> failures = new ArrayList<>(getGateFailures());
		if (!failures.contains(message)) {
			failures.add(message);
			this.metadata.put("gate_failures", failures);
			addError(message);
		}
	}

	/**
	 * Record that an external scanner did not produce complete trustworthy results.
	 * Partial findings remain available; reporters distinguish these via the canonical
	 * {@code incomplete} status.
	 * @param toolName the scanner whose evidence is incomplete; recorded once
	 */
	public void markScanIncomplete(String toolName) {
		List<String> scans = new ArrayList<>(getIncompleteScans());
		if (!scans.contains(toolName)) {
			scans.add(toolName);
		}
		this.metadata.put("incomplete_scans", scans);
		this.passed = false;
	}

	/**
	 * Rebuild passed, errors, warnings, and summary counts from current findings. Call
	 * after mutating finding severities (e.g. scanner downgrades).
	 */
	public void recalculateFromFindings() {
		this.errors.clear();
		this.warnings.clear();
		this.passed = !isIncomplete();
		this.summary.resetFindingCounts();
		for (String message : getGateFailures()) {
			addError(message);
		}
		for (Finding finding : this.findings) {
			Severity sev = finding.getSeverity();
			this.summary.tallySeverity(sev);
			boolean failOnMedium = Boolean.TRUE.equals(finding.getMetadata().get("fail_on_medium"));
			String legacy = finding.toLegacyString();
			if (sev.isError(failOnMedium, false)) {
				this.errors.add(legacy);
				this.passed = false;
				this.summary.setErrors(this.summary.getErrors() + 1);
			}
			else {
				this.warnings.add(legacy);
				this.summary.setWarnings(this.summary.getWarnings() + 1);
			}
		}
	}

	/**
	 * Merge another result into this one, combining all fields.
	 * @param other the result folded into this one
	 */
	public void merge(ValidationResult other) {
		if (!other.passed) {
			this.passed = false;
		}
		this.findings.addAll(other.findings);
		this.successDetails.addAll(other.successDetails);
		this.errors.addAll(other.errors);
		this.warnings.addAll(other.warnings);
		this.messages.addAll(other.messages);

		List<String> incomplete = new ArrayList<>(new LinkedHashSet<>(
				java.util.stream.Stream.concat(getIncompleteScans().stream(), other.getIncompleteScans().stream())
					.toList()));
		List<String> gateFailures = new ArrayList<>(getGateFailures());
		gateFailures.addAll(other.getGateFailures());
		Map<String, String> scanners = new LinkedHashMap<>(getScanners());
		scanners.putAll(other.getScanners());

		this.metadata.putAll(other.metadata);
		if (!incomplete.isEmpty()) {
			this.metadata.put("incomplete_scans", incomplete);
		}
		if (!scanners.isEmpty()) {
			this.metadata.put("scanners", scanners);
		}
		if (!gateFailures.isEmpty()) {
			this.metadata.put("gate_failures", gateFailures);
		}
		this.summary.addFrom(other.summary);
	}

	/**
	 * Merge another result, prefixing all locations with the skill name.
	 * @param other the result folded into this one
	 * @param prefix the skill name prefixed to errors, warnings and finding locations
	 */
	public void mergeWithPrefix(ValidationResult other, String prefix) {
		if (!other.passed) {
			this.passed = false;
		}
		for (String error : other.errors) {
			this.errors.add("[" + prefix + "] " + error);
		}
		for (String warning : other.warnings) {
			this.warnings.add("[" + prefix + "] " + warning);
		}
		for (Finding finding : other.findings) {
			this.findings.add(finding.withPrefix(prefix));
		}
		for (SuccessDetail detail : other.successDetails) {
			this.successDetails.add(detail.withCheckName("[" + prefix + "] " + detail.getCheckName()));
		}
		List<String> incomplete = new ArrayList<>(new LinkedHashSet<>(
				java.util.stream.Stream.concat(getIncompleteScans().stream(), other.getIncompleteScans().stream())
					.toList()));
		if (!incomplete.isEmpty()) {
			this.metadata.put("incomplete_scans", incomplete);
		}
		List<String> gateFailures = new ArrayList<>(getGateFailures());
		for (String message : other.getGateFailures()) {
			gateFailures.add("[" + prefix + "] " + message);
		}
		if (!gateFailures.isEmpty()) {
			this.metadata.put("gate_failures", gateFailures);
		}
		Map<String, String> scanners = new LinkedHashMap<>(getScanners());
		scanners.putAll(other.getScanners());
		if (!scanners.isEmpty()) {
			this.metadata.put("scanners", scanners);
		}
		this.summary.setErrors(this.summary.getErrors() + other.summary.getErrors());
		this.summary.setWarnings(this.summary.getWarnings() + other.summary.getWarnings());
	}

	/**
	 * Carry a passing sub-result's nonblocking findings into this one, naming the member.
	 * @param other the passing sub-result whose findings and warnings are carried over
	 * @param prefix member name prefixed to locations and recorded as
	 * {@code collection_skill}
	 */
	public void mergeAdvisoriesWithPrefix(ValidationResult other, String prefix) {
		for (Finding finding : other.findings) {
			this.findings.add(finding.withPrefix(prefix, Map.of("collection_skill", prefix)));
			this.summary.tallySeverity(finding.getSeverity());
		}
		for (String warning : other.warnings) {
			this.warnings.add("[" + prefix + "] " + warning);
		}
		this.summary.setWarnings(this.summary.getWarnings() + other.summary.getWarnings());
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("validator", this.validatorName);
		map.put("description", this.validatorDescription);
		map.put("passed", this.passed);
		map.put("status", getStatus());
		map.put("incomplete_scans", getIncompleteScans());
		map.put("summary", this.summary.toMap());
		map.put("findings", this.findings.stream().map(Finding::toMap).toList());
		map.put("success_details", this.successDetails.stream().map(SuccessDetail::toMap).toList());
		if (!getGateFailures().isEmpty()) {
			map.put("gate_failures", getGateFailures());
		}
		if (!getScanners().isEmpty()) {
			map.put("scanners", getScanners());
		}
		return map;
	}

	public List<Map<String, Object>> findingsAsMaps() {
		return this.findings.stream().map(Finding::toMap).toList();
	}

	/**
	 * Unmodifiable view helper for callers that must not mutate the finding list.
	 * @return a live unmodifiable view of this result's findings
	 */
	public List<Finding> unmodifiableFindings() {
		return Collections.unmodifiableList(this.findings);
	}

}
