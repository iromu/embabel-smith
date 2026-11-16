/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * One action in a compiled draft: its name, the types it consumes, and the type it
 * produces.
 *
 * @param name the action name
 * @param inputTypes the types the action consumes
 * @param outputType the type the action produces
 * @author Ivan Rodriguez
 */
public record ActionInfo(String name, List<String> inputTypes, String outputType) {
}
