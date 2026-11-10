package com.embabel.smith.studio.rest.dto;

/**
 * The Compose pane's export result: where the pack landed, the goal it exposes, and its lifecycle
 * stamp. A fresh composed pack is stamped {@code INCUBATING} with a 0.0 baseline (not yet eval'd);
 * the improvement loop establishes the real baseline later.
 */
public record ComposeResponse(
        boolean ok,
        String packName,
        String goal,
        PublishInfo published,
        LifecycleInfo lifecycle,
        String error) {

    public static ComposeResponse published(String packName, String goal,
                                            PublishInfo published, LifecycleInfo lifecycle) {
        return new ComposeResponse(true, packName, goal, published, lifecycle, null);
    }

    public static ComposeResponse failed(String error) {
        return new ComposeResponse(false, null, null, null, null, error);
    }
}
