/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.io.IOException;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.common.model.AgentSpec;

/**
 * Publishes a built, validated, scanned artifact — an agent-spec pack or an Agent Skill —
 * to a destination. Implementations: {@link FolderPublisher} and {@link GitPublisher}.
 * The unit published is a {@link Publishable}: any named directory of files, so both
 * artifacts share one collision gate and one no-push-by-default rule. A collision with an
 * existing directory of the same name is reported as a {@link CollisionException} unless
 * {@code overwrite} is set.
 *
 * @author Ivan Rodriguez
 */
public interface Publisher extends AutoCloseable {

	/**
	 * Writes the artifact's files to the destination under its own name.
	 * @param unit the artifact to publish: its name, its baseline pass rate and its files
	 * @param overwrite replace an existing directory of the same name instead of
	 * colliding
	 * @param push (git only) push the local commit to the remote; ignored by the folder
	 * publisher
	 * @return the publish outcome: the location the artifact now lives at and (for git)
	 * whether it was pushed
	 * @throws CollisionException when a same-named directory exists and {@code overwrite}
	 * is false
	 */
	PublishResult publish(Publishable unit, boolean overwrite, boolean push) throws IOException;

	/**
	 * Convenience for the agent-spec target: the pack and its lifecycle sidecar as a
	 * {@link Publishable}.
	 * @param pack the built, validated agent spec to publish
	 * @param lifecycle the lifecycle metadata stamped into the sidecar
	 * @param overwrite replace an existing pack of the same name instead of colliding
	 * @param push (git only) push the local commit to the remote; ignored by the folder
	 * publisher
	 * @return the publish outcome
	 * @throws CollisionException when a same-named pack exists and {@code overwrite} is
	 * false
	 */
	default PublishResult publish(AgentSpec pack, LifecycleMetadata lifecycle, boolean overwrite, boolean push)
			throws IOException {
		return publish(new AgentSpecPublishable(pack, lifecycle), overwrite, push);
	}

	/** Releases any temporary resources (e.g. the git working tree). */
	@Override
	default void close() {
	}

}
