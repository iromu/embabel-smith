package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * The request for the eval/refinement pane: which skill to evaluate, and (optionally) the eval tasks
 * to run against it. When no tasks are supplied, {@link #tasksOrDefault()} falls back to a small
 * default set.
 */
public record EvalRequest(String skillName, List<EvalTask> tasks) {

    public List<EvalTask> tasksOrDefault() {
        return (tasks == null || tasks.isEmpty()) ? EvalTask.DEFAULTS : tasks;
    }
}
