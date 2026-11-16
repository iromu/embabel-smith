/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.lifecycle;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.ProcessSpec;

/**
 * A deterministic content hash of a process's load-bearing surface: every pack's own
 * {@link SpecContentHash} in run order, under the process name.
 *
 * <p>
 * The order is part of the hash because it is part of the artifact — the same packs wired
 * into a different order are a different process — and a drift in any one pack has to
 * move the hash the way a drift in a single pack's prompts moves {@link SpecContentHash}.
 *
 * @author Ivan Rodriguez
 */
public final class ProcessContentHash {

	private ProcessContentHash() {
	}

	/**
	 * The hash of a process as published.
	 * @param process the process to hash
	 * @return the hex sha-256 of its canonical form
	 */
	public static String of(ProcessSpec process) {
		StringBuilder canonical = new StringBuilder("process:").append(process.process()).append('\n');
		for (AgentSpec pack : process.agents()) {
			canonical.append("pack:").append(SpecContentHash.of(pack)).append('\n');
		}
		return ContentHash.sha256Hex(canonical.toString());
	}

}
