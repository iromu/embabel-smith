/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.embabel.smith.common.io.Yaml;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Reads the lifecycle state a pack directory declares in its {@code lifecycle.yml}
 * sidecar.
 *
 * <p>
 * The sidecar is written by the builder's lifecycle stamper and looks like:
 *
 * <pre>
 * contentHash: "0be2d86f26cd…"
 * baselinePassRate: 1.0
 * state: "INCUBATING"
 * updatedAt: "2026-11-17T21:35:38.683642067Z"
 * </pre>
 *
 * <p>
 * Only {@code state} is read, and only as a string, so the runtime — the reader side of
 * the improvement loop — does not depend on the module that writes the file. A pack with
 * no sidecar, or one that cannot be read, has <em>no declared state</em> rather than a
 * default state: that matches the fallback the improvement loop applies, so the two never
 * disagree about whether a pack is runnable. Packs published before stamping existed, and
 * the frozen POC sources, stay runnable for the same reason.
 *
 * @author Ivan Rodriguez
 */
public final class LifecycleSidecar {

	/** The sidecar file name the builder writes inside a pack directory. */
	public static final String FILE_NAME = "lifecycle.yml";

	private static final String RETIRED = "RETIRED";

	private LifecycleSidecar() {
	}

	/**
	 * The state the pack declares, or empty when it has no sidecar, the sidecar cannot be
	 * read, or it records no state.
	 * @param packRoot the pack's directory
	 * @return the declared state, or empty when there is no state recorded.
	 */
	public static Optional<String> declaredState(Path packRoot) {
		Path sidecar = packRoot.resolve(FILE_NAME);
		if (!Files.isRegularFile(sidecar)) {
			return Optional.empty();
		}
		try {
			Map<String, Object> document = Yaml.mapper()
				.readValue(Files.readString(sidecar), new TypeReference<Map<String, Object>>() {
				});
			return (document != null && document.get("state") instanceof String state && !state.isBlank())
					? Optional.of(state) : Optional.empty();
		}
		catch (IOException ex) {
			// An unreadable sidecar is no state recorded, not a refusal to run.
			return Optional.empty();
		}
	}

	/**
	 * Whether the pack declares itself retired — the one state that takes a pack out of
	 * the library. Matched case-insensitively because the state is written as an enum
	 * name but authored as a word.
	 * @param packRoot the pack's directory
	 * @return {@code true} when the sidecar declares {@code retired}.
	 */
	public static boolean retired(Path packRoot) {
		return declaredState(packRoot).filter(RETIRED::equalsIgnoreCase).isPresent();
	}

}
