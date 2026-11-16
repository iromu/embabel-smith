/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.lifecycle;

import java.util.function.Predicate;

/**
 * One eval task for the spec: the input, a human-readable acceptance criterion, and a
 * deterministic acceptance check on the goal output (same shape as POC 9's task).
 *
 * @param input the input to feed the spec.
 * @param acceptanceCriteria the human-readable acceptance criterion.
 * @param acceptance the deterministic check on the goal output.
 * @author Ivan Rodriguez
 */
public record SpecEvalTask(String input, String acceptanceCriteria, Predicate<String> acceptance) {
}
