package com.embabel.smith.studio.rest.dto;

/**
 * The discover request: a local docs {@code path} or a repository {@code url} to load the
 * business-process documents from. The path wins when both are set.
 */
public record DiscoverRequest(String path, String url) {
}
