package com.embabel.smith.builder.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores a set of {@link EvalTask}s against the outputs the built skill produced (extracted from
 * POC 5's eval harness). A task passes when its output contains the expected substring; the
 * composite score is the fraction of tasks that passed.
 */
public final class EvalScorer {

    private EvalScorer() {
    }

    /** The fraction of tasks whose output contains the expected substring, in {@code [0, 1]}. */
    public static double score(List<EvalTask> tasks, List<String> outputs) {
        if (tasks.isEmpty()) {
            return 1.0;
        }
        long passed = 0;
        for (int i = 0; i < tasks.size(); i++) {
            if (passes(tasks.get(i), outputAt(outputs, i))) {
                passed++;
            }
        }
        return (double) passed / tasks.size();
    }

    /** The names of the tasks that did not pass, in task order. */
    public static List<String> failingTasks(List<EvalTask> tasks, List<String> outputs) {
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            if (!passes(tasks.get(i), outputAt(outputs, i))) {
                failures.add(tasks.get(i).name());
            }
        }
        return failures;
    }

    private static boolean passes(EvalTask task, String output) {
        if (output == null) {
            return false;
        }
        String expected = task.expected();
        if (expected == null || expected.isBlank()) {
            // A blank criterion means "the run produced output" (the smoke default): a blank or
            // missing output does not pass. Without this, contains("") would let any non-null
            // output — including empty — through and every build would score 1.0.
            return !output.isBlank();
        }
        return output.toLowerCase().contains(expected.toLowerCase());
    }

    private static String outputAt(List<String> outputs, int i) {
        return i < outputs.size() ? outputs.get(i) : null;
    }
}
