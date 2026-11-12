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

import java.util.Map;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.discover.BusinessProcessDiscovery;
import com.embabel.smith.studio.rest.dto.ExtractRequest;
import com.embabel.smith.studio.rest.dto.ExtractResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's model-based process extraction: load the business-process documents
 * from a docs path or a repository URL and run the discovery prompt over them, returning
 * a business-process description that the UI hands to generation as its blueprint.
 * Endpoint-gated — the extraction runs the model. The model-dependent call is behind the
 * {@link ExtractFn} seam so the endpoint-present path is stub-testable.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class ExtractController {

	private final ExtractFn extractor;

	private final ModelEndpointConfig endpoint;

	private final BusinessProcessDiscovery discovery;

	public ExtractController(ExtractFn extractor, ModelEndpointConfig endpoint, BusinessProcessDiscovery discovery) {
		this.extractor = extractor;
		this.endpoint = endpoint;
		this.discovery = discovery;
	}

	@PostMapping("/api/extract")
	public ExtractResponse extract(@RequestBody ExtractRequest request) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			return ExtractResponse.noEndpoint(configError);
		}
		String corpus;
		try {
			BusinessProcessDiscovery.DiscoverResult result = this.discovery.load(request.path(), request.url());
			corpus = toCorpus(result.docs());
		}
		catch (RuntimeException ex) {
			return ExtractResponse.failed((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
		}
		if (corpus == null || corpus.isBlank()) {
			return ExtractResponse.failed("No documents to extract a process from.");
		}
		try {
			return ExtractResponse.of(this.extractor.extract(corpus));
		}
		catch (Exception ex) {
			String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
			return ExtractResponse.failed("Extraction failed: " + message);
		}
	}

	/**
	 * Concatenates the loaded docs into one corpus, mirroring the generation blueprint
	 * shape.
	 * @param docs the loaded docs keyed by name.
	 * @return the concatenated, stripped corpus.
	 */
	private static String toCorpus(Map<String, String> docs) {
		StringBuilder sb = new StringBuilder();
		docs.forEach((name, body) -> sb.append("--- ").append(name).append(" ---\n").append(body).append("\n\n"));
		return sb.toString().strip();
	}

	/**
	 * The model-dependent extraction: a docs corpus to a business-process description.
	 */
	@FunctionalInterface
	public interface ExtractFn {

		String extract(String corpus);

	}

}
