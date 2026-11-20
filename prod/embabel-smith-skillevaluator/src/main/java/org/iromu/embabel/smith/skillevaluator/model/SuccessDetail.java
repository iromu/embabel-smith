/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured success information for a passed check.
 *
 * @author Ivan Rodriguez
 */
public final class SuccessDetail {

	private final String checkName;

	private final String message;

	private final Map<String, Object> metadata;

	public SuccessDetail(String checkName, String message, Map<String, Object> metadata) {
		this.checkName = checkName;
		this.message = message;
		this.metadata = (metadata != null) ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
	}

	public SuccessDetail(String checkName, String message) {
		this(checkName, message, Map.of());
	}

	public String getCheckName() {
		return this.checkName;
	}

	public String getMessage() {
		return this.message;
	}

	public Map<String, Object> getMetadata() {
		return Collections.unmodifiableMap(this.metadata);
	}

	public SuccessDetail withCheckName(String newCheckName) {
		return new SuccessDetail(newCheckName, this.message, this.metadata);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("check", this.checkName);
		map.put("message", this.message);
		map.put("metadata", this.metadata.isEmpty() ? null : this.metadata);
		return map;
	}

}
