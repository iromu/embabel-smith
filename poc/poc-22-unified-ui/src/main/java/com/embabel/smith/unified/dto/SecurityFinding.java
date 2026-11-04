package com.embabel.smith.unified.dto;

/** One security finding: severity, risk category, a human message, and the concrete evidence. */
public record SecurityFinding(String severity, String category, String message, String evidence) {}
