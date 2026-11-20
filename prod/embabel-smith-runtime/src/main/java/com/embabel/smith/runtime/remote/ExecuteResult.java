/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

/**
 * The outcome of asking a sidecar to run one action. Three states, each with the evidence
 * the product actually has: the {@code content} the sidecar returned when it ran the
 * action; the status and untouched body when the sidecar refused the request; or the
 * failure when no process answered. The refusal case carries the sidecar's body verbatim
 * so a rejected action name is reported rather than replaced by a fabricated result.
 *
 * @param success whether the sidecar ran the action and answered success
 * @param urlProbed the execute URL that was contacted
 * @param output the {@code content} the sidecar returned, only on success
 * @param status the sidecar's HTTP status when it refused, otherwise null
 * @param sidecarBody the sidecar's untouched body when it refused, otherwise null
 * @param error the failure that stopped the request reaching the sidecar, otherwise null
 * @author Ivan Rodriguez
 */
public record ExecuteResult(boolean success, String urlProbed, String output, Integer status, String sidecarBody,
		String error) {

	/**
	 * The sidecar ran the action and returned its output.
	 * @param urlProbed the execute URL that was contacted
	 * @param output the {@code content} field of the sidecar's reply
	 * @return the success result
	 */
	public static ExecuteResult executed(String urlProbed, String output) {
		return new ExecuteResult(true, urlProbed, output, null, null, null);
	}

	/**
	 * The sidecar answered, but refused the request — the status and body are its, not
	 * ours.
	 * @param urlProbed the execute URL that was contacted
	 * @param status the HTTP status the sidecar answered with
	 * @param sidecarBody the body the sidecar answered with, unaltered
	 * @return the refusal result
	 */
	public static ExecuteResult refused(String urlProbed, int status, String sidecarBody) {
		return new ExecuteResult(false, urlProbed, null, status, sidecarBody, null);
	}

	/**
	 * No process answered, so nothing ran.
	 * @param urlProbed the execute URL that was contacted
	 * @param error the failure that stopped the request reaching the sidecar
	 * @return the unreachable result
	 */
	public static ExecuteResult unreachable(String urlProbed, String error) {
		return new ExecuteResult(false, urlProbed, null, null, null, error);
	}

}
