/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Structured validation issue with full context for CI reporting.
 *
 * <p>
 * Mirrors the Python {@code Finding} dataclass, including the deterministic semantic
 * identity ({@code SEF-…}) and occurrence identity ({@code SEO-…}) used by reporters to
 * reconcile findings across runs.
 */
public final class Finding {

	private final String category;

	private final Severity severity;

	private final String checkName;

	private final String message;

	private final String filePath;

	private final Integer lineNumber;

	private final String lineContent;

	private final String suggestion;

	private final Map<String, Object> metadata;

	public Finding(String category, Severity severity, String checkName, String message, String filePath,
			Integer lineNumber, String lineContent, String suggestion, Map<String, Object> metadata) {
		this.category = category;
		this.severity = severity == null ? Severity.MEDIUM : severity;
		this.checkName = checkName;
		this.message = message;
		this.filePath = filePath;
		this.lineNumber = lineNumber;
		this.lineContent = lineContent;
		this.suggestion = suggestion;
		this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
	}

	public Finding(String category, Severity severity, String checkName, String message, String filePath) {
		this(category, severity, checkName, message, filePath, null, null, null, null);
	}

	public String getCategory() {
		return category;
	}

	public Severity getSeverity() {
		return severity;
	}

	public String getCheckName() {
		return checkName;
	}

	public String getMessage() {
		return message;
	}

	public String getFilePath() {
		return filePath;
	}

	public Integer getLineNumber() {
		return lineNumber;
	}

	public String getLineContent() {
		return lineContent;
	}

	public String getSuggestion() {
		return suggestion;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	/** Formatted tag such as {@code [SCHEMA-HIGH]}. */
	public String getTag() {
		return "[" + category + "-" + severity.name().toUpperCase(Locale.ROOT) + "]";
	}

	/** Formatted location such as {@code file.py:42}. */
	public String getLocation() {
		String loc = filePath;
		if (lineNumber != null && lineNumber != 0) {
			loc += ":" + lineNumber;
		}
		return loc;
	}

	public String toLegacyString() {
		return getTag() + " " + message + " in " + getLocation();
	}

	/** Copy this finding with {@code [prefix]} applied to every location it names. */
	public Finding withPrefix(String prefix, Map<String, Object> extraMetadata) {
		Map<String, Object> newMetadata = new LinkedHashMap<>(metadata);
		for (String key : List.of("occurrences", "skillspector_occurrences")) {
			Object listed = newMetadata.get(key);
			if (!(listed instanceof List<?> items)) {
				continue;
			}
			List<Object> prefixed = new ArrayList<>();
			for (Object item : items) {
				if (item instanceof Map<?, ?> mapItem && mapItem.get("file") instanceof String file) {
					Map<String, Object> copy = new LinkedHashMap<>();
					mapItem.forEach((k, v) -> copy.put(String.valueOf(k), v));
					copy.put("file", "[" + prefix + "] " + file);
					prefixed.add(copy);
				}
				else {
					prefixed.add(item);
				}
			}
			newMetadata.put(key, prefixed);
		}
		if (extraMetadata != null) {
			newMetadata.putAll(extraMetadata);
		}
		return new Finding(category, severity, checkName, message, "[" + prefix + "] " + filePath, lineNumber,
				lineContent, suggestion, newMetadata);
	}

	public Finding withPrefix(String prefix) {
		return withPrefix(prefix, null);
	}

	/** Deterministic semantic identity independent of location: {@code SEF-<20 hex>}. */
	public String getFindingId() {
		String identity = String.join("\u001f", normalizeIdentityText(category), normalizeIdentityText(checkName),
				normalizeIdentityText(identityMessage()), normalizeIdentityText(suggestion));
		return "SEF-" + sha256Prefix(identity);
	}

	/** Deterministic identity for a concrete location: {@code SEO-<20 hex>}. */
	public String locationOccurrenceId(String path, Object line, String content) {
		String identity = String.join("\u001f", getFindingId(), (path == null ? "" : path).replace('\\', '/'),
				line == null ? "" : String.valueOf(line), normalizeIdentityText(content));
		return "SEO-" + sha256Prefix(identity);
	}

	public String getOccurrenceId() {
		return locationOccurrenceId(filePath, lineNumber, lineContent);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("finding_id", getFindingId());
		map.put("occurrence_id", getOccurrenceId());
		map.put("category", category);
		map.put("severity", severity.name().toLowerCase(Locale.ROOT));
		map.put("check_name", checkName);
		map.put("message", message);
		map.put("file_path", filePath);
		map.put("line_number", lineNumber);
		map.put("line_content", lineContent);
		map.put("suggestion", suggestion);
		map.put("metadata", metadata);
		return map;
	}

	// --- identity helpers (ports of the Python privates) ---

	static String normalizeIdentityText(Object value) {
		String text = (value == null ? "" : String.valueOf(value)).toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		boolean pendingSpace = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				pendingSpace = sb.length() > 0;
			}
			else {
				if (pendingSpace) {
					sb.append(' ');
				}
				pendingSpace = false;
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * {@code file_path} plus the shorter paths a message may name it by, longest first.
	 */
	private static List<String> locationPaths(String path) {
		List<String> paths = new ArrayList<>();
		paths.add(path);
		String current = path;
		while (current.startsWith("[") && current.contains("] ")) {
			current = current.substring(current.indexOf("] ") + 2);
			paths.add(current);
		}
		List<String> suffixes = new ArrayList<>();
		for (int i = 0; i < current.length() - 1; i++) {
			char c = current.charAt(i);
			if (c == '/' || c == '\\') {
				suffixes.add(current.substring(i + 1));
			}
		}
		paths.addAll(suffixes);
		return paths;
	}

	/** Message without the location/occurrence text the finding records elsewhere. */
	private String identityMessage() {
		String msg = message == null ? "" : message;
		if (filePath != null && lineNumber != null) {
			String slashed = msg.replace('\\', '/');
			for (String location : locationPaths(filePath)) {
				String text = (" in " + location + ":" + lineNumber).replace('\\', '/');
				int index = slashed.indexOf(text);
				if (index >= 0) {
					msg = slashed.substring(0, index) + slashed.substring(index + text.length());
					break;
				}
			}
		}
		Object occurrences = metadata.get("occurrences");
		if (occurrences instanceof List<?> list && list.size() > 1) {
			String marker = " \u2014 " + list.size() + " occurrences (";
			int index = msg.lastIndexOf(marker);
			if (index >= 0 && msg.endsWith(")")) {
				msg = msg.substring(0, index);
			}
		}
		return msg;
	}

	private static String sha256Prefix(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash).substring(0, 20);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

}
