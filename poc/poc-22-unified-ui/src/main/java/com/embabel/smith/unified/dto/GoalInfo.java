/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * The composite's exported goal.
 *
 * @param name The goal name.
 * @param outputType The goal's output type.
 * @param export Whether the goal is exported.
 * @author Ivan Rodriguez
 */
public record GoalInfo(String name, String outputType, boolean export) {
}
