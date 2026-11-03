package com.embabel.smith.ui.dto;

/** A natural-language skill description to compile into a draft, plus an optional agent name. */
public record CompileRequest(String description, String agentName) {

    public String agentNameOrDefault() {
        return agentName == null || agentName.isBlank() ? "draft-agent" : agentName;
    }
}
