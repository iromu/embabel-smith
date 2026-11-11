package com.embabel.smith.studio.rest.dto;

/**
 * A recorded discovery in the durable history: when it happened, where the docs came from, and how
 * many documents were loaded.
 */
public record DiscoveryRecordDto(String timestamp, String sourceType, String location, int docCount) {
}
