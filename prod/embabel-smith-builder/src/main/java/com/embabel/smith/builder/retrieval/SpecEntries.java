/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Builds the index entry a pack contributes to retrieval. The matched text is the pack's
 * name, its goal description, every step description, then its external input type names
 * and its goal output type — carried verbatim in shape from the POC, because it is the
 * measured thing: type names alone made natural-language queries miss, since a task is
 * worded in meaning words rather than type names.
 *
 * @author Ivan Rodriguez
 */
public final class SpecEntries {

	private SpecEntries() {
	}

	/**
	 * The retrieval index entry for a pack. A pack with no descriptions still yields an
	 * entry, carrying its name and its type names.
	 * @param pack the pack to index.
	 * @return the index entry that retrieval scores the pack through.
	 */
	public static SpecEntry of(AgentSpec pack) {
		String name = pack.name();
		Set<String> externalInputs = externalInputTypes(pack);
		String outputType = pack.goalOutputType();
		StringBuilder text = new StringBuilder((name != null) ? name : "");
		if (pack.goal() != null && pack.goal().description() != null) {
			text.append(' ').append(pack.goal().description());
		}
		pack.steps().forEach((step) -> {
			if (step.description() != null) {
				text.append(' ').append(step.description());
			}
		});
		text.append(' ').append(String.join(" ", externalInputs));
		if (outputType != null) {
			text.append(' ').append(outputType);
		}
		return new SpecEntry(name, name, text.toString(), List.copyOf(externalInputs), outputType);
	}

	/**
	 * The types the pack consumes from outside itself: every step input type minus every
	 * type a step in the pack produces.
	 * @param pack the pack to read.
	 * @return the input types the pack needs from outside itself.
	 */
	private static Set<String> externalInputTypes(AgentSpec pack) {
		Set<String> produced = new LinkedHashSet<>();
		Set<String> inputs = new LinkedHashSet<>();
		pack.steps().forEach((step) -> {
			if (step.outputTypeName() != null) {
				produced.add(step.outputTypeName());
			}
			inputs.addAll(step.inputTypeNames());
		});
		inputs.removeAll(produced);
		return inputs;
	}

}
