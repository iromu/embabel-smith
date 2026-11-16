/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.studio.rest.dto.SimulateRequest;
import com.embabel.smith.studio.rest.dto.SimulateResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's simulate step: dry-run the generated (or edited) pack in a throwaway
 * platform with a sample input, and return the per-step trace. Nothing is published.
 * Endpoint-gated — a simulate runs the pack's action bodies, which call the model. The
 * run is behind the {@link SimulateFn} seam so the endpoint-present path is
 * stub-testable.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class SimulateController {

	private final SimulateFn simulator;

	private final ModelEndpointConfig endpoint;

	public SimulateController(SimulateFn simulator, ModelEndpointConfig endpoint) {
		this.simulator = simulator;
		this.endpoint = endpoint;
	}

	@PostMapping("/api/simulate")
	public SimulateResponse simulate(@RequestBody SimulateRequest request) {
		AgentSpec pack = request.pack();
		if (pack == null || pack.goal() == null || pack.goal().name() == null) {
			return SimulateResponse.failed("No pack or goal to simulate; generate a flow first.");
		}
		String configError = this.endpoint.configError();
		if (configError != null) {
			return SimulateResponse.noEndpoint(configError);
		}
		try {
			return this.simulator.simulate(pack, (request.input() == null) ? "" : request.input(), request.model());
		}
		catch (Exception ex) {
			String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
			return SimulateResponse.failed("Simulate failed: " + message);
		}
	}

	/**
	 * The model-dependent dry-run: a pack, a sample input and a model to a run outcome
	 * with its steps.
	 */
	@FunctionalInterface
	public interface SimulateFn {

		SimulateResponse simulate(AgentSpec pack, String input, String model);

	}

}
