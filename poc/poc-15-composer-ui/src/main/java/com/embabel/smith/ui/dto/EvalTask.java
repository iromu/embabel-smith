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

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * One eval task as submitted from the canvas: a user input, a human-readable acceptance
 * criterion (fed to the prompt rewriter when the task fails), and the substring the goal
 * output must contain for the task to pass — a simple, JSON-serializable stand-in for the
 * deterministic acceptance check.
 *
 * @param input the user input to run
 * @param acceptanceCriteria the human-readable acceptance criterion
 * @param mustContain the substring the goal output must contain to pass
 * @author Ivan Rodriguez
 */
public record EvalTask(String input, String acceptanceCriteria, String mustContain) {

	/**
	 * The default task set when the request supplies none — a couple of goal-type probes.
	 */
	public static final List<EvalTask> DEFAULTS = List.of(
			new EvalTask("Explain how a coffee machine works", "a plain-language explainer about coffee machines",
					"coffee"),
			new EvalTask("Explain what a black hole is", "an explanation of black holes", "black hole"));

}
