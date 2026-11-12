package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;

/** The lifecycle state of a agent spec (mirrors {@code LifecycleMetadata}). */
public record LifecycleInfo(String contentHash, double baselinePassRate, String state, String updatedAt) {

    public static LifecycleInfo of(LifecycleMetadata metadata) {
        return new LifecycleInfo(metadata.contentHash(), metadata.baselinePassRate(),
                metadata.state().name(), metadata.updatedAt());
    }
}
