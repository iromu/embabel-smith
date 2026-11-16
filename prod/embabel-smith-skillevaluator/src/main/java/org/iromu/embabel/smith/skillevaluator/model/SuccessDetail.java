/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured success information for a passed check. */
public final class SuccessDetail {

	private final String checkName;

	private final String message;

	private final Map<String, Object> metadata;

	public SuccessDetail(String checkName, String message, Map<String, Object> metadata) {
		this.checkName = checkName;
		this.message = message;
		this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
	}

	public SuccessDetail(String checkName, String message) {
		this(checkName, message, Map.of());
	}

	public String getCheckName() {
		return checkName;
	}

	public String getMessage() {
		return message;
	}

	public Map<String, Object> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	public SuccessDetail withCheckName(String newCheckName) {
		return new SuccessDetail(newCheckName, message, metadata);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("check", checkName);
		map.put("message", message);
		map.put("metadata", metadata.isEmpty() ? null : metadata);
		return map;
	}

}
