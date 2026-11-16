/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * The result of exporting a canvas: the composite's goal, steps, types, and written path.
 *
 * @param path the file path the composite spec was written to
 * @param goal the composite's exported goal
 * @param steps the ordered step names of the composite
 * @param types the types involved in the composite
 * @param issues the export issues, if any
 * @author Ivan Rodriguez
 */
public record ComposeResponse(String path, GoalInfo goal, List<String> steps, List<String> types, List<String> issues) {
}
