package com.embabel.smith.studio.generate;

/**
 * The model seam for generation: a system prompt plus a user message in, the raw completion
 * out. Endpoint-gated in the production implementation; a stub stands in for offline tests.
 */
@FunctionalInterface
public interface FlowPromptCaller {
    String call(String system, String user);
}
