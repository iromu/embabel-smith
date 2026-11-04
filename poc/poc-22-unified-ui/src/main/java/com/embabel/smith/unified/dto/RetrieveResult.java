package com.embabel.smith.unified.dto;

/**
 * One ranked retrieval hit: the skill, its retrieval text, its (curation-adjusted) score, its output
 * type, and its lifecycle state (POC 18 — the retrieval pane curates the library, not just scores it).
 */
public record RetrieveResult(String name, String description, double score, String outputType,
                             String state) {}
