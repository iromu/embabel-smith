package com.embabel.smith.ui.dto;

import java.util.List;

/** A canvas to validate: the placed specs (by name) and the connections between them. */
public record CanvasRequest(List<String> nodes, List<Edge> edges) {}
