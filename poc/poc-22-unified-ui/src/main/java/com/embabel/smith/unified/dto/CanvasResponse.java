/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The validation result for a canvas: whether it is a valid chain, the issues, and the
 * chain.
 *
 * @param valid Whether the canvas is a valid chain.
 * @param issues The validation issues.
 * @param chain The ordered chain of specs.
 * @author Ivan Rodriguez
 */
public record CanvasResponse(boolean valid, List<String> issues, List<String> chain) {
}
