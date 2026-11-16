/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

/**
 * One ranked retrieval hit: the spec, its retrieval text, its (curation-adjusted) score,
 * its output type, and its lifecycle state (POC 18 — the retrieval pane curates the
 * library, not just scores it).
 *
 * @param name the spec name
 * @param description the spec's retrieval text
 * @param score the curation-adjusted retrieval score
 * @param outputType the spec's output type
 * @param state the spec's lifecycle state
 * @author Ivan Rodriguez
 */
public record RetrieveResult(String name, String description, double score, String outputType, String state) {
}
