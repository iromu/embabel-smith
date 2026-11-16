/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * One action in a compiled draft: its name, the types it consumes, and the type it
 * produces.
 *
 * @param name The action name.
 * @param inputTypes The types the action consumes.
 * @param outputType The type the action produces.
 * @author Ivan Rodriguez
 */
public record ActionInfo(String name, List<String> inputTypes, String outputType) {
}
