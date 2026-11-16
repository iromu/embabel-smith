/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.eval;

import java.util.function.Predicate;

/**
 * One evaluation task for a goal-type (non-chat) spec: a user input plus the acceptance
 * check on the spec's goal output. This is the goal-type analogue of the conversational
 * eval's {@code Task} (design D6 — the eval is adapted, not assumed): instead of scoring
 * a chat transcript, we score the single goal output the spec produces for the input.
 *
 * @param input the user input to evaluate
 * @param acceptanceCriteria the acceptance criteria description for the task
 * @param acceptance the acceptance check on the spec's goal output
 * @author Ivan Rodriguez
 */
public record SpecEvalTask(String input, String acceptanceCriteria, Predicate<String> acceptance) {
}
