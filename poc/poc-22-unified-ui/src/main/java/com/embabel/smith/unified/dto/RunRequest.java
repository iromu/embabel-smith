package com.embabel.smith.unified.dto;

/** A request to run a agent spec over an input (the no-code runtime, POC 1). */
public record RunRequest(String specName, String input) {

    public String inputOrDefault() {
        return input == null ? "" : input;
    }
}
