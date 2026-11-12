package com.embabel.smith.builder.core;

import java.nio.file.Path;

/**
 * The output of the {@code writeAgentSpec} step: a reference to the agent spec now on disk. This
 * is the builder's goal state — "a validated agent spec exists on disk".
 */
public record AgentSpecRef(Path path, String name, String version) {
}
