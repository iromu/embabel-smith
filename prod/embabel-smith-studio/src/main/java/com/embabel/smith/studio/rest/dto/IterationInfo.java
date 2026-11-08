package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The outcome of one loop iteration (the Auto-improve pane's on-demand trigger): how many
 * candidates were gated, and which skills were published (auto-publish) or queued for approval.
 * {@code ran} is false (with a structured {@code errorCode}) when the iteration could not proceed,
 * e.g. no model endpoint is configured.
 */
public record IterationInfo(boolean ran, int candidates, List<String> publishedSkills,
                            List<String> queuedSkills, boolean autoPublish, String errorCode,
                            String error) {
}
