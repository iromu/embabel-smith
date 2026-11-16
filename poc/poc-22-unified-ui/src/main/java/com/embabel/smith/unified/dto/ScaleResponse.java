/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The at-scale retrieval measurement (POC 12): precision@1 under the active scorer for
 * the core library and for the full library (core + near-duplicates — the shadowing
 * pressure), plus a sampled growth curve.
 *
 * @param scorer The active scorer name.
 * @param coreSize The core library size.
 * @param coreP1 The precision@1 for the core library.
 * @param fullSize The full library size.
 * @param fullP1 The precision@1 for the full library.
 * @param curve The sampled growth curve.
 * @author Ivan Rodriguez
 */
public record ScaleResponse(String scorer, int coreSize, double coreP1, int fullSize, double fullP1,
		List<ScalePoint> curve) {
}
