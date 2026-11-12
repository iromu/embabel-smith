package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The simulate request: the generated (or edited) {@link AgentSpec} to dry-run, and the sample
 * {@code input} bound to its {@code UserInput}. The pack is run in a throwaway platform; nothing is
 * published.
 */
public record SimulateRequest(AgentSpec pack, String input) {
}
