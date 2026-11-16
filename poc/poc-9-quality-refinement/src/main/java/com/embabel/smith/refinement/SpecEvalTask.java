/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.refinement;

import java.util.function.Predicate;

/**
 * One eval task for a goal-type spec: the input, a human-readable acceptance criterion
 * (fed to the rewriter when the task fails), and a deterministic acceptance check on the
 * goal output.
 *
 * @param input the eval input fed to the agent
 * @param acceptanceCriteria the human-readable acceptance criterion
 * @param acceptance the deterministic acceptance check on the goal output
 * @author Ivan Rodriguez
 */
public record SpecEvalTask(String input, String acceptanceCriteria, Predicate<String> acceptance) {
}
