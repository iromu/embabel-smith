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

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.TraceRecord;

/**
 * Decides whether a spec needs improvement, and why. It is a pure function of the spec's
 * trace window, its current pack content, and its lifecycle sidecar — no model calls, so
 * it is fully offline-testable.
 *
 * <p>
 * Three triggers (spec "Detection triggers"):
 * <ul>
 * <li><b>failed runs</b> — recent traces that failed in a way that is <em>not</em> a
 * trust-tier block (a tier block is a policy outcome, not a spec defect refinement can
 * fix);</li>
 * <li><b>content drift</b> — the current pack's content hash differs from the sidecar's
 * recorded hash (the pack changed since its baseline was recorded);</li>
 * <li><b>below baseline</b> — an injected live re-evaluation scores below the sidecar's
 * baseline (regression); supplied as a hook so it is testable without a model.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public final class Detector {

	/**
	 * The prefix the runtime records for a trust-tier block (see
	 * {@code TrustTierBlockedException}).
	 */
	public static final String TIER_BLOCK_PREFIX = "Run blocked by trust tier";

	/**
	 * Detects without a live re-eval (the below-baseline trigger is disabled).
	 * @param spec the spec being detected
	 * @param current the pack's current content
	 * @param sidecar the pack's lifecycle sidecar
	 * @param window the spec's trace window
	 * @return the detection outcome.
	 */
	public Result detect(String spec, AgentSpec current, LifecycleMetadata sidecar, List<TraceRecord> window) {
		return detect(spec, current, sidecar, window, null);
	}

	/**
	 * Detects using the supplied per-call re-eval hook (the "re-eval &lt; baseline"
	 * trigger); pass {@code null} to disable the below-baseline trigger for this call.
	 * The failed-run and content-drift triggers are always evaluated.
	 * @param spec the spec being detected
	 * @param current the pack's current content
	 * @param sidecar the pack's lifecycle sidecar
	 * @param window the spec's trace window
	 * @param hook the live re-eval hook, or null to disable the below-baseline trigger
	 * @return the detection outcome.
	 */
	public Result detect(String spec, AgentSpec current, LifecycleMetadata sidecar, List<TraceRecord> window,
			ReevalHook hook) {
		List<String> reasons = new ArrayList<>();
		List<TraceRecord> failures = failedNonTierBlock(window);
		boolean regressed = false;
		if (!failures.isEmpty()) {
			reasons.add("failed runs");
		}
		if (hasDrift(current, sidecar)) {
			reasons.add("content drift");
		}
		if (hook != null && belowBaseline(hook.score(spec), sidecar.baselinePassRate())) {
			regressed = true;
			reasons.add("below baseline");
		}
		return new Result(!reasons.isEmpty(), regressed, List.copyOf(reasons), List.copyOf(failures));
	}

	/**
	 * The failed runs in the window that are <em>not</em> trust-tier blocks. A tier block
	 * is a policy outcome (the granted tier does not allow the pack's tool groups) and
	 * cannot be fixed by refining prompts, so it is excluded from the refinement
	 * evidence.
	 * @param window the spec's trace window
	 * @return the failed runs that are not trust-tier blocks.
	 */
	public static List<TraceRecord> failedNonTierBlock(List<TraceRecord> window) {
		List<TraceRecord> out = new ArrayList<>();
		for (TraceRecord record : window) {
			if (record.success()) {
				continue;
			}
			if (isTierBlock(record)) {
				continue;
			}
			out.add(record);
		}
		return out;
	}

	/**
	 * A failed run caused by the trust-tier gate (a policy block, not a spec defect).
	 * @param record the trace record to test
	 * @return {@code true} when the record is a trust-tier block.
	 */
	public static boolean isTierBlock(TraceRecord record) {
		return !record.success() && record.error() != null && record.error().startsWith(TIER_BLOCK_PREFIX);
	}

	/**
	 * Content drift: the current pack's content hash differs from the sidecar's recorded
	 * hash.
	 * @param current the pack's current content
	 * @param sidecar the pack's lifecycle sidecar
	 * @return {@code true} when the content has drifted from the recorded hash.
	 */
	public static boolean hasDrift(AgentSpec current, LifecycleMetadata sidecar) {
		return !SpecContentHash.of(current).equals(sidecar.contentHash());
	}

	/**
	 * A re-eval that does not reach the baseline (the "re-eval &lt; baseline" trigger).
	 * @param reevalScore the live re-eval score
	 * @param baseline the pack's baseline pass-rate
	 * @return {@code true} when the re-eval is below the baseline.
	 */
	public static boolean belowBaseline(double reevalScore, double baseline) {
		return reevalScore < baseline;
	}

	/** The "live re-eval &lt; baseline" hook: returns the pack's current pass-rate. */
	@FunctionalInterface
	public interface ReevalHook {

		double score(String spec);

	}

	/**
	 * The detection outcome: whether the spec is queued, whether it regressed, why, and
	 * the failure traces to refine from.
	 * @param queued whether the spec is queued for improvement
	 * @param regressed whether the spec regressed below its baseline
	 * @param reasons the human-readable reasons for queuing
	 * @param failureTraces the failure traces to refine from
	 */
	public record Result(boolean queued, boolean regressed, List<String> reasons, List<TraceRecord> failureTraces) {

		public static Result clean() {
			return new Result(false, false, List.of(), List.of());
		}
	}

}
