package com.embabel.smith.unified.dto;

import com.embabel.smith.lifecycle.DriftReport;

/**
 * The result of a lifecycle re-eval (POC 18): the skill's lifecycle transition (before → after), the
 * two drift signals (content hash + eval pass-rate), the human-readable POC 18 verdict, and whether
 * the skill is still surfaced by retrieval. {@link #error} is non-null when the re-eval could not run
 * (no skill selected, an unknown skill, or no endpoint configured) — in which case the transition
 * fields are empty.
 */
public record CurateResponse(
        String error,
        String skillName,
        String before,
        String after,
        boolean contentDrift,
        double baselineScore,
        double currentScore,
        String verdict,
        boolean surfaced) {

    public static CurateResponse error(String error) {
        return new CurateResponse(error, null, null, null, false, 0.0, 0.0, null, false);
    }

    public static CurateResponse from(String skillName, DriftReport report, boolean surfaced) {
        return new CurateResponse(
                null,
                skillName,
                report.before().name(),
                report.after().name(),
                report.contentDrift(),
                round4(report.baselineScore()),
                round4(report.currentScore()),
                report.verdict(),
                surfaced);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
