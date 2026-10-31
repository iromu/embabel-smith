package com.embabel.smith.eval;

import com.embabel.agent.eval.support.FullScores;
import com.embabel.agent.eval.support.Score;

import java.util.List;

/**
 * Runs a goal-type skill against a set of eval tasks and produces a composite score (task 6.1).
 *
 * <p>Decoupled from the invocation mechanism via {@link SkillInvoker}: the endpoint-gated
 * production path is {@link AgentInvocationSkillInvoker}, while offline tests inject a fake
 * invoker so the full evaluate → score → composite flow is verifiable without a live model.
 */
public class GoalSkillEvaluator {

    private final SkillEvalScorer scorer = new SkillEvalScorer();

    public FullScores evaluate(SkillInvoker invoker, List<SkillEvalTask> tasks) {
        List<Score> scores = tasks.stream()
                .map(task -> scorer.scoreOne(invoker.invoke(task.input()), task))
                .toList();
        return scorer.composite(scores);
    }
}
