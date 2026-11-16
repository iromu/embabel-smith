/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
 * {@code /api/compose}. The request carries a generation {@code technique} (the
 * single-shot prompt, or Embabel's NL compiler); the workspace lets the user pick either.
 * Endpoint-gated: without a model endpoint it returns the structured
 * {@code MODEL_NOT_CONFIGURED} error. The model-dependent generation is behind the
 * {@link GenerateFn} seam so the endpoint-present path is stub-testable.
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
		return this.generator.generate(blueprint, request.technique(), request.model());
	}

	/**
	 * The model-dependent generation: a blueprint, a generation technique and a model to
	 * a generated pack (plus flow issues).
	 */
	@FunctionalInterface
	public interface GenerateFn {

		GenerateResponse generate(String blueprint, String technique, String model);

	}

}
