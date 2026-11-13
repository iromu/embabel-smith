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

import java.util.List;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.rest.dto.BuildRequest;
import com.embabel.smith.studio.rest.dto.BuildResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Author pane (change 06): submit a description and an output source, and build the
 * agent spec through the change-04 builder core (compile → eval → security → lifecycle →
 * publish). Endpoint- gated — without a configured model endpoint it returns the
 * structured {@code MODEL_NOT_CONFIGURED} error before any build. The build is behind the
 * {@link BuildFn} seam so the endpoint-present path is testable with a stub.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class BuildController {

	private final BuildFn builder;

	private final ModelEndpointConfig endpoint;

	public BuildController(BuildFn builder, ModelEndpointConfig endpoint) {
		this.builder = builder;
		this.endpoint = endpoint;
	}

	@PostMapping("/api/build")
	public BuildResponse build(@RequestBody BuildRequest request) {
		if (request.description() == null || request.description().isBlank()) {
			return BuildResponse.blocked("input", null,
					List.of("No description provided; describe the spec to build."),
					"No description provided; describe the spec to build.");
		}
		if (request.outputSource() == null || request.outputSource().isBlank()) {
			return BuildResponse.blocked("input", null,
					List.of("No output source provided; pick where to publish the pack."),
					"No output source provided; pick where to publish the pack.");
		}
		String configError = this.endpoint.configError();
		if (configError != null) {
			return BuildResponse.noEndpoint(configError);
		}
		try {
			PublishTarget target = PublishTarget.parse(request.outputSource());
			BuildOptions options = new BuildOptions(
					(request.maxRefine() == null) ? BuildOptions.DEFAULT_MAX_REFINE : request.maxRefine(),
					BuildOptions.DEFAULT_SCORE_GATE, request.overwrite() != null && request.overwrite(),
					request.push() != null && request.push(), GenerationTechnique.SINGLE_SHOT);
			return BuildResponse.of(this.builder.build(request.description(), target, options));
		}
		catch (Exception ex) {
			String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
			return BuildResponse.blocked("build", null, List.of(message), message);
		}
	}

	/**
	 * The model-dependent build: description + output source + options to a build result.
	 */
	@FunctionalInterface
	public interface BuildFn {

		BuildResult build(String description, PublishTarget target, BuildOptions options);

	}

}
