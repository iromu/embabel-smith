package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The save-a-pack request: the {@link AgentSpec} to persist to the durable store.
 */
public record SavePackRequest(AgentSpec pack) {
}
