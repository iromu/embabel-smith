package com.embabel.smith.builder.eval;

import com.embabel.smith.common.model.SkillPack;

import java.util.List;

/**
 * The result of the bounded refinement loop: the (possibly refined) pack, its final composite
 * eval score, how many prompt-rewrite iterations were applied, the tasks still failing, and
 * whether the final score met the gate.
 */
public record RefinementOutcome(SkillPack pack, double score, int refinementCount,
                                List<String> failingTasks, boolean passed) {
}
