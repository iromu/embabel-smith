package com.embabel.smith.builder.eval;

/**
 * One acceptance case for the built skill: an {@code input} to run the skill on and the
 * {@code expected} substring that a passing run's output must contain. The task set is a
 * parameter of the build (design D1) — the builder does not hardcode it.
 */
public record EvalTask(String name, String input, String expected) {
}
