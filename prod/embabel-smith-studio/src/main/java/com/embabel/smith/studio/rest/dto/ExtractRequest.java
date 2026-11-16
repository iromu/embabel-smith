/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The extract request: a local docs {@code path} or a repository {@code url} to load the
 * business-process documents from and run the discovery prompt over. The path wins when
 * both are set.
 *
 * @param path the local docs path.
 * @param url the repository URL.
 * @param model the LLM to extract with; blank uses the configured default.
 * @author Ivan Rodriguez
 */
public record ExtractRequest(String path, String url, String model) {
}
