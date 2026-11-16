/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The discover request: a local docs {@code path} or a repository {@code url} to load the
 * business-process documents from. The path wins when both are set.
 *
 * @param path the local docs path.
 * @param url the repository URL.
 * @author Ivan Rodriguez
 */
public record DiscoverRequest(String path, String url) {
}
