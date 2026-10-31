package com.embabel.smith.eval;

import java.util.function.Predicate;

/**
 * One evaluation task for a goal-type (non-chat) skill: a user input plus the acceptance check on
 * the skill's goal output. This is the goal-type analogue of the conversational eval's {@code Task}
 * (design D6 — the eval is adapted, not assumed): instead of scoring a chat transcript, we score the
 * single goal output the skill produces for the input.
 */
public record SkillEvalTask(String input, String acceptanceCriteria, Predicate<String> acceptance) {
}
