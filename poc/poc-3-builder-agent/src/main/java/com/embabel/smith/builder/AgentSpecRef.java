/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import java.nio.file.Path;

/**
 * The output of the {@code writeAgentSpec} step: a reference to the agent spec now on
 * disk. This is the builder's goal state — "a validated agent spec exists on disk".
 *
 * @param path the directory on disk holding the written agent spec
 * @param name the agent name from the written spec's manifest
 * @param version the version from the written spec's manifest
 * @author Ivan Rodriguez
 */
public record AgentSpecRef(Path path, String name, String version) {
}
