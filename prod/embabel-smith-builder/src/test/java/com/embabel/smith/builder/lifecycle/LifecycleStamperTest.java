package com.embabel.smith.builder.lifecycle;

import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the lifecycle stamper (task 3.3): the metadata records the content hash, the
 * baseline pass-rate, and the state; a clean baseline promotes to ACTIVE; and the {@code
 * lifecycle.yml} sidecar carries that content and round-trips.
 */
class LifecycleStamperTest {

    private final LifecycleStamper stamper = new LifecycleStamper();

    @Test
    void stampsTheBaselineMetadata() {
        SkillPack pack = SampleSkillPacks.explainer();

        LifecycleMetadata metadata = stamper.stamp(pack, 1.0);

        assertThat(metadata.contentHash()).isEqualTo(SkillContentHash.of(pack));
        assertThat(metadata.baselinePassRate()).isEqualTo(1.0);
        assertThat(metadata.state()).isEqualTo(LifecycleState.ACTIVE); // clean baseline >= 0.8
        assertThat(metadata.updatedAt()).isNotBlank();
    }

    @Test
    void aWeakBaselineStaysIncubating() {
        LifecycleMetadata metadata = stamper.stamp(SampleSkillPacks.explainer(), 0.5);
        assertThat(metadata.state()).isEqualTo(LifecycleState.INCUBATING);
    }

    @Test
    void writesTheSidecarWithTheRecordedContent(@TempDir Path dir) throws Exception {
        SkillPack pack = SampleSkillPacks.explainer();
        LifecycleMetadata metadata = stamper.stamp(pack, 1.0);

        stamper.write(dir, metadata);

        String yaml = Files.readString(dir.resolve("lifecycle.yml"));
        assertThat(yaml).contains(metadata.contentHash());
        assertThat(yaml).contains("ACTIVE");

        LifecycleMetadata read = Yaml.mapper().readValue(yaml, LifecycleMetadata.class);
        assertThat(read.contentHash()).isEqualTo(metadata.contentHash());
        assertThat(read.baselinePassRate()).isEqualTo(1.0);
        assertThat(read.state()).isEqualTo(LifecycleState.ACTIVE);
    }
}
