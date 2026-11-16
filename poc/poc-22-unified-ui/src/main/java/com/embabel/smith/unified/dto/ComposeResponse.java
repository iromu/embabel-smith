/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The result of exporting a canvas: the composite's goal, steps, types, and written path.
 *
 * @param path The path the composite spec was written to.
 * @param goal The composite's exported goal.
 * @param steps The composite's steps.
 * @param types The composite's external types.
 * @param issues The validation issues.
 * @author Ivan Rodriguez
 */
public record ComposeResponse(String path, GoalInfo goal, List<String> steps, List<String> types, List<String> issues) {
}
