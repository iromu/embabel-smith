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

package com.embabel.smith.builder.publish;

import java.io.IOException;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.common.model.AgentSpec;

/**
 * Publishes a built, validated, scanned agent spec (with its lifecycle sidecar) to a
 * destination. Implementations: {@link FolderPublisher} and {@link GitPublisher}. A
 * collision with an existing pack of the same name is reported as a
 * {@link CollisionException} unless {@code overwrite} is set.
 *
 * @author Ivan Rodriguez
 */
public interface Publisher extends AutoCloseable {

	/**
	 * Writes the pack and its {@code lifecycle.yml} sidecar to the destination.
	 * @param pack the built, validated agent spec to publish
	 * @param lifecycle the lifecycle metadata stamped into the sidecar
	 * @param overwrite replace an existing pack of the same name instead of colliding
	 * @param push (git only) push the local commit to the remote; ignored by the folder
	 * publisher
	 * @return the publish outcome: the location the pack now lives at and (for git)
	 * whether it was pushed
	 * @throws CollisionException when a same-named pack exists and {@code overwrite} is
	 * false
	 */
	PublishResult publish(AgentSpec pack, LifecycleMetadata lifecycle, boolean overwrite, boolean push)
			throws IOException;

	/** Releases any temporary resources (e.g. the git working tree). */
	@Override
	default void close() {
	}

}
