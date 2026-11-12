package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The Compose pane's export request (change 07): an assembled agent spec — a goal plus its action
 * steps wired by type names — and the source to publish it to. The pack is fully specified by the
 * client (the flow was already authored in the graph editor); the controller only validates its
 * shape and writes it.
 */
public record ComposeRequest(
        String source,
        AgentSpec pack,
        Boolean overwrite,
        Boolean push) {
}
