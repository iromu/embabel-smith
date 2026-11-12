package com.embabel.smith.disclosure;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Measures the context cost of progressive vs. eager disclosure for a spec that ships a bundled
 * reference (POC 11). Pure and offline.
 */
public class ProgressiveDisclosureRunner {

    private final PlanContextMeter meter = new PlanContextMeter();

    /**
     * The measured context costs for a spec with a bundled reference of {@code referenceChars}.
     *
     * @param planContextChars      bounded, independent of the reference (what the planner loads)
     * @param progressivePromptChars base prompt only (reference fetched on demand)
     * @param eagerPromptChars      base prompt + inlined reference
     * @param referenceChars        size of the bundled reference
     */
    public record ContextCost(int planContextChars, int progressivePromptChars,
                              int eagerPromptChars, int referenceChars) {

        /** How many times larger the eager execution context is than the progressive one. */
        public double eagerToProgressiveRatio() {
            return progressivePromptChars == 0 ? 0.0 : (double) eagerPromptChars / progressivePromptChars;
        }
    }

    public ContextCost measure(AgentSpec pack, String reference) {
        return new ContextCost(
                meter.planContextChars(pack),
                meter.progressivePromptChars(pack),
                meter.eagerPromptChars(pack, reference),
                reference.length());
    }
}
