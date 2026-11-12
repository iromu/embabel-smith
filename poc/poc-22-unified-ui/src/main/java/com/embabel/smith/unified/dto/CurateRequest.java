package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The request for the lifecycle re-eval: which spec to re-score, and (optionally) the eval tasks to
 * run against it. When no tasks are supplied, {@link #tasksOrDefault()} falls back to a small
 * default set.
 */
public record CurateRequest(String specName, List<EvalTask> tasks) {

    public List<EvalTask> tasksOrDefault() {
        return (tasks == null || tasks.isEmpty()) ? EvalTask.DEFAULTS : tasks;
    }
}
