/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the operator's bridge configuration — a comma-separated list of {@code name@url}
 * entries, the shape {@code smith.studio.sources} already uses for sources. An entry with
 * no {@code @} (or an empty side of one) is reported as malformed rather than guessed at:
 * inventing a name for a URL, or a URL for a name, would contact a host nobody
 * configured.
 *
 * @author Ivan Rodriguez
 */
public final class RemoteBridges {

	private RemoteBridges() {
	}

	/**
	 * Splits a comma-separated {@code name@url} list. Blank entries are an unset slot in
	 * the list and are skipped; anything else that lacks a {@code @} with text on both
	 * sides is malformed.
	 * @param configured the raw configuration value, possibly null or empty
	 * @return the parsed bridges and the entries that could not be parsed
	 */
	public static Parsed parse(String configured) {
		List<RemoteBridge> bridges = new ArrayList<>();
		List<String> malformed = new ArrayList<>();
		if (configured == null) {
			return new Parsed(List.of(), List.of());
		}
		for (String entry : configured.split(",")) {
			String trimmed = entry.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			int at = trimmed.indexOf('@');
			String name = (at < 0) ? "" : trimmed.substring(0, at).trim();
			String baseUrl = (at < 0) ? "" : trimmed.substring(at + 1).trim();
			if (name.isEmpty() || baseUrl.isEmpty()) {
				malformed.add(trimmed);
				continue;
			}
			// A trailing slash would put an empty segment in front of the contract path
			// and a
			// running sidecar would read as dead, so the base is normalised here.
			bridges.add(new RemoteBridge(name,
					baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl));
		}
		return new Parsed(List.copyOf(bridges), List.copyOf(malformed));
	}

	/**
	 * The configured bridges, plus the entries that could not be read as
	 * {@code name@url}.
	 *
	 * @param bridges the entries that parsed, in configured order
	 * @param malformed the raw entries that did not, reported rather than dropped
	 * @author Ivan Rodriguez
	 */
	public record Parsed(List<RemoteBridge> bridges, List<String> malformed) {
	}

}
