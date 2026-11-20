/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.List;

/**
 * A lightweight index entry for a pack in a source — the metadata retrieval needs to
 * route a task, without holding the pack's full action chain. Copied from the retrieval
 * POC (extract, not depend: {@code poc/} stays frozen evidence and is never a dependency
 * of {@code prod/}).
 *
 * <p>
 * Progressive disclosure: only this metadata is held hot; the pack itself is read on
 * demand once it is selected.
 *
 * @param id stable pack id
 * @param name pack name
 * @param description what the pack does — the text that gets matched against the task
 * @param inputTypes the input type names the pack consumes from outside itself
 * @param outputType the output type name the pack produces
 * @author Ivan Rodriguez
 */
public record SpecEntry(String id, String name, String description, List<String> inputTypes, String outputType) {

	public SpecEntry {
		inputTypes = (inputTypes != null) ? List.copyOf(inputTypes) : List.of();
	}
}
