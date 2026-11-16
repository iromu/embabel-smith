/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.util.List;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The result of the bounded refinement loop: the (possibly refined) pack, its final
 * composite eval score, how many prompt-rewrite iterations were applied, the tasks still
 * failing, and whether the final score met the gate.
 *
 * @param pack the (possibly refined) pack
 * @param score the final composite eval score
 * @param refinementCount the number of prompt-rewrite iterations applied
 * @param failingTasks the tasks still failing at the end of the loop
 * @param passed whether the final score met the gate
 * @author Ivan Rodriguez
 */
public record RefinementOutcome(AgentSpec pack, double score, int refinementCount, List<String> failingTasks,
		boolean passed) {
}
