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
import com.embabel.smith.studio.generate.BlueprintLoader;
import com.embabel.smith.studio.rest.dto.GenerateRequest;
import com.embabel.smith.studio.rest.dto.GenerateResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Author-a-Process workspace's generation step: a blueprint (a description, or a path
 * to business-process docs) in, a generated agent spec (GOAP goal + action steps + types)
 * out — not published, so the UI can render and edit it before publishing via
 * {@code /api/compose}. Endpoint-gated: without a model endpoint it returns the
 * structured {@code MODEL_NOT_CONFIGURED} error. The model-dependent generation is behind
 * the {@link GenerateFn} seam so the endpoint-present path is stub-testable.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class GenerateController {

	private final GenerateFn generator;

	private final ModelEndpointConfig endpoint;

	private final BlueprintLoader loader;

	public GenerateController(GenerateFn generator, ModelEndpointConfig endpoint, BlueprintLoader loader) {
		this.generator = generator;
		this.endpoint = endpoint;
		this.loader = loader;
	}

	@PostMapping("/api/generate")
	public GenerateResponse generate(@RequestBody GenerateRequest request) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			return GenerateResponse.noEndpoint(configError);
		}
		String blueprint;
		try {
			blueprint = this.loader.resolve(request.blueprint(), request.path());
		}
		catch (RuntimeException ex) {
			return GenerateResponse.failed((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
		}
		if (blueprint == null || blueprint.isBlank()) {
			return GenerateResponse.failed("No blueprint: provide a description or a path to business-process docs.");
		}
		return this.generator.generate(blueprint);
	}

	/**
	 * The model-dependent generation: a blueprint to a generated pack (plus flow issues).
	 */
	@FunctionalInterface
	public interface GenerateFn {

		GenerateResponse generate(String blueprint);

	}

}
