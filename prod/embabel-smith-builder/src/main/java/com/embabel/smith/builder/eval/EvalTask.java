/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
