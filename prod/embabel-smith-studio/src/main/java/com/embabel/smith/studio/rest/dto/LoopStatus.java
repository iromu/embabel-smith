package com.embabel.smith.studio.rest.dto;

/** The loop's schedule + auto-publish status (the Auto-improve pane). */
public record LoopStatus(boolean running, boolean autoPublish) {
}
