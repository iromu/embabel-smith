package com.embabel.smith.studio.rest.dto;

/** A security-scan request (the Security pane): the source and spec to scan. Offline. */
public record SecurityRequest(String source, String spec) {
}
