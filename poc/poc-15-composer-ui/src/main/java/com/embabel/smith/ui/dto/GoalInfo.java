/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

/**
 * The composite's exported goal.
 *
 * @param name the goal name
 * @param outputType the goal's output type
 * @param export whether the goal is exported
 * @author Ivan Rodriguez
 */
public record GoalInfo(String name, String outputType, boolean export) {
}
