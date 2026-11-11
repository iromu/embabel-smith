package com.embabel.smith.studio.rest.dto;

import java.util.Map;

/**
 * The discover response: the loaded business-process documents (filename to content) and where they
 * came from. The workspace presents these and then generates from the same location.
 */
public record DiscoverResponse(
        String sourceType,
        String location,
        Map<String, String> docs,
        String error) {

    public static DiscoverResponse of(String sourceType, String location, Map<String, String> docs) {
        return new DiscoverResponse(sourceType, location, docs, null);
    }

    public static DiscoverResponse failed(String message) {
        return new DiscoverResponse(null, null, null, message);
    }
}
