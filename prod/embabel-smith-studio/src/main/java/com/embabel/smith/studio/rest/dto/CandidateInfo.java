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

package com.embabel.smith.studio.rest.dto;

import java.util.List;

import com.embabel.smith.studio.improve.PendingCandidate;

/**
 * A pending candidate awaiting approval (the Auto-improve pane), with its evidence: the
 * re-eval score vs the baseline, the security scan, the triggering failure traces, a diff
 * summary, and the lifecycle state it would transition to.
 *
 * @param id the candidate id.
 * @param spec the spec the candidate belongs to.
 * @param score the re-eval score.
 * @param baseline the baseline score.
 * @param diffSummary the diff summary of the candidate change.
 * @param scan the security scan of the candidate.
 * @param evidence the triggering failure traces.
 * @param proposedState the lifecycle state the candidate would transition to.
 * @author Ivan Rodriguez
 */
public record CandidateInfo(String id, String spec, double score, double baseline, String diffSummary, ScanInfo scan,
		List<TraceInfo> evidence, String proposedState) {

	public static CandidateInfo of(PendingCandidate candidate) {
		List<TraceInfo> evidence = candidate.evidence().stream().map(TraceInfo::of).toList();
		return new CandidateInfo(candidate.id(), candidate.spec(), candidate.score(), candidate.baseline(),
				candidate.diffSummary(), (candidate.scan() != null) ? ScanInfo.of(candidate.scan()) : null, evidence,
				candidate.proposedLifecycle().state().name());
	}
}
