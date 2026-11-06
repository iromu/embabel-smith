package com.embabel.smith.builder;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.common.model.SkillPack;

import java.util.List;

/**
 * The outcome of a build: whether the skill-pack was published or the build was blocked, at which
 * stage it stopped, and the evidence (eval score, security scan, lifecycle metadata, publish
 * location). A blocked build names the stage and the reasons it stopped; when the build could not
 * be resolved cleanly (an ambiguous/invalid description) it carries the draft for review.
 */
public record BuildResult(
        Status status,
        String stage,
        String packName,
        Double score,
        List<String> issues,
        ScanReport scan,
        LifecycleMetadata lifecycle,
        PublishResult published,
        SkillPack draft) {

    public enum Status {
        BUILT,
        BLOCKED
    }

    public boolean isBuilt() {
        return status == Status.BUILT;
    }

    public static BuildResult built(String packName, double score, ScanReport scan,
                                   LifecycleMetadata lifecycle, PublishResult published) {
        return new BuildResult(Status.BUILT, "done", packName, score, List.of(), scan, lifecycle, published, null);
    }

    public static BuildResult blocked(String stage, String packName, List<String> issues,
                                      Double score, ScanReport scan, SkillPack draft) {
        return new BuildResult(Status.BLOCKED, stage, packName, score,
                issues == null ? List.of() : List.copyOf(issues), scan, null, null, draft);
    }

    /** A human-readable build report for stdout. */
    public String report() {
        StringBuilder sb = new StringBuilder();
        if (isBuilt()) {
            sb.append("BUILD OK: ").append(packName).append('\n');
            sb.append("  eval score:  ").append(String.format("%.3f", score)).append('\n');
            if (scan != null) {
                sb.append("  security:    ").append(scan.summary()).append('\n');
            }
            if (lifecycle != null) {
                sb.append("  lifecycle:   ").append(lifecycle.state())
                        .append(" (hash ").append(shortHash(lifecycle.contentHash()))
                        .append(", baseline ").append(String.format("%.3f", lifecycle.baselinePassRate()))
                        .append(")\n");
            }
            if (published != null) {
                sb.append("  published:   ").append(published.location());
                if (published.pushed()) {
                    sb.append(" (pushed)");
                }
                sb.append('\n');
            }
        } else {
            sb.append("BUILD BLOCKED at ").append(stage).append(" (").append(packName).append(")\n");
            if (score != null) {
                sb.append("  eval score:  ").append(String.format("%.3f", score)).append('\n');
            }
            for (String issue : issues) {
                sb.append("  - ").append(issue).append('\n');
            }
            if (draft != null) {
                sb.append("  draft:       ").append(draft.name()).append(" (retained for review)").append('\n');
            }
        }
        return sb.toString();
    }

    private static String shortHash(String hash) {
        return hash == null || hash.length() <= 12 ? String.valueOf(hash) : hash.substring(0, 12);
    }
}
