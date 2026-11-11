package com.embabel.smith.studio.rest.dto;

/**
 * The extract request: a local docs {@code path} or a repository {@code url} to load the
 * business-process documents from and run the discovery prompt over. The path wins when both are
 * set.
 */
public record ExtractRequest(String path, String url) {
}
