/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.nio.file.Path;

import com.embabel.smith.common.io.SpecFolders;

/**
 * Where the builder's own packs are addressed from.
 *
 * <p>
 * They are classpath resources under {@code src/main/resources/specs}, so a pack travels
 * with the module that runs it and nobody has to be standing in a particular directory to
 * find one. Before this they sat in a directory of the module root and were resolved
 * against the working directory, which meant the pack flows worked only from inside
 * {@code prod/embabel-smith-builder/} and failed with a pack-load error from the
 * repository root.
 *
 * <p>
 * The packs stay out of {@code src/main/resources/prompts}, where the scorer prompts
 * live, and that separation is load-bearing rather than tidiness: {@code prompts/} holds
 * the referee of an improvement loop and {@code specs/} holds the surface it may edit,
 * which is the placement boundary
 * {@code com.embabel.smith.builder.skill.ScorerPromptBoundaryTest} guards by content.
 *
 * @author Ivan Rodriguez
 */
public final class BuilderPackPaths {

	/**
	 * The resource path the builder packs live below.
	 */
	public static final String ROOT = "specs";

	private BuilderPackPaths() {
	}

	/**
	 * The directory of one builder pack.
	 * @param packName the pack name, which is also its directory and manifest name, e.g.
	 * {@code builder-spec-one-shot}
	 * @return the pack's directory, addressed through the class loader rather than the
	 * working directory
	 */
	public static Path directory(String packName) {
		return SpecFolders.classpath(ROOT + "/" + packName);
	}

	/**
	 * The directory holding every builder pack.
	 * @return the pack root, for a caller that walks all six rather than naming one
	 */
	public static Path root() {
		return SpecFolders.classpath(ROOT);
	}

}
