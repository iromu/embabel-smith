/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
