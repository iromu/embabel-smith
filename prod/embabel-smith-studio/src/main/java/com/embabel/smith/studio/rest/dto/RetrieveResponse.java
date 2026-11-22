/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The ranked, curated retrieval results and the name of the scorer that produced them —
 * or the structured error, on the shape every other source-backed pane uses, so a source
 * the studio cannot resolve surfaces as a banner rather than a thrown request.
 *
 * @param error the structured error (null on success).
 * @param scorer the active scorer's name (null on failure).
 * @param results the ranked hits, empty for a blank query or a source with no packs.
 * @author Ivan Rodriguez
 */
public record RetrieveResponse(String error, String scorer, List<RetrieveResult> results) {

	public static RetrieveResponse ok(String scorer, List<RetrieveResult> results) {
		return new RetrieveResponse(null, scorer, results);
	}

	public static RetrieveResponse error(String error) {
		return new RetrieveResponse(error, null, List.of());
	}
}
