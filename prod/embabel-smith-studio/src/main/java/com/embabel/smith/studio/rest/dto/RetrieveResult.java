/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * One ranked, curated hit in a retrieve response.
 *
 * @param name the pack name.
 * @param description the text the pack was matched on — its goal and step descriptions
 * plus its I/O type names.
 * @param score the curated score, lower than the scorer's own when the pack is degraded.
 * @param outputType the type the pack's exported goal produces.
 * @param lifecycleState the pack's recorded lifecycle state, or {@code UNKNOWN} when it
 * carries no lifecycle sidecar.
 * @author Ivan Rodriguez
 */
public record RetrieveResult(String name, String description, double score, String outputType, String lifecycleState) {
}
