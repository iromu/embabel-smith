/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The import-a-pack request (change: smith-studio-postgres-storage): a local filesystem
 * path to an agent-spec directory to read into the storage database.
 *
 * @param path the local path to the agent-spec directory.
 * @author Ivan Rodriguez
 */
public record ImportPackRequest(String path) {
}
