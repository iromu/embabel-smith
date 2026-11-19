/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A request to run one action on one bridge. The bridge is named, never addressed: the
 * address comes from configuration, so a request cannot point the product at a host its
 * operator did not write down.
 *
 * @param bridge the configured bridge to run on.
 * @param action the action to run, as that bridge's catalog named it.
 * @param input the text to send as the action's {@code UserInput} content.
 * @author Ivan Rodriguez
 */
public record RemoteExecuteRequest(String bridge, String action, String input) {
}
