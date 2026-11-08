package com.embabel.smith.studio.rest.dto;

/** The result of approving a pending candidate: where it was published, or the error. */
public record ApproveResponse(boolean ok, String location, String error) {
}
