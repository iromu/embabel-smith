/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.builder.lifecycle;

/**
 * The lifecycle sidecar recorded for a published agent spec (design D4, the POC 18
 * contract): the content hash at baseline, the baseline eval pass-rate, the initial
 * lifecycle state, and when the baseline was recorded. It is the reference the
 * runtime/library later compares against to detect content and score drift.
 * @param contentHash the deterministic hash of the spec's load-bearing surface at baseline.
 * @param baselinePassRate the eval pass-rate recorded at baseline.
 * @param state the lifecycle state, initially {@link LifecycleState#INCUBATING}.
 * @param updatedAt the instant the baseline was recorded, as an ISO-8601 string.
 *
 * @author Ivan Rodriguez
 */
public record LifecycleMetadata(String contentHash, double baselinePassRate, LifecycleState state, String updatedAt) {
}
