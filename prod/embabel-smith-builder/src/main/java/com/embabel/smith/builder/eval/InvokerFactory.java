package com.embabel.smith.builder.eval;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Produces the {@link AgentSpecInvoker} for a given pack. The production implementation deploys the
 * pack and returns an invoker that runs it; because the pack's prompts change across refinement
 * rounds, a fresh invoker is built for each round's pack.
 */
@FunctionalInterface
public interface InvokerFactory {
    AgentSpecInvoker invokerFor(AgentSpec pack);
}
