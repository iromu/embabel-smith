package com.embabel.smith.studio.rest.dto;

/**
 * A build request (the Author pane): a natural-language description of the spec, the source URI to
 * publish to, and optional build knobs (refinement bound, overwrite, push).
 */
public record BuildRequest(String description, String outputSource, Integer maxRefine,
                          Boolean overwrite, Boolean push) {
}
