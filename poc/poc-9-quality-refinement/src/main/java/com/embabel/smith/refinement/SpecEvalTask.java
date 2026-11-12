package com.embabel.smith.refinement;

import java.util.function.Predicate;

/**
 * One eval task for a goal-type spec: the input, a human-readable acceptance criterion (fed to the
 * rewriter when the task fails), and a deterministic acceptance check on the goal output.
 */
public record SpecEvalTask(String input, String acceptanceCriteria, Predicate<String> acceptance) {
}
