package com.embabel.smith.studio.rest.dto;

/** A single security finding (mirrors {@code com.embabel.smith.builder.security.Finding}). */
public record SecurityFinding(String severity, String category, String message, String evidence) {
}
