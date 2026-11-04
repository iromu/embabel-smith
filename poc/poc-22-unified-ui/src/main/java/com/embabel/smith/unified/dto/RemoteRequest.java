package com.embabel.smith.unified.dto;

/** A request to invoke the remote action on the backing sidecar. */
public record RemoteRequest(String input) {

    public String inputOrDefault() {
        return input == null ? "" : input;
    }
}
