package com.embabel.smith.unified.dto;

import java.util.List;

/** A canvas to validate: the placed skills (by name) and the connections between them. */
public record CanvasRequest(List<String> nodes, List<Edge> edges) {}
