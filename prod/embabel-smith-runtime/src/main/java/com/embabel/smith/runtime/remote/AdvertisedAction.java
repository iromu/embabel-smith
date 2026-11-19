/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

import java.util.List;

/**
 * One action a sidecar advertised in its own catalog, named as the sidecar named it. The
 * product never invents an action, so this is a transcription of what
 * {@code GET /api/v1/actions} returned for one entry — including the declared input type
 * names, which decide whether the text-input pane can run it.
 *
 * @param name the action name exactly as the sidecar published it
 * @param description the sidecar's own description, or empty when it published none
 * @param inputTypeNames the declared input type names, in the order the sidecar listed
 * them
 * @author Ivan Rodriguez
 */
public record AdvertisedAction(String name, String description, List<String> inputTypeNames) {

	/** The one input type this product's single text box can supply. */
	public static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";

	/**
	 * Whether this action takes exactly one {@code UserInput}, which is the only shape
	 * the pane's single text field can feed. An action declaring anything else is still
	 * listed, with its declared types, but is not runnable from here.
	 * @return true when the action declares one {@code UserInput} input and nothing else
	 */
	public boolean singleUserInput() {
		return this.inputTypeNames.size() == 1 && USER_INPUT.equals(this.inputTypeNames.get(0));
	}

}
