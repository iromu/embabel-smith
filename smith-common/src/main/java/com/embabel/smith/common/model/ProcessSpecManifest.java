/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The manifest of a {@link ProcessSpec}: the process name, its version, and the pack
 * names of its agents <em>in run order</em>.
 *
 * <p>
 * The ordered pack names are what let a published process be read back in the order the
 * process runs: the packs live in one directory each, and a directory listing is
 * alphabetical, so the order the model produced has to be recorded rather than inferred.
 *
 * @param process the process name
 * @param version the version of the process
 * @param agents the pack names of the agents, in run order
 * @author Ivan Rodriguez
 */
@JsonInclude(Include.NON_NULL)
public record ProcessSpecManifest(String process, String version, List<String> agents) {

	public ProcessSpecManifest {
		agents = (agents != null) ? List.copyOf(agents) : List.of();
	}

	public ProcessSpecManifest(String process, String version) {
		this(process, version, List.of());
	}

}
