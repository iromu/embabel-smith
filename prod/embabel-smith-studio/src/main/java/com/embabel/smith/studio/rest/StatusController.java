/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
