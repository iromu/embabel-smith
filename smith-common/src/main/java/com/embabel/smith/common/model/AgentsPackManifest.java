/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The manifest of an {@link AgentsPack}: its name, its version, and the pack names of its
 * agents <em>in run order</em>.
 *
 * <p>
 * The ordered pack names are what let a published agents-pack be read back in the order
 * it runs: the packs live in one directory each, and a directory listing is alphabetical,
 * so the order the model produced has to be recorded rather than inferred.
 *
 * <p>
 * The {@code process} key is the agents-pack's name: it predates the agents-pack
 * vocabulary, as does the {@code process.yml} filename this manifest is written to, and
 * both belong to the frozen on-disk format — neither is renamed with the type.
 *
 * @param process the name of the agents-pack, under the frozen {@code process} key
 * @param version the version of the agents-pack
 * @param agents the pack names of the agents, in run order
 * @author Ivan Rodriguez
 */
@JsonInclude(Include.NON_NULL)
public record AgentsPackManifest(String process, String version, List<String> agents) {

	public AgentsPackManifest {
		agents = (agents != null) ? List.copyOf(agents) : List.of();
	}

	public AgentsPackManifest(String process, String version) {
		this(process, version, List.of());
	}

}
