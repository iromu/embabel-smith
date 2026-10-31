package com.embabel.smith.eval;

/**
 * Abstracts "run the goal-type skill on an input and return the goal output's content." The
 * endpoint-gated production implementation is {@link AgentInvocationSkillInvoker} (via
 * {@code AgentInvocation}); offline tests supply a fake so the eval/scoring path is verifiable
 * without a live model.
 */
@FunctionalInterface
public interface SkillInvoker {

    String invoke(String input);
}
