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

package com.embabel.smith.studio.rest;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.studio.rest.dto.RunRequest;
import com.embabel.smith.studio.rest.dto.RunResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Run pane (change 06): pick a source, an exported goal, and an input, and run the
 * goal through the change-03 runtime core. Endpoint-gated — without a configured model
 * endpoint it returns the structured {@code MODEL_NOT_CONFIGURED} error before any run.
 * The run is behind the {@link RunFn} seam so the endpoint-present path is testable with
 * a stub.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class RunController {

	private final RunFn runner;

	private final ModelEndpointConfig endpoint;

	public RunController(RunFn runner, ModelEndpointConfig endpoint) {
		this.runner = runner;
		this.endpoint = endpoint;
	}

	@PostMapping("/api/run")
	public RunResponse run(@RequestBody RunRequest request) {
		if (request.source() == null || request.source().isBlank()) {
			return RunResponse.error("No source selected; pick a source to run from.");
		}
		String configError = this.endpoint.configError();
		if (configError != null) {
			return RunResponse.noEndpoint(configError);
		}
		TrustTier tier = parseTier(request.trustTier());
		try {
			AgentSpecRunService.RunResult result = this.runner.run(request.source(), request.goal(), request.input(),
					request.planner(), tier);
			String runId = (result.trace() == null) ? null : result.trace().runId();
			return (result.success()) ? RunResponse.ok(result.output(), runId)
					: RunResponse.failed(result.error(), runId);
		}
		catch (Exception ex) {
			return RunResponse.error("Run failed: " + message(ex));
		}
	}

	private static TrustTier parseTier(String tier) {
		if (tier == null || tier.isBlank()) {
			return TrustTier.STANDARD;
		}
		try {
			return TrustTier.valueOf(tier.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			return TrustTier.STANDARD;
		}
	}

	private static String message(Exception ex) {
		return (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
	}

	/**
	 * The model-dependent run: source + goal + input + planner + tier to a run result.
	 */
	@FunctionalInterface
	public interface RunFn {

		AgentSpecRunService.RunResult run(String source, String goal, String input, String planner, TrustTier tier);

	}

}
