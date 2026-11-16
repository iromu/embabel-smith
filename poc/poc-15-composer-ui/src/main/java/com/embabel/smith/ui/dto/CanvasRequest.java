/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * A canvas to validate: the placed specs (by name) and the connections between them.
 *
 * @param nodes the placed spec names
 * @param edges the connections between the placed specs
 * @author Ivan Rodriguez
 */
public record CanvasRequest(List<String> nodes, List<Edge> edges) {
}
