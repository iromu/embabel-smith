/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.lifecycle;

/**
 * The lifecycle sidecar recorded for a published agent spec (design D4, the POC 18
 * contract): the content hash at baseline, the baseline eval pass-rate, the initial
 * lifecycle state, and when the baseline was recorded. It is the reference the
 * runtime/library later compares against to detect content and score drift.
 *
 * @param contentHash the deterministic hash of the spec's load-bearing surface at
 * baseline.
 * @param baselinePassRate the eval pass-rate recorded at baseline.
 * @param state the lifecycle state, initially {@link LifecycleState#INCUBATING}.
 * @param updatedAt the instant the baseline was recorded, as an ISO-8601 string.
 * @author Ivan Rodriguez
 */
public record LifecycleMetadata(String contentHash, double baselinePassRate, LifecycleState state, String updatedAt) {
}
