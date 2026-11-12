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
import com.embabel.smith.studio.rest.dto.StatusResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global endpoint status for the studio header (change 06 polish): reports whether a
 * model endpoint is resolvable and which default model it would use, so the UI can show a
 * live readiness indicator instead of each pane discovering the missing endpoint only
 * after a failed run.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class StatusController {

	private final ModelEndpointConfig endpoint;

	public StatusController(ModelEndpointConfig endpoint) {
		this.endpoint = endpoint;
	}

	@GetMapping("/api/status")
	public StatusResponse status() {
		return new StatusResponse(this.endpoint.isConfigured(),
				(this.endpoint.isConfigured()) ? this.endpoint.defaultLlm() : null, this.endpoint.configError());
	}

}
