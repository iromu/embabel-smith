/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * Whether a model endpoint is resolvable, the default model it would use, and any
 * configuration error. Drives the studio header's live "ready / no endpoint" indicator so
 * the user can see at a glance whether model-backed actions (Run, Eval, Auto-improve)
 * will work.
 *
 * @param configured whether a model endpoint is resolvable.
 * @param model the default model the endpoint would use.
 * @param error any configuration error (null when configured).
 * @author Ivan Rodriguez
 */
public record StatusResponse(boolean configured, String model, String error) {
}
