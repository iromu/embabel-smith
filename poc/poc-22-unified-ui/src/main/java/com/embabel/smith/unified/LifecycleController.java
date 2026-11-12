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

package com.embabel.smith.unified;

import java.util.List;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.lifecycle.DriftReport;
import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.lifecycle.LiveEvalRunner;
import com.embabel.smith.lifecycle.SpecEvalTask;
import com.embabel.smith.unified.dto.CurateRequest;
import com.embabel.smith.unified.dto.CurateResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Lifecycle pane (POC 22): re-score a spec with POC 18's {@link LiveEvalRunner} and
 * move it through the lifecycle, so the library is curated, not just scored. The live
 * re-eval is the endpoint-gated half behind the {@link LiveEvalFn} seam; the pure
 * content-hash / drift / state-machine half (POC 18's
 * {@link com.embabel.smith.lifecycle.LifecycleManager} via {@link SpecCuration}) is what
 * runs offline. Without a configured endpoint it returns a clear configuration error
 * before any model call, and the endpoint-present path is testable with a stub.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class LifecycleController {

	private final SpecCatalog catalog;

	private final SpecCuration curation;

	private final LiveEvalFn evaluator;

	private final ModelEndpointConfig endpoint;

	public LifecycleController(SpecCatalog catalog, SpecCuration curation, LiveEvalFn evaluator,
			ModelEndpointConfig endpoint) {
		this.catalog = catalog;
		this.curation = curation;
		this.evaluator = evaluator;
		this.endpoint = endpoint;
	}

	/** Re-evaluate a spec's pass-rate and record its (possibly new) lifecycle state.
	 * @param request the curation request (spec name + tasks).
	 * @return the curation response (before/after state, drift report, or a named error).
	 */
	@PostMapping("/api/curate")
	public CurateResponse curate(@RequestBody CurateRequest request) {
		String specName = (request.specName() == null) ? "" : request.specName();
		if (specName.isBlank()) {
			return CurateResponse.error("No spec selected; pick a spec to re-evaluate first.");
		}
		SpecNode node = this.catalog.get(specName);
		if (node == null) {
			return CurateResponse.error("Unknown spec: " + specName + ". Choose one from the palette.");
		}
		String configError = this.endpoint.configError();
		if (configError != null) {
			return CurateResponse.error(configError);
		}
		try {
			List<SpecEvalTask> tasks = request.tasksOrDefault()
				.stream()
				.map((t) -> new SpecEvalTask(t.input(), t.acceptanceCriteria(),
						(out) -> out != null && !out.isBlank()
								&& out.toLowerCase().contains(t.mustContain().toLowerCase())))
				.toList();
			LifecycleState before = this.curation.stateBefore(specName);
			double measured = this.evaluator.reEval(specName, tasks);
			LifecycleState after = this.curation.curate(specName, measured);
			SpecCuration.Baseline b = this.curation.baseline(specName);
			DriftReport report = new DriftReport(specName, before, after, this.curation.contentDrift(specName),
					b.score(), measured, b.contentHash(), this.curation.currentHash(specName));
			return CurateResponse.from(specName, report, this.curation.surfaced(after));
		}
		catch (Exception ex) {
			return CurateResponse.error("Re-eval failed: " + ((ex.getMessage() == null) ? ex.toString() : ex.getMessage()));
		}
	}

	/**
	 * The model-dependent re-eval: spec name + tasks to the measured live eval pass-rate.
	 */
	@FunctionalInterface
	public interface LiveEvalFn {

		double reEval(String specName, List<SpecEvalTask> tasks) throws Exception;

	}

}
