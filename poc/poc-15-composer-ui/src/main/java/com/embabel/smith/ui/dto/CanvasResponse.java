package com.embabel.smith.ui.dto;

import java.util.List;

/** The validation result for a canvas: whether it is a valid chain, the issues, and the chain. */
public record CanvasResponse(boolean valid, List<String> issues, List<String> chain) {}
