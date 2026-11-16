/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.specsource;

import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of resolving a {@link SpecSource}: where the source materialized, the agent
 * specs found in it, and any non-fatal warnings encountered along the way.
 *
 * <p>
 * {@code packs} holds only packs that passed agent spec structure validation; candidates
 * that failed validation are excluded and named in {@code warnings} instead.
 *
 * @param origin where the source came from and where it materialized
 * @param packs the agent specs found in the source
 * @param warnings non-fatal warnings encountered during resolution
 * @author Ivan Rodriguez
 */
public record ResolvedSpecSource(Origin origin, List<ResolvedSpec> packs, List<String> warnings) {

	public ResolvedSpecSource {
		packs = (packs != null) ? List.copyOf(packs) : List.of();
		warnings = (warnings != null) ? List.copyOf(warnings) : List.of();
	}

	/**
	 * Where a source came from and where it materialized on this machine.
	 *
	 * @param uri the source URI
	 * @param root the directory the source materialized at
	 */
	public record Origin(String uri, Path root) {
	}
}
