package com.embabel.smith.builder.lifecycle;

import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.SkillPack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Stamps the lifecycle sidecar for a published skill-pack (design D4, the POC 18 contract): the
 * content hash, the baseline eval pass-rate, and the initial lifecycle state. A freshly built skill
 * that passed a clean baseline eval is promoted from {@link LifecycleState#INCUBATING} to
 * {@link LifecycleState#ACTIVE} (POC 18's transition for a clean baseline at or above the active
 * threshold); a weaker baseline stays incubating.
 */
public class LifecycleStamper {

    /** The baseline pass-rate at or above which a clean-baseline skill is promoted to ACTIVE (POC 18). */
    private static final double ACTIVE_THRESHOLD = 0.8;

    /** Computes the lifecycle metadata for a pack at its recorded baseline pass-rate. */
    public LifecycleMetadata stamp(SkillPack pack, double baselinePassRate) {
        LifecycleState state = baselinePassRate >= ACTIVE_THRESHOLD
                ? LifecycleState.ACTIVE
                : LifecycleState.INCUBATING;
        return new LifecycleMetadata(SkillContentHash.of(pack), baselinePassRate, state, Instant.now().toString());
    }

    /** Writes the {@code lifecycle.yml} sidecar into the pack directory. */
    public void write(Path packDir, LifecycleMetadata metadata) throws IOException {
        String yaml = Yaml.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        Files.writeString(packDir.resolve("lifecycle.yml"), yaml);
    }
}
