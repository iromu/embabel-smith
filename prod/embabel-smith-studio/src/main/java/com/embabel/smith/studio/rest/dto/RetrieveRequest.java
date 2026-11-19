/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A retrieval query (the Retrieve pane): which source to search, the natural-language
 * task, and how many packs to return.
 *
 * @param source the source URI to rank the packs of.
 * @param query the natural-language task, or blank for no results.
 * @param k how many packs to return, or {@code null} for the default.
 * @author Ivan Rodriguez
 */
public record RetrieveRequest(String source, String query, Integer k) {

	/** What a request that names no {@code k} asks for. */
	private static final int DEFAULT_K = 10;

	/**
	 * The most hits one request may ask for, so a typo cannot ask for the whole library.
	 */
	static final int MAX_K = 50;

	/**
	 * The {@code k} to rank against: the requested one, or the default when it is absent
	 * or not positive, capped at {@link #MAX_K}.
	 * @return the effective number of hits to return.
	 */
	public int effectiveK() {
		int wanted = (this.k != null && this.k > 0) ? this.k : DEFAULT_K;
		return Math.min(wanted, MAX_K);
	}
}
