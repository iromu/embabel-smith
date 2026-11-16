/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest;

import java.util.Arrays;
import java.util.List;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.ModelMetadata;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ModelType;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.rest.dto.ModelCatalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The model picker's data (change 06 polish): the LLMs a model-launching pane may ask
 * for, and the one it gets when it asks for none.
 *
 * <p>
 * An OpenAI-compatible endpoint registers exactly the models the operator listed in
 * {@code OPENAI_CUSTOM_MODELS}, so that list is what the picker offers — the bundled
 * catalog those model names would otherwise be confused with is not reachable through a
 * gateway that does not serve it. The catalog providers (OpenAI, Anthropic) register
 * Embabel's catalog instead, so the picker offers what the platform actually resolved.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class ModelsController {

	private final ModelProvider modelProvider;

	private final ModelEndpointConfig endpoint;

	public ModelsController(ModelProvider modelProvider, ModelEndpointConfig endpoint) {
		this.modelProvider = modelProvider;
		this.endpoint = endpoint;
	}

	@GetMapping("/api/models")
	public ModelCatalog models() {
		return new ModelCatalog(offeredModels(), resolvedDefault());
	}

	private List<String> offeredModels() {
		if (this.endpoint.provider() == ModelEndpointConfig.Provider.OPENAI_CUSTOM
				&& notBlank(this.endpoint.models())) {
			return Arrays.stream(this.endpoint.models().split(","))
				.map(String::trim)
				.filter((s) -> !s.isBlank())
				.distinct()
				.sorted()
				.toList();
		}
		return this.modelProvider.listModels()
			.stream()
			.filter((model) -> model.getType() == ModelType.LLM)
			.map(ModelMetadata::getName)
			.distinct()
			.sorted()
			.toList();
	}

	/**
	 * The model a request naming none will run. The configured value may name a role
	 * rather than a model, which only the platform can answer for.
	 * @return the model to offer as the default, or {@code null} when the deployment
	 * names none.
	 */
	private String resolvedDefault() {
		String configured = this.endpoint.defaultLlm();
		if (!notBlank(configured)) {
			return null;
		}
		LlmOptions role = this.modelProvider.configuredOptionsForRole(configured);
		String model = (null != role) ? role.getModel() : null;
		return notBlank(model) ? model : configured;
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}

}
