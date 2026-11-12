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

/**
 * One acceptance case for the built spec: an {@code input} to run the spec on and the
 * {@code expected} substring that a passing run's output must contain. The task set is a
 * parameter of the build (design D1) — the builder does not hardcode it.
 *
 * @param name the task's name
 * @param input the input to run the spec on
 * @param expected the substring a passing run's output must contain
 * @author Ivan Rodriguez
 */
public record EvalTask(String name, String input, String expected) {

	/**
	 * The default smoke task set: run the spec on a generic prompt and require that it
	 * produces non-blank output (a blank {@code expected} means "produced output", see
	 * {@link EvalScorer}). Centralized here so the CLI, the studio's builder/eval/loop
	 * wiring, and the eval fallback all share one definition instead of drifting apart.
	 */
	public static List<EvalTask> smokeDefault() {
		return List.of(new EvalTask("runs-and-produces-output", "Describe your use case.", ""));
	}
}
