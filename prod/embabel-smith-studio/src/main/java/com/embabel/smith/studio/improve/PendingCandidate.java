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

package com.embabel.smith.studio.improve;

import java.util.List;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.TraceRecord;

/**
 * A gated candidate held in the approval queue, awaiting an explicit decision (change
 * 06's UI). Carries the evidence that produced it: the triggering failure traces, the
 * re-eval score, the security scan, the proposed lifecycle (new hash, new baseline, new
 * state), and a summary of what the candidate changes relative to the current pack.
 *
 * @param id the candidate's unique id
 * @param spec the spec the candidate refines
 * @param candidate the refined candidate pack
 * @param proposedLifecycle the proposed lifecycle sidecar (new hash, baseline, state)
 * @param score the candidate's re-eval pass-rate
 * @param baseline the pack's baseline pass-rate
 * @param scan the security scan report
 * @param evidence the triggering failure traces
 * @param diffSummary a summary of what the candidate changes
 * @author Ivan Rodriguez
 */
public record PendingCandidate(String id, String spec, AgentSpec candidate, LifecycleMetadata proposedLifecycle,
		double score, double baseline, ScanReport scan, List<TraceRecord> evidence, String diffSummary) {
}
