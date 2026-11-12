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

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.unified.dto.RunRequest;
import com.embabel.smith.unified.dto.RunResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Run pane (POC 22): pick a spec from the palette and run the fixed no-code runtime
 * (POC 1's {@link com.embabel.smith.runtime.SpecRunner}) over its exported goal — the
 * action bodies invoke the model, so this is endpoint-gated. Without a configured
 * endpoint it returns a clear, named configuration error before any model call. The model
 * call is behind the {@link RunFn} seam so the endpoint-present path is testable with a
 * stub.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class RunController {

	private final SpecCatalog catalog;

	private final RunFn runner;

	private final ModelEndpointConfig endpoint;

	public RunController(SpecCatalog catalog, RunFn runner, ModelEndpointConfig endpoint) {
		this.catalog = catalog;
		this.runner = runner;
		this.endpoint = endpoint;
	}

	/** Run a spec over an input; a clear error when no endpoint is configured.
	 * @param request the run request (spec name + input).
	 * @return the run response (goal output, or a named error).
	 */
	@PostMapping("/api/run")
	public RunResponse run(@RequestBody RunRequest request) {
		String specName = (request.specName() == null) ? "" : request.specName();
		if (specName.isBlank()) {
			return RunResponse.error("No spec selected; pick a spec to run first.");
		}
		if (this.catalog.get(specName) == null) {
			return RunResponse.error("Unknown spec: " + specName + ". Choose one from the palette.");
		}
		String configError = this.endpoint.configError();
		if (configError != null) {
			return RunResponse.noEndpoint(configError);
		}
		try {
			String output = this.runner.run(specName, request.inputOrDefault());
			return RunResponse.ok(output);
		}
		catch (Exception ex) {
			return RunResponse.error("Run failed: " + ((ex.getMessage() == null) ? ex.toString() : ex.getMessage()));
		}
	}

	/** The model-dependent run: spec name + input to the goal output. */
	@FunctionalInterface
	public interface RunFn {

		String run(String specName, String input) throws Exception;

	}

}
