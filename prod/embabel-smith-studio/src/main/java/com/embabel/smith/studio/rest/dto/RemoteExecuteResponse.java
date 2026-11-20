/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The outcome of asking one bridge to run one action. Four states, and each carries only
 * what actually happened: the sidecar's own {@code output}; the sidecar's own status and
 * untouched {@code sidecarBody} when it declined the request; {@code urlProbed} with the
 * {@code reason} when no process answered; or {@code error} with no URL at all when the
 * product refused the request before contacting anything.
 *
 * @param error why the product refused to try, null when it tried. Null {@code urlProbed}
 * beside it is the proof that no outbound request was made.
 * @param bridge the bridge the request named.
 * @param action the action the request named.
 * @param urlProbed the execute URL that was contacted, null when nothing was contacted.
 * @param output the content the sidecar returned, null unless it ran the action.
 * @param reason why no process answered, null when one did.
 * @param status the HTTP status the sidecar answered with when it declined, otherwise
 * null.
 * @param sidecarBody the sidecar's untouched body when it declined, otherwise null.
 * @author Ivan Rodriguez
 */
public record RemoteExecuteResponse(String error, String bridge, String action, String urlProbed, String output,
		String reason, Integer status, String sidecarBody) {

	/**
	 * The sidecar ran the action and returned its output.
	 * @param bridge the bridge that ran it.
	 * @param action the action it ran.
	 * @param urlProbed the execute URL that was contacted.
	 * @param output the content the sidecar returned.
	 * @return the success response.
	 */
	public static RemoteExecuteResponse executed(String bridge, String action, String urlProbed, String output) {
		return new RemoteExecuteResponse(null, bridge, action, urlProbed, output, null, null, null);
	}

	/**
	 * The sidecar declined the request; its status and body are passed through untouched
	 * rather than turned into a result.
	 * @param bridge the bridge that declined.
	 * @param action the action it declined.
	 * @param urlProbed the execute URL that was contacted.
	 * @param status the HTTP status it answered with.
	 * @param sidecarBody the body it answered with.
	 * @return the decline response.
	 */
	public static RemoteExecuteResponse declined(String bridge, String action, String urlProbed, int status,
			String sidecarBody) {
		return new RemoteExecuteResponse(null, bridge, action, urlProbed, null, null, status, sidecarBody);
	}

	/**
	 * No process answered, which is a state the pane shows rather than an error it
	 * throws.
	 * @param bridge the bridge that did not answer.
	 * @param action the action that therefore did not run.
	 * @param urlProbed the execute URL that was contacted.
	 * @param reason the failure that reached no process.
	 * @return the unreachable response.
	 */
	public static RemoteExecuteResponse unreachable(String bridge, String action, String urlProbed, String reason) {
		return new RemoteExecuteResponse(null, bridge, action, urlProbed, null, reason, null, null);
	}

	/**
	 * The product refused before contacting anything, so no URL is reported.
	 * @param bridge the bridge the request named.
	 * @param action the action the request named.
	 * @param error why the request was refused.
	 * @return the refusal response.
	 */
	public static RemoteExecuteResponse refused(String bridge, String action, String error) {
		return new RemoteExecuteResponse(error, bridge, action, null, null, null, null, null);
	}

}
