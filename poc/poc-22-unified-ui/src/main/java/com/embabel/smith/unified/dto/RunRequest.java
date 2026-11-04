package com.embabel.smith.unified.dto;

/** A request to run a skill-pack over an input (the no-code runtime, POC 1). */
public record RunRequest(String skillName, String input) {

    public String inputOrDefault() {
        return input == null ? "" : input;
    }
}
