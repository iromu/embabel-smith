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
 * <p>
 * Mirrors the Python {@code Finding} dataclass, including the deterministic semantic
 * identity ({@code SEF-…}) and occurrence identity ({@code SEO-…}) used by reporters to
 * reconcile findings across runs.
 *
 * @author Ivan Rodriguez
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
		this.severity = (severity != null) ? severity : Severity.MEDIUM;
		this.checkName = checkName;
		this.message = message;
		this.filePath = filePath;
		this.lineNumber = lineNumber;
		this.lineContent = lineContent;
		this.suggestion = suggestion;
		this.metadata = (metadata != null) ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
	}

	public Finding(String category, Severity severity, String checkName, String message, String filePath) {
		this(category, severity, checkName, message, filePath, null, null, null, null);
	}

	public String getCategory() {
		return this.category;
	}

	public Severity getSeverity() {
		return this.severity;
	}

	public String getCheckName() {
		return this.checkName;
	}

	public String getMessage() {
		return this.message;
	}

	public String getFilePath() {
		return this.filePath;
	}

	public Integer getLineNumber() {
		return this.lineNumber;
	}

	public String getLineContent() {
		return this.lineContent;
	}

	public String getSuggestion() {
		return this.suggestion;
	}

	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	/**
	 * Formatted tag such as {@code [SCHEMA-HIGH]}.
	 * @return the category and the uppercase severity name, in brackets
	 */
	public String getTag() {
		return "[" + this.category + "-" + this.severity.name().toUpperCase(Locale.ROOT) + "]";
	}

	/**
	 * Formatted location such as {@code file.py:42}.
	 * @return the file path, suffixed {@code :<line>} when a non-zero line number is
	 * known
	 */
	public String getLocation() {
		String loc = this.filePath;
		if (this.lineNumber != null && this.lineNumber != 0) {
			loc += ":" + this.lineNumber;
		}
		return loc;
	}

	public String toLegacyString() {
		return getTag() + " " + this.message + " in " + getLocation();
	}

	/**
	 * Copy this finding with {@code [prefix]} applied to every location it names.
	 * @param prefix bundle label prepended, in brackets, to the file path and to the
	 * {@code file} entry of each {@code occurrences} and {@code skillspector_occurrences}
	 * item
	 * @param extraMetadata metadata merged into the copy, overriding same-named keys;
	 * {@code null} leaves the copy's metadata as this finding's
	 * @return a new finding with prefixed locations and a fresh metadata map
	 */
	public Finding withPrefix(String prefix, Map<String, Object> extraMetadata) {
		Map<String, Object> newMetadata = new LinkedHashMap<>(this.metadata);
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
		return new Finding(this.category, this.severity, this.checkName, this.message,
				"[" + prefix + "] " + this.filePath, this.lineNumber, this.lineContent, this.suggestion, newMetadata);
	}

	public Finding withPrefix(String prefix) {
		return withPrefix(prefix, null);
	}

	/**
	 * Deterministic semantic identity independent of location: {@code SEF-<20 hex>}.
	 * @return the digest of the normalized category, check name, location-free message
	 * and suggestion, prefixed {@code SEF-}
	 */
	public String getFindingId() {
		String identity = String.join("\u001f", normalizeIdentityText(this.category),
				normalizeIdentityText(this.checkName), normalizeIdentityText(identityMessage()),
				normalizeIdentityText(this.suggestion));
		return "SEF-" + sha256Prefix(identity);
	}

	/**
	 * Deterministic identity for a concrete location: {@code SEO-<20 hex>}.
	 * @param path occurrence file path; backslashes count as separators and {@code null}
	 * reads as empty
	 * @param line occurrence line number, stringified as given or empty when {@code null}
	 * @param content matched line text, normalized like the identity fields
	 * @return the digest of this finding's semantic identity with the given location,
	 * prefixed {@code SEO-}
	 */
	public String locationOccurrenceId(String path, Object line, String content) {
		String identity = String.join("\u001f", getFindingId(), ((path != null) ? path : "").replace('\\', '/'),
				(line != null) ? String.valueOf(line) : "", normalizeIdentityText(content));
		return "SEO-" + sha256Prefix(identity);
	}

	public String getOccurrenceId() {
		return locationOccurrenceId(this.filePath, this.lineNumber, this.lineContent);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("finding_id", getFindingId());
		map.put("occurrence_id", getOccurrenceId());
		map.put("category", this.category);
		map.put("severity", this.severity.name().toLowerCase(Locale.ROOT));
		map.put("check_name", this.checkName);
		map.put("message", this.message);
		map.put("file_path", this.filePath);
		map.put("line_number", this.lineNumber);
		map.put("line_content", this.lineContent);
		map.put("suggestion", this.suggestion);
		map.put("metadata", this.metadata);
		return map;
	}

	// --- identity helpers (ports of the Python privates) ---

	static String normalizeIdentityText(Object value) {
		String text = ((value != null) ? String.valueOf(value) : "").toLowerCase(Locale.ROOT);
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
	 * @param path the finding's file path, possibly {@code [bundle] }-prefixed
	 * @return the path as given, then its unbracketed forms and every suffix that follows
	 * a path separator
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

	/**
	 * Message without the location/occurrence text the finding records elsewhere.
	 * @return the message with one {@code in <path>:<line>} span and a multi-occurrence
	 * suffix stripped, or unchanged when neither is present
	 */
	private String identityMessage() {
		String msg = (this.message == null) ? "" : this.message;
		if (this.filePath != null && this.lineNumber != null) {
			String slashed = msg.replace('\\', '/');
			for (String location : locationPaths(this.filePath)) {
				String text = (" in " + location + ":" + this.lineNumber).replace('\\', '/');
				int index = slashed.indexOf(text);
				if (index >= 0) {
					msg = slashed.substring(0, index) + slashed.substring(index + text.length());
					break;
				}
			}
		}
		Object occurrences = this.metadata.get("occurrences");
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
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
