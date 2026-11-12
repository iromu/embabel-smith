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
import com.embabel.smith.unified.dto.AuthorDraft;
import com.embabel.smith.unified.dto.CompileRequest;
import com.embabel.smith.unified.dto.CompileResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Author pane (POC 22): given a natural-language description, compile a draft spec
 * via POC 2's {@link com.embabel.smith.nlcompile.NlCompileRunner} and report its goal
 * shape + {@code DraftQuality} verdict — the "describe a spec in plain language" half of
 * the builder. This is endpoint-gated: it needs a live OpenAI-compatible endpoint, and
 * returns a clear configuration error (not an opaque model error) when none is set. The
 * model call is behind the {@link CompileFn} seam so the endpoint-present path is
 * testable with a stub.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class AuthorController {

	private final CompileFn compiler;

	private final ModelEndpointConfig endpoint;

	public AuthorController(CompileFn compiler, ModelEndpointConfig endpoint) {
		this.compiler = compiler;
		this.endpoint = endpoint;
	}

	/**
	 * Compile a description into a draft; a clear error when no endpoint is configured.
	 * @param request the compile request (agent name + description).
	 * @return the compile response (draft, or a named error).
	 */
	@PostMapping("/api/compile")
	public CompileResponse compile(@RequestBody CompileRequest request) {
		String description = (request.description() == null) ? "" : request.description();
		if (description.isBlank()) {
			return CompileResponse.badRequest("No description supplied; describe the spec first.");
		}
		String configError = this.endpoint.configError();
		if (configError != null) {
			return CompileResponse.noEndpoint(configError);
		}
		try {
			AuthorDraft draft = this.compiler.compile(request.agentNameOrDefault(), description);
			return CompileResponse.ok(draft);
		}
		catch (Exception ex) {
			return CompileResponse.error("Compile failed: " + ((ex.getMessage() == null) ? ex.toString() : ex.getMessage()));
		}
	}

	/**
	 * The model-dependent compile: agent name + description to the mapped
	 * {@link AuthorDraft}.
	 */
	@FunctionalInterface
	public interface CompileFn {

		AuthorDraft compile(String agentName, String description) throws Exception;

	}

}
