package com.embabel.smith.runtime.core;

import com.embabel.agent.core.AgentProcess;

/**
 * The outcome of a run: the process that was executed (or {@code null} when the run never
 * started, e.g. blocked by the trust tier), whether the goal was achieved, its output, and the
 * failure reason. {@code plannerName} is the planner the run resolved to ({@code null} when the
 * run never started).
 */
public record RunOutcome(AgentProcess process, boolean success, String output, String error, String plannerName) {

    /** A run that failed before a process was created (tier block, goal not found). */
    public static RunOutcome failed(String error) {
        return new RunOutcome(null, false, null, error, null);
    }
}
