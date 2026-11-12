package com.embabel.smith.builder.publish;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;

import java.io.IOException;

/**
 * Publishes a built, validated, scanned agent spec (with its lifecycle sidecar) to a destination.
 * Implementations: {@link FolderPublisher} and {@link GitPublisher}. A collision with an existing
 * pack of the same name is reported as a {@link CollisionException} unless {@code overwrite} is set.
 */
public interface Publisher extends AutoCloseable {

    /**
     * Writes the pack and its {@code lifecycle.yml} sidecar to the destination.
     *
     * @param overwrite replace an existing pack of the same name instead of colliding
     * @param push      (git only) push the local commit to the remote; ignored by the folder publisher
     * @throws CollisionException when a same-named pack exists and {@code overwrite} is false
     */
    PublishResult publish(AgentSpec pack, LifecycleMetadata lifecycle, boolean overwrite, boolean push) throws IOException;

    /** Releases any temporary resources (e.g. the git working tree). */
    @Override
    default void close() {
    }
}
