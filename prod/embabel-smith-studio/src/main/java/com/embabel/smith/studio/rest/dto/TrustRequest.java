package com.embabel.smith.studio.rest.dto;

/** A trust-tier check request (the Trust pane): the source, spec, and tier. Offline. */
public record TrustRequest(String source, String spec, String tier) {
}
