package com.embabel.smith.builder.lifecycle;

/**
 * The lifecycle sidecar recorded for a published agent spec (design D4, the POC 18 contract): the
 * content hash at baseline, the baseline eval pass-rate, the initial lifecycle state, and when the
 * baseline was recorded. It is the reference the runtime/library later compares against to detect
 * content and score drift.
 */
public record LifecycleMetadata(String contentHash, double baselinePassRate,
                                LifecycleState state, String updatedAt) {
}
