/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * A point on the precision curve: the library size and precision@1 across the benchmark.
 *
 * @param librarySize The library size at this point.
 * @param precisionAt1 The precision@1 at this library size.
 * @author Ivan Rodriguez
 */
public record ScalePoint(int librarySize, double precisionAt1) {
}
