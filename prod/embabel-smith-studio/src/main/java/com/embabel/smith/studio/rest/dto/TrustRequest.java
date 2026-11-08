package com.embabel.smith.studio.rest.dto;

/** A trust-tier check request (the Trust pane): the source, skill, and tier. Offline. */
public record TrustRequest(String source, String skill, String tier) {
}
