package com.embabel.smith.lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of one proactive re-eval: the lifecycle transition and the two drift signals, plus a
 * human-readable verdict.
 */
public record DriftReport(
        String skill,
        LifecycleState before,
        LifecycleState after,
        boolean contentDrift,
        double baselineScore,
        double currentScore,
        String recordedHash,
        String currentHash) {

    public boolean drifted() {
        return contentDrift || after == LifecycleState.DEGRADED || after == LifecycleState.RETIRED;
    }

    public String verdict() {
        if (after == LifecycleState.RETIRED) {
            return String.format("RETIRED: score collapsed %.2f -> %.2f; the skill's contract is violated",
                    baselineScore, currentScore);
        }
        if (after == LifecycleState.DEGRADED) {
            List<String> reasons = new ArrayList<>();
            if (contentDrift) {
                reasons.add("content changed");
            }
            if (currentScore < baselineScore) {
                reasons.add(String.format("score dropped %.2f -> %.2f", baselineScore, currentScore));
            }
            return "DEGRADED: " + String.join(", ", reasons) + "; re-evaluate before trusting it";
        }
        if (after == LifecycleState.ACTIVE && before == LifecycleState.INCUBATING) {
            return String.format("ACTIVE: baseline eval passed (%.2f); promoted from incubation", baselineScore);
        }
        return "STABLE: no drift; the skill matches its recorded baseline";
    }
}
