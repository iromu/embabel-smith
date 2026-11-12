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
