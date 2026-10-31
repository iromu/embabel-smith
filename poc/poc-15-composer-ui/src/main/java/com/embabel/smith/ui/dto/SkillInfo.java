package com.embabel.smith.ui.dto;

import java.util.List;

/** A skill offered by the palette: its name, the types it consumes from outside, and its output. */
public record SkillInfo(String name, List<String> externalInputTypes, String outputType) {}
