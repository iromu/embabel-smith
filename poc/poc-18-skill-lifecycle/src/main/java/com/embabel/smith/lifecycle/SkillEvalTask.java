package com.embabel.smith.lifecycle;

import java.util.function.Predicate;

/**
 * One eval task for the skill: the input, a human-readable acceptance criterion, and a
 * deterministic acceptance check on the goal output (same shape as POC 9's task).
 */
public record SkillEvalTask(String input, String acceptanceCriteria, Predicate<String> acceptance) {
}
