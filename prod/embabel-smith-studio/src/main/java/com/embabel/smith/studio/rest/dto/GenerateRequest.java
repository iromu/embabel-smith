package com.embabel.smith.studio.rest.dto;

/**
 * The generate request: a blueprint either as a {@code blueprint} string or as a {@code path} to a
 * directory of business-process documents (the server reads the docs). Exactly one should be set;
 * the path wins when both are.
 */
public record GenerateRequest(String blueprint, String path) {
}
