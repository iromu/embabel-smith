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
