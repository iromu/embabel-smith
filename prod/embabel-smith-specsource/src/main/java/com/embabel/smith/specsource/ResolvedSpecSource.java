/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

	/** Where a source came from and where it materialized on this machine.
	 * @param uri the source URI
	 * @param root the directory the source materialized at
	 */
	public record Origin(String uri, Path root) {
	}
}
