/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest;

import java.util.List;
import java.util.stream.Collectors;

import com.embabel.smith.runtime.remote.AdvertisedAction;
import com.embabel.smith.runtime.remote.BridgeState;
import com.embabel.smith.runtime.remote.ExecuteResult;
import com.embabel.smith.runtime.remote.RemoteActionBridge;
import com.embabel.smith.runtime.remote.RemoteBridge;
import com.embabel.smith.runtime.remote.RemoteBridges;
import com.embabel.smith.studio.rest.dto.ActionInfo;
import com.embabel.smith.studio.rest.dto.BridgeInfo;
import com.embabel.smith.studio.rest.dto.BridgesResponse;
import com.embabel.smith.studio.rest.dto.RemoteExecuteRequest;
import com.embabel.smith.studio.rest.dto.RemoteExecuteResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Remote pane: which remote-action sidecars this installation configured, whether
 * each one answers, what each advertises, and the output of running one of those actions.
 * Everything about the wire — the three-endpoint contract, the timeouts, how a reply
 * decides reachability — lives in the runtime's {@link RemoteActionBridge}; this class
 * reads configuration, selects a bridge by the name configuration gave it, and shapes the
 * DTOs. It holds no HTTP of its own.
 *
 * <p>
 * Two things this controller deliberately never does. It never starts a process: a
 * sidecar is started by hand, and an absent one is reported, not launched. And it never
 * accepts an address from a request — a request names a bridge, and a name that
 * configuration does not define is refused before anything is contacted.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class RemoteController {

	private final List<RemoteBridge> bridges;

	private final List<String> malformedBridges;

	private final String mcpEndpoint;

	public RemoteController(@Value("${smith.studio.remote-bridges:}") String configuredBridges,
			@Value("${smith.studio.mcp-endpoint:}") String mcpEndpoint) {
		RemoteBridges.Parsed parsed = RemoteBridges.parse(configuredBridges);
		this.bridges = parsed.bridges();
		this.malformedBridges = parsed.malformed();
		this.mcpEndpoint = (mcpEndpoint == null || mcpEndpoint.isBlank()) ? null : mcpEndpoint.trim();
	}

	/**
	 * Probe every configured bridge and report what each one answered. A bridge that is
	 * not running comes back as an unreachable entry in a successful response, not as a
	 * failure of this endpoint.
	 * @return every configured bridge, the entries whose configuration could not be read,
	 * and the MCP endpoint (reported, never probed).
	 */
	@GetMapping("/api/remote/bridges")
	public BridgesResponse bridges() {
		List<BridgeInfo> probed = this.bridges.stream().map(this::probe).toList();
		return new BridgesResponse(probed, this.malformedBridges, this.mcpEndpoint);
	}

	/**
	 * Run one action on one named bridge and return the output that bridge's process
	 * produced.
	 * @param request the bridge to use, the action to run, and the text to send as its
	 * input.
	 * @return the sidecar's output, its decline, the unreachable state, or the refusal —
	 * the last one the only case where no address was contacted.
	 */
	@PostMapping("/api/remote/execute")
	public RemoteExecuteResponse execute(@RequestBody RemoteExecuteRequest request) {
		String action = request.action();
		if (action == null || action.isBlank()) {
			return RemoteExecuteResponse.refused(request.bridge(), action, "No action named; pick one it advertises.");
		}
		RemoteBridge bridge = configuredAs(request.bridge());
		if (bridge == null) {
			return RemoteExecuteResponse.refused(request.bridge(), action, "No remote bridge named '" + request.bridge()
					+ "' is configured. Configured: " + configuredNames() + ".");
		}
		ExecuteResult result = new RemoteActionBridge(bridge).execute(action,
				(request.input() == null) ? "" : request.input());
		if (result.success()) {
			return RemoteExecuteResponse.executed(bridge.name(), action, result.urlProbed(), result.output());
		}
		if (result.status() != null) {
			return RemoteExecuteResponse.declined(bridge.name(), action, result.urlProbed(), result.status(),
					result.sidecarBody());
		}
		return RemoteExecuteResponse.unreachable(bridge.name(), action, result.urlProbed(), result.error());
	}

	private BridgeInfo probe(RemoteBridge bridge) {
		BridgeState state = new RemoteActionBridge(bridge).probe();
		List<ActionInfo> actions = state.actions().stream().map(RemoteController::toInfo).toList();
		return new BridgeInfo(state.name(), state.baseUrl(), state.urlProbed(), state.reachable(), state.reason(),
				actions);
	}

	private static ActionInfo toInfo(AdvertisedAction action) {
		return new ActionInfo(action.name(), action.description(), action.inputTypeNames(), action.singleUserInput());
	}

	/**
	 * The configured bridge with this name, or {@code null} when configuration does not
	 * define one. Matched on the configured name only — never on an address a request
	 * supplied — which is what keeps the pane from becoming an open proxy.
	 * @param name the bridge name the request carried.
	 * @return the configured bridge, or null when no configured bridge has that name.
	 */
	private RemoteBridge configuredAs(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		for (RemoteBridge bridge : this.bridges) {
			if (bridge.name().equals(name.trim())) {
				return bridge;
			}
		}
		return null;
	}

	private String configuredNames() {
		if (this.bridges.isEmpty()) {
			return "none";
		}
		return this.bridges.stream().map(RemoteBridge::name).collect(Collectors.joining(", "));
	}

}
