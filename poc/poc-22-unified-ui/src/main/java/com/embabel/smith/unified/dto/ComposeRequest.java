/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * A canvas to export as a composite spec, named {@code name}.
 *
 * @param name The name for the composite spec.
 * @param nodes The placed spec names.
 * @param edges The connections between the nodes.
 * @author Ivan Rodriguez
 */
public record ComposeRequest(String name, List<String> nodes, List<Edge> edges) {
}
