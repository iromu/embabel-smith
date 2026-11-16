/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * The validation result for a canvas: whether it is a valid chain, the issues, and the
 * chain.
 *
 * @param valid whether the canvas is a valid chain
 * @param issues the validation issues found
 * @param chain the validated chain of spec names
 * @author Ivan Rodriguez
 */
public record CanvasResponse(boolean valid, List<String> issues, List<String> chain) {
}
