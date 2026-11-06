package com.embabel.smith.builder.eval;

/**
 * Runs the built skill once and returns the goal output's content. The production implementation
 * deploys the pack and invokes it as a single typed goal run (endpoint-gated); offline tests
 * inject a fake so the evaluate → score → refine flow is verifiable without a live model.
 */
@FunctionalInterface
public interface SkillInvoker {
    String invoke(String input);
}
