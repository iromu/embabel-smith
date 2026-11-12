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

package com.embabel.smith.builder;

/**
 * The per-build knobs (design D1): the refinement iteration bound, the eval score gate,
 * and the publish flags. Defaults: up to 3 refinement rounds, a score gate of 1.0 (every
 * eval task must pass), no overwrite, no push.
 *
 * @param maxRefine the maximum number of refinement rounds to run.
 * @param scoreGate the minimum eval score required to pass the build gate.
 * @param overwrite whether to overwrite an existing published pack.
 * @param push whether to push the published pack to a remote.
 * @author Ivan Rodriguez
 */
public record BuildOptions(int maxRefine, double scoreGate, boolean overwrite, boolean push) {

	/** Default value for {@link #maxRefine}: up to 3 refinement rounds. */
	public static final int DEFAULT_MAX_REFINE = 3;

	/** Default value for {@link #scoreGate}: a score gate of 1.0. */
	public static final double DEFAULT_SCORE_GATE = 1.0;

	public static BuildOptions defaults() {
		return new BuildOptions(DEFAULT_MAX_REFINE, DEFAULT_SCORE_GATE, false, false);
	}
}
