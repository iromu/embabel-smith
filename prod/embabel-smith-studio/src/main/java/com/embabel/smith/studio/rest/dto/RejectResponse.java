package com.embabel.smith.studio.rest.dto;

/** The result of rejecting a pending candidate (discarded, no publish). */
public record RejectResponse(boolean ok, String error) {
}
