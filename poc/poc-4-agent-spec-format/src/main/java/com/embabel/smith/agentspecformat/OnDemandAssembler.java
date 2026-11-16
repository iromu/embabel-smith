/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.agentspecformat;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;

/**
 * Loads a single spec on demand: copies the goal and <em>only</em> the action steps in
 * the resolved chain (not the whole pack) into a flat directory for
 * {@code YmlStepSpecRepository}. This is the "progressive disclosure" the exported-goal
 * primitive enables — only the referenced steps are ingested.
 *
 * @author Ivan Rodriguez
 */
public class OnDemandAssembler {

	private static final CopyOption[] COPY = { StandardCopyOption.REPLACE_EXISTING };

	public Path assemble(Path packDir, Path targetDir, List<ActionStepSpec> chain) throws IOException {
		Files.createDirectories(targetDir);

		Files.copy(packDir.resolve("goal.yml"), targetDir.resolve("goal.yml"), COPY);
		Path stepsDir = packDir.resolve("steps");
		for (ActionStepSpec step : chain) {
			Files.copy(stepsDir.resolve(step.name() + ".yml"), targetDir.resolve(step.name() + ".yml"), COPY);
		}
		return targetDir;
	}

}
