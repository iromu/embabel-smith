package com.embabel.smith.builder.eval;

/**
 * Rewrites an action step's prompt in response to eval failures (extracted from POC 9's refiner).
 * The production implementation asks the model for a revised prompt; offline tests inject a fake.
 *
 * @param currentPrompt   the prompt to revise
 * @param failureFeedback a description of the failing eval tasks
 * @return the revised prompt
 */
@FunctionalInterface
public interface PromptRewriter {
    String rewrite(String currentPrompt, String failureFeedback);
}
