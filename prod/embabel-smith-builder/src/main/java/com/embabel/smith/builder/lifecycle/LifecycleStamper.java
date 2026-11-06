package com.embabel.smith.builder.lifecycle;

import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.SkillPack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Stamps the lifecycle sidecar for a published skill-pack (design D4, the POC 18 contract): the
 * content hash, the baseline eval pass-rate, and the initial lifecycle state. The builder always
 * records the initial state as {@link LifecycleState#INCUBATING}; the {@code incubating → active}
 * promotion is a later *transition* owned by the studio (change 05, POC 18's
 * {@code LifecycleManager.transition}), not by the builder.
 */
public class LifecycleStamper {

    /**
     * Computes the lifecycle metadata for a pack at its recorded baseline pass-rate. The state is
     * always the initial {@link LifecycleState#INCUBATING}; the baseline pass-rate is recorded but
     * does not drive the initial state.
     */
    public LifecycleMetadata stamp(SkillPack pack, double baselinePassRate) {
        return new LifecycleMetadata(SkillContentHash.of(pack), baselinePassRate,
                LifecycleState.INCUBATING, Instant.now().toString());
    }

    /** Writes the {@code lifecycle.yml} sidecar into the pack directory. */
    public void write(Path packDir, LifecycleMetadata metadata) throws IOException {
        String yaml = Yaml.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        Files.writeString(packDir.resolve("lifecycle.yml"), yaml);
    }
}
