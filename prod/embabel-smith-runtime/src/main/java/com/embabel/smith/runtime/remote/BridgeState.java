/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

import java.util.List;

/**
 * What one probe of one bridge established: whether it answered, the URL that was probed,
 * the failure when it did not, and the actions it advertised when it did. An absent
 * sidecar is a value here rather than an exception, which is what lets a pane render "not
 * running" as a state instead of a failed request.
 *
 * @param name the configured bridge name
 * @param baseUrl the configured base URL, for display beside the bridge name
 * @param urlProbed the exact catalog URL that was contacted
 * @param reachable whether the catalog call answered with a JSON array
 * @param reason the failure, only when unreachable
 * @param actions the advertised actions, empty when unreachable or when the catalog was
 * empty
 * @author Ivan Rodriguez
 */
public record BridgeState(String name, String baseUrl, String urlProbed, boolean reachable, String reason,
		List<AdvertisedAction> actions) {

	/**
	 * A bridge whose catalog answered, carrying what it advertised.
	 * @param bridge the configured bridge
	 * @param urlProbed the catalog URL that was contacted
	 * @param actions the actions the catalog listed
	 * @return the reachable state
	 */
	public static BridgeState reachable(RemoteBridge bridge, String urlProbed, List<AdvertisedAction> actions) {
		return new BridgeState(bridge.name(), bridge.baseUrl(), urlProbed, true, null, List.copyOf(actions));
	}

	/**
	 * A bridge that did not answer its catalog, keeping why.
	 * @param bridge the configured bridge
	 * @param urlProbed the catalog URL that was contacted
	 * @param reason the status or failure that decided the state
	 * @return the unreachable state
	 */
	public static BridgeState unreachable(RemoteBridge bridge, String urlProbed, String reason) {
		return new BridgeState(bridge.name(), bridge.baseUrl(), urlProbed, false, reason, List.of());
	}

}
