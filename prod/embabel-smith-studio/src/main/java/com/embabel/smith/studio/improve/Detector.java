package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.TraceRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a spec needs improvement, and why. It is a pure function of the spec's trace
 * window, its current pack content, and its lifecycle sidecar — no model calls, so it is fully
 * offline-testable.
 *
 * <p>Three triggers (spec "Detection triggers"):
 * <ul>
 *   <li><b>failed runs</b> — recent traces that failed in a way that is <em>not</em> a trust-tier
 *       block (a tier block is a policy outcome, not a spec defect refinement can fix);</li>
 *   <li><b>content drift</b> — the current pack's content hash differs from the sidecar's recorded
 *       hash (the pack changed since its baseline was recorded);</li>
 *   <li><b>below baseline</b> — an injected live re-evaluation scores below the sidecar's baseline
 *       (regression); supplied as a hook so it is testable without a model.</li>
 * </ul>
 */
public final class Detector {

    /** The prefix the runtime records for a trust-tier block (see {@code TrustTierBlockedException}). */
    public static final String TIER_BLOCK_PREFIX = "Run blocked by trust tier";

    /** The "live re-eval &lt; baseline" hook: returns the pack's current pass-rate. */
    @FunctionalInterface
    public interface ReevalHook {
        double score(String spec);
    }

    /** The detection outcome: whether the spec is queued, whether it regressed, why, and the failure traces to refine from. */
    public record Result(boolean queued, boolean regressed, List<String> reasons,
                         List<TraceRecord> failureTraces) {

        public static Result clean() {
            return new Result(false, false, List.of(), List.of());
        }
    }

    /** Detects without a live re-eval (the below-baseline trigger is disabled). */
    public Result detect(String spec, AgentSpec current, LifecycleMetadata sidecar,
                         List<TraceRecord> window) {
        return detect(spec, current, sidecar, window, null);
    }

    /**
     * Detects using the supplied per-call re-eval hook (the "re-eval &lt; baseline" trigger); pass
     * {@code null} to disable the below-baseline trigger for this call. The failed-run and
     * content-drift triggers are always evaluated.
     */
    public Result detect(String spec, AgentSpec current, LifecycleMetadata sidecar,
                         List<TraceRecord> window, ReevalHook hook) {
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
     * The failed runs in the window that are <em>not</em> trust-tier blocks. A tier block is a
     * policy outcome (the granted tier does not allow the pack's tool groups) and cannot be fixed by
     * refining prompts, so it is excluded from the refinement evidence.
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

    /** A failed run caused by the trust-tier gate (a policy block, not a spec defect). */
    public static boolean isTierBlock(TraceRecord record) {
        return !record.success()
                && record.error() != null
                && record.error().startsWith(TIER_BLOCK_PREFIX);
    }

    /** Content drift: the current pack's content hash differs from the sidecar's recorded hash. */
    public static boolean hasDrift(AgentSpec current, LifecycleMetadata sidecar) {
        return !SpecContentHash.of(current).equals(sidecar.contentHash());
    }

    /** A re-eval that does not reach the baseline (the "re-eval &lt; baseline" trigger). */
    public static boolean belowBaseline(double reevalScore, double baseline) {
        return reevalScore < baseline;
    }
}
