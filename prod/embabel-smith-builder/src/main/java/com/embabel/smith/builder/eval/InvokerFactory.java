package com.embabel.smith.builder.eval;

import com.embabel.smith.common.model.SkillPack;

/**
 * Produces the {@link SkillInvoker} for a given pack. The production implementation deploys the
 * pack and returns an invoker that runs it; because the pack's prompts change across refinement
 * rounds, a fresh invoker is built for each round's pack.
 */
@FunctionalInterface
public interface InvokerFactory {
    SkillInvoker invokerFor(SkillPack pack);
}
