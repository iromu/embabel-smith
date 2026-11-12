/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
