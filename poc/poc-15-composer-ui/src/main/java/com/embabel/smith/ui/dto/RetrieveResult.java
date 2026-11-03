package com.embabel.smith.ui.dto;

/** One ranked retrieval hit: the skill, its retrieval text, its score, and its output type. */
public record RetrieveResult(String name, String description, double score, String outputType) {}
