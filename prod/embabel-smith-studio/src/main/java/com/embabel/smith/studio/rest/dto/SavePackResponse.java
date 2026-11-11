package com.embabel.smith.studio.rest.dto;

/**
 * The save-a-pack response: whether the pack was stored, the name it was saved under, and a
 * structured error.
 */
public record SavePackResponse(boolean ok, String name, String error) {

    public static SavePackResponse of(String name) {
        return new SavePackResponse(true, name, null);
    }

    public static SavePackResponse failed(String message) {
        return new SavePackResponse(false, null, message);
    }
}
