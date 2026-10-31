package com.embabel.smith.ui.dto;

import java.util.List;

/** The result of exporting a canvas: the composite's goal, steps, types, and written path. */
public record ComposeResponse(
        String path,
        GoalInfo goal,
        List<String> steps,
        List<String> types,
        List<String> issues) {}
