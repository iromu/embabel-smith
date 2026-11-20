/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * One action a reachable sidecar advertised, named as the sidecar named it. The product
 * adds no action of its own, so {@code inputTypeNames} is the sidecar's declaration and
 * {@code runnable} is the pane's honest answer about whether its single text box can feed
 * it.
 *
 * @param name the action name exactly as the sidecar published it.
 * @param description the sidecar's own description, empty when it published none.
 * @param inputTypeNames the declared input type names, listed so an unrunnable action
 * still explains what it wants.
 * @param runnable whether the action takes exactly one {@code UserInput} and so can be
 * run from this pane.
 * @author Ivan Rodriguez
 */
public record ActionInfo(String name, String description, List<String> inputTypeNames, boolean runnable) {
}
