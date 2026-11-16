/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.nio.file.Path;

/**
 * The output of the {@code writeAgentSpec} step: a reference to the agent spec now on
 * disk. This is the builder's goal state — "a validated agent spec exists on disk".
 *
 * @param path The directory on disk where the agent spec was written.
 * @param name The name of the written agent spec.
 * @param version The version of the written agent spec.
 * @author Ivan Rodriguez
 */
public record AgentSpecRef(Path path, String name, String version) {
}
