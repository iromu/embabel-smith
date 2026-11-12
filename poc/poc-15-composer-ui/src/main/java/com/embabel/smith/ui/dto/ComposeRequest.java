package com.embabel.smith.ui.dto;

import java.util.List;

/** A canvas to export as a composite spec, named {@code name}. */
public record ComposeRequest(String name, List<String> nodes, List<Edge> edges) {}
