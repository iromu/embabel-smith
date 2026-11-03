package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * The request for the curation re-eval: which skill to re-score, and (optionally) the eval tasks to
 * run against it. When no tasks are supplied, {@link #tasksOrDefault()} falls back to a small
 * default set (the same defaults the eval/refinement pane uses).
 */
public record CurateRequest(String skillName, List<EvalTask> tasks) {

    public List<EvalTask> tasksOrDefault() {
        return (tasks == null || tasks.isEmpty()) ? EvalTask.DEFAULTS : tasks;
    }
}
