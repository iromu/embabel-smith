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

package com.embabel.smith.builder.lifecycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.AgentSpec;

/**
 * Stamps the lifecycle sidecar for a published agent spec (design D4, the POC 18
 * contract): the content hash, the baseline eval pass-rate, and the initial lifecycle
 * state. The builder always records the initial state as
 * {@link LifecycleState#INCUBATING}; the {@code incubating → active} promotion is a later
 * *transition* owned by the studio (change 05, POC 18's
 * {@code LifecycleManager.transition}), not by the builder.
 *
 * @author Ivan Rodriguez
 */
public class LifecycleStamper {

	/**
	 * Computes the lifecycle metadata for a pack at its recorded baseline pass-rate. The
	 * state is always the initial {@link LifecycleState#INCUBATING}; the baseline
	 * pass-rate is recorded but does not drive the initial state.
	 * @param pack the agent spec whose content hash is stamped.
	 * @param baselinePassRate the eval pass-rate recorded at baseline.
	 * @return the lifecycle metadata with the initial state.
	 */
	public LifecycleMetadata stamp(AgentSpec pack, double baselinePassRate) {
		return new LifecycleMetadata(SpecContentHash.of(pack), baselinePassRate, LifecycleState.INCUBATING,
				Instant.now().toString());
	}

	/**
	 * Writes the {@code lifecycle.yml} sidecar into the pack directory.
	 * @param packDir the pack directory the sidecar is written into.
	 * @param metadata the lifecycle metadata to serialize.
	 */
	public void write(Path packDir, LifecycleMetadata metadata) throws IOException {
		String yaml = Yaml.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
		Files.writeString(packDir.resolve("lifecycle.yml"), yaml);
	}

}
