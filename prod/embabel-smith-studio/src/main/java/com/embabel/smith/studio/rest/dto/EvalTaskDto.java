/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * One acceptance case for evaluating a spec (mirrors the builder's {@code EvalTask}).
 *
 * @param name the task name.
 * @param input the input for the task.
 * @param expected the expected output.
 * @author Ivan Rodriguez
 */
public record EvalTaskDto(String name, String input, String expected) {
}
