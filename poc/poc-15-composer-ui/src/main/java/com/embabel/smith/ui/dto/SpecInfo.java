/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * A spec offered by the palette: its name, the types it consumes from outside, and its
 * output.
 *
 * @param name the spec name
 * @param externalInputTypes the types the spec consumes from outside
 * @param outputType the spec's output type
 * @author Ivan Rodriguez
 */
public record SpecInfo(String name, List<String> externalInputTypes, String outputType) {
}
