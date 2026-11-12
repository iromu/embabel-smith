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

package com.embabel.smith.studio.improve;

import java.time.Instant;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The lifecycle state machine (POC 18, carried into the product): how a spec's state
 * moves as it is improved and regresses.
 *
 * <pre>
 *   first proven improvement          regression (re-eval &lt; baseline)
 * incubating -----------------------> active -----------------------> degraded
 *                                        ^                               |
 *                                        |                               | next re-eval fails
 *                                        | gated candidate published     v
 *                                        +----------------------------- retired
 * </pre>
 *
 * @author Ivan Rodriguez
 */
public final class LifecycleTransitions {

	private LifecycleTransitions() {
	}

	/**
	 * The state a published (gated) candidate moves the pack to: a first proven
	 * improvement promotes {@code incubating} to {@code active}; a recovered
	 * {@code degraded} pack also returns to {@code active}; an already-{@code active}
	 * pack stays {@code active}. (A {@code retired} pack is only ever republished by an
	 * explicit human rebuild, which revives it.)
	 * @param current the pack's current lifecycle state
	 * @return the state a published candidate moves the pack to.
	 */
	public static LifecycleState onPublished(LifecycleState current) {
		return switch (current) {
			case INCUBATING, DEGRADED, ACTIVE, RETIRED -> LifecycleState.ACTIVE;
		};
	}

	/**
	 * The state a live re-evaluation below baseline moves the pack to: an {@code active}
	 * pack degrades; a {@code degraded} pack that fails its next re-evaluation is
	 * retired.
	 * @param current the pack's current lifecycle state
	 * @return the state a below-baseline re-evaluation moves the pack to.
	 */
	public static LifecycleState onRegression(LifecycleState current) {
		return switch (current) {
			case ACTIVE -> LifecycleState.DEGRADED;
			case DEGRADED -> LifecycleState.RETIRED;
			case INCUBATING, RETIRED -> current;
		};
	}

	/**
	 * The sidecar for a published candidate: the new content hash, the new baseline (the
	 * candidate's re-eval score), and the state it transitions to.
	 * @param candidate the published candidate pack
	 * @param newBaseline the candidate's re-eval score, the new baseline
	 * @param state the state the pack transitions to
	 * @return the sidecar for the published candidate.
	 */
	public static LifecycleMetadata sidecarFor(AgentSpec candidate, double newBaseline, LifecycleState state) {
		return new LifecycleMetadata(SpecContentHash.of(candidate), newBaseline, state, Instant.now().toString());
	}

	/**
	 * The sidecar after a regression: the same content hash and baseline, but the state
	 * moved per {@link #onRegression} (the baseline is unchanged — the pack's content did
	 * not change, only its measured score).
	 * @param sidecar the pack's current lifecycle sidecar
	 * @return the sidecar after a regression.
	 */
	public static LifecycleMetadata regressed(LifecycleMetadata sidecar) {
		return new LifecycleMetadata(sidecar.contentHash(), sidecar.baselinePassRate(), onRegression(sidecar.state()),
				Instant.now().toString());
	}

}
