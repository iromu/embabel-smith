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
 *
 * <p>
 * Port of the Python {@code ValidationResult}: structured findings plus the legacy
 * error/warning/message string lists that reporters consume, the severity-tallied
 * {@link ValidationSummary}, and metadata channels for incomplete external scans and
 * severity-independent gate failures.
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
		return validatorName;
	}

	public void setValidatorName(String value) {
		validatorName = value;
	}

	public String getValidatorDescription() {
		return validatorDescription;
	}

	public void setValidatorDescription(String value) {
		validatorDescription = value;
	}

	public boolean isPassed() {
		return passed;
	}

	public List<Finding> getFindings() {
		return findings;
	}

	public List<SuccessDetail> getSuccessDetails() {
		return successDetails;
	}

	public ValidationSummary getSummary() {
		return summary;
	}

	public List<String> getErrors() {
		return errors;
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public List<String> getMessages() {
		return messages;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	/** Canonical report status: {@code passed}, {@code failed}, or {@code incomplete}. */
	public String getStatus() {
		if (isIncomplete()) {
			return "incomplete";
		}
		return passed ? "passed" : "failed";
	}

	/** External scanners that did not produce complete trustworthy evidence. */
	public List<String> getIncompleteScans() {
		Object value = metadata.get("incomplete_scans");
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

	/** Failed requirements independent of finding severity, such as a minimum score. */
	@SuppressWarnings("unchecked")
	public List<String> getGateFailures() {
		Object value = metadata.get("gate_failures");
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

	/** External scanners that produced this result, mapped to their versions. */
	public Map<String, String> getScanners() {
		Object value = metadata.get("scanners");
		if (!(value instanceof Map<?, ?> map)) {
			return Map.of();
		}
		Map<String, String> out = new LinkedHashMap<>();
		map.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "unknown" : String.valueOf(v)));
		return out;
	}

	public void recordScanner(String name, String version) {
		Map<String, String> scanners = new LinkedHashMap<>(getScanners());
		scanners.put(name, version == null || version.isEmpty() ? "unknown" : version);
		metadata.put("scanners", scanners);
	}

	/** Add a structured finding and update the summary; error severities fail the run. */
	public void addFinding(Finding finding) {
		addFinding(finding, false, false);
	}

	public void addFinding(Finding finding, boolean failOnMedium, boolean failOnLow) {
		findings.add(finding);
		summary.tallySeverity(finding.getSeverity());
		if (finding.getSeverity().isError(failOnMedium, failOnLow)) {
			passed = false;
			summary.setErrors(summary.getErrors() + 1);
			errors.add(finding.toLegacyString());
		}
		else {
			summary.setWarnings(summary.getWarnings() + 1);
			warnings.add(finding.toLegacyString());
		}
	}

	/** Legacy calling convention: {@code addFinding("CVE", Severity.HIGH, msg)}. */
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

	/** Add a structured finding, forcing error/warning classification. */
	public void addStructuredFinding(Finding finding, boolean isError) {
		findings.add(finding);
		summary.tallySeverity(finding.getSeverity());
		if (isError) {
			addError(finding.toLegacyString());
		}
		else {
			addWarning(finding.toLegacyString());
		}
	}

	public void addSuccess(String checkName, String message, Map<String, Object> metadata) {
		successDetails.add(new SuccessDetail(checkName, message, metadata));
		summary.setChecksPerformed(summary.getChecksPerformed() + 1);
		messages.add("[OK] " + checkName + ": " + message);
	}

	public void addSuccess(String checkName, String message) {
		addSuccess(checkName, message, Map.of());
	}

	public void addError(String message) {
		errors.add(message);
		passed = false;
		summary.setErrors(summary.getErrors() + 1);
	}

	public void addWarning(String message) {
		warnings.add(message);
		summary.setWarnings(summary.getWarnings() + 1);
	}

	public void addMessage(String message) {
		messages.add(message);
	}

	/** Record a failed requirement that severity overrides cannot waive. */
	public void failGate(String message) {
		List<String> failures = new ArrayList<>(getGateFailures());
		if (!failures.contains(message)) {
			failures.add(message);
			metadata.put("gate_failures", failures);
			addError(message);
		}
	}

	/**
	 * Record that an external scanner did not produce complete trustworthy results.
	 * Partial findings remain available; reporters distinguish these via the canonical
	 * {@code incomplete} status.
	 */
	public void markScanIncomplete(String toolName) {
		List<String> scans = new ArrayList<>(getIncompleteScans());
		if (!scans.contains(toolName)) {
			scans.add(toolName);
		}
		metadata.put("incomplete_scans", scans);
		passed = false;
	}

	/**
	 * Rebuild passed, errors, warnings, and summary counts from current findings. Call
	 * after mutating finding severities (e.g. scanner downgrades).
	 */
	public void recalculateFromFindings() {
		errors.clear();
		warnings.clear();
		passed = !isIncomplete();
		summary.resetFindingCounts();
		for (String message : getGateFailures()) {
			addError(message);
		}
		for (Finding finding : findings) {
			Severity sev = finding.getSeverity();
			summary.tallySeverity(sev);
			boolean failOnMedium = Boolean.TRUE.equals(finding.getMetadata().get("fail_on_medium"));
			String legacy = finding.toLegacyString();
			if (sev.isError(failOnMedium, false)) {
				errors.add(legacy);
				passed = false;
				summary.setErrors(summary.getErrors() + 1);
			}
			else {
				warnings.add(legacy);
				summary.setWarnings(summary.getWarnings() + 1);
			}
		}
	}

	/** Merge another result into this one, combining all fields. */
	public void merge(ValidationResult other) {
		if (!other.passed) {
			passed = false;
		}
		findings.addAll(other.findings);
		successDetails.addAll(other.successDetails);
		errors.addAll(other.errors);
		warnings.addAll(other.warnings);
		messages.addAll(other.messages);

		List<String> incomplete = new ArrayList<>(new LinkedHashSet<>(
				java.util.stream.Stream.concat(getIncompleteScans().stream(), other.getIncompleteScans().stream())
					.toList()));
		List<String> gateFailures = new ArrayList<>(getGateFailures());
		gateFailures.addAll(other.getGateFailures());
		Map<String, String> scanners = new LinkedHashMap<>(getScanners());
		scanners.putAll(other.getScanners());

		metadata.putAll(other.metadata);
		if (!incomplete.isEmpty()) {
			metadata.put("incomplete_scans", incomplete);
		}
		if (!scanners.isEmpty()) {
			metadata.put("scanners", scanners);
		}
		if (!gateFailures.isEmpty()) {
			metadata.put("gate_failures", gateFailures);
		}
		summary.addFrom(other.summary);
	}

	/** Merge another result, prefixing all locations with the skill name. */
	public void mergeWithPrefix(ValidationResult other, String prefix) {
		if (!other.passed) {
			passed = false;
		}
		for (String error : other.errors) {
			errors.add("[" + prefix + "] " + error);
		}
		for (String warning : other.warnings) {
			warnings.add("[" + prefix + "] " + warning);
		}
		for (Finding finding : other.findings) {
			findings.add(finding.withPrefix(prefix));
		}
		for (SuccessDetail detail : other.successDetails) {
			successDetails.add(detail.withCheckName("[" + prefix + "] " + detail.getCheckName()));
		}
		List<String> incomplete = new ArrayList<>(new LinkedHashSet<>(
				java.util.stream.Stream.concat(getIncompleteScans().stream(), other.getIncompleteScans().stream())
					.toList()));
		if (!incomplete.isEmpty()) {
			metadata.put("incomplete_scans", incomplete);
		}
		List<String> gateFailures = new ArrayList<>(getGateFailures());
		for (String message : other.getGateFailures()) {
			gateFailures.add("[" + prefix + "] " + message);
		}
		if (!gateFailures.isEmpty()) {
			metadata.put("gate_failures", gateFailures);
		}
		Map<String, String> scanners = new LinkedHashMap<>(getScanners());
		scanners.putAll(other.getScanners());
		if (!scanners.isEmpty()) {
			metadata.put("scanners", scanners);
		}
		summary.setErrors(summary.getErrors() + other.summary.getErrors());
		summary.setWarnings(summary.getWarnings() + other.summary.getWarnings());
	}

	/**
	 * Carry a passing sub-result's nonblocking findings into this one, naming the member.
	 */
	public void mergeAdvisoriesWithPrefix(ValidationResult other, String prefix) {
		for (Finding finding : other.findings) {
			findings.add(finding.withPrefix(prefix, Map.of("collection_skill", prefix)));
			summary.tallySeverity(finding.getSeverity());
		}
		for (String warning : other.warnings) {
			warnings.add("[" + prefix + "] " + warning);
		}
		summary.setWarnings(summary.getWarnings() + other.summary.getWarnings());
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("validator", validatorName);
		map.put("description", validatorDescription);
		map.put("passed", passed);
		map.put("status", getStatus());
		map.put("incomplete_scans", getIncompleteScans());
		map.put("summary", summary.toMap());
		map.put("findings", findings.stream().map(Finding::toMap).toList());
		map.put("success_details", successDetails.stream().map(SuccessDetail::toMap).toList());
		if (!getGateFailures().isEmpty()) {
			map.put("gate_failures", getGateFailures());
		}
		if (!getScanners().isEmpty()) {
			map.put("scanners", getScanners());
		}
		return map;
	}

	public List<Map<String, Object>> findingsAsMaps() {
		return findings.stream().map(Finding::toMap).toList();
	}

	/** Unmodifiable view helper for callers that must not mutate the finding list. */
	public List<Finding> unmodifiableFindings() {
		return Collections.unmodifiableList(findings);
	}

}
