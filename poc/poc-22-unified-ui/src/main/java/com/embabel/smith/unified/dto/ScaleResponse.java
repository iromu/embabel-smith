package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The at-scale retrieval measurement (POC 12): precision@1 under the active scorer for the core
 * library and for the full library (core + near-duplicates — the shadowing pressure), plus a sampled
 * growth curve.
 */
public record ScaleResponse(
        String scorer,
        int coreSize,
        double coreP1,
        int fullSize,
        double fullP1,
        List<ScalePoint> curve) {}
