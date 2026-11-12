package com.embabel.smith.ui.dto;

import java.util.List;

/** A spec offered by the palette: its name, the types it consumes from outside, and its output. */
public record SpecInfo(String name, List<String> externalInputTypes, String outputType) {}
