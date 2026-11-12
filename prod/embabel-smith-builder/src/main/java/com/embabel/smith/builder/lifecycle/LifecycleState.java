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

package com.embabel.smith.builder.lifecycle;

/**
 * The lifecycle of a spec in the library (TrackRL-style: incubation → retention →
 * retirement). A spec's <em>contract</em> is its eval pass-rate; drift is the divergence
 * of its content or its score from the recorded baseline (doc 02 §5 caveat 4, doc 03 §1
 * "Spec decay / lifecycle"). Extracted from POC 18.
 *
 * @author Ivan Rodriguez
 */
public enum LifecycleState {

	/** New; not yet validated by a passing baseline eval. */
	INCUBATING,

	/** Baseline eval passed and no drift — the spec is trusted. */
	ACTIVE,

	/** Drift detected (content or score) but the spec still functions — needs re-eval. */
	DEGRADED,

	/**
	 * The spec's contract is violated (its score collapsed) — terminal, out of the
	 * library.
	 */
	RETIRED

}
