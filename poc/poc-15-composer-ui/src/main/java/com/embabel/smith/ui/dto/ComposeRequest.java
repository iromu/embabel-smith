/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * A canvas to export as a composite spec, named {@code name}.
 *
 * @param name the name to give the composite spec
 * @param nodes the placed spec names
 * @param edges the connections between the placed specs
 * @author Ivan Rodriguez
 */
public record ComposeRequest(String name, List<String> nodes, List<Edge> edges) {
}
