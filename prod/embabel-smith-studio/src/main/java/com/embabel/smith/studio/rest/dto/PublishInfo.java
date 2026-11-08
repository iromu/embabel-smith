package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.builder.publish.PublishResult;

/** Where a pack was published (mirrors {@code PublishResult}). */
public record PublishInfo(String location, boolean pushed) {

    public static PublishInfo of(PublishResult result) {
        return new PublishInfo(result.location().toString(), result.pushed());
    }
}
