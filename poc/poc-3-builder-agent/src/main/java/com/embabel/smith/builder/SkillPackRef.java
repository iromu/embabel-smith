package com.embabel.smith.builder;

import java.nio.file.Path;

/**
 * The output of the {@code writeSkillPack} step: a reference to the skill-pack now on disk. This
 * is the builder's goal state — "a validated skill-pack exists on disk".
 */
public record SkillPackRef(Path path, String name, String version) {
}
