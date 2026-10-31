package com.embabel.smith.common.io;

import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies task 1.2: a skill-pack written to disk can be read back losslessly.
 */
class SkillPackRoundTripTest {

    @Test
    void writesThenReadsBackLosslessly(@TempDir Path dir) throws Exception {
        SkillPack pack = SampleSkillPacks.explainer();

        Path written = new SkillPackWriter().write(pack, dir);
        SkillPack readBack = new SkillPackReader().read(written);

        assertThat(readBack).isEqualTo(pack);
        assertThat(readBack.name()).isEqualTo("explainer");
        assertThat(readBack.goalOutputType()).isEqualTo(SampleSkillPacks.BACKGROUND_MESSAGE);
        assertThat(readBack.steps()).hasSize(2);
        assertThat(readBack.types()).hasSize(1);
    }

    @Test
    void producesTheExpectedDirectoryLayout(@TempDir Path dir) throws Exception {
        new SkillPackWriter().write(SampleSkillPacks.explainer(), dir);

        assertThat(Files.isRegularFile(dir.resolve("manifest.yml"))).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("goal.yml"))).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("steps/extractTopic.yml"))).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("steps/writeExplainer.yml"))).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("types/TopicBrief.yml"))).isTrue();
    }

    @Test
    void roundTripsAPackWithNoIntermediateTypes(@TempDir Path dir) throws Exception {
        SkillPack pack = SampleSkillPacks.singleStep();

        new SkillPackWriter().write(pack, dir);
        SkillPack readBack = new SkillPackReader().read(dir);

        assertThat(readBack).isEqualTo(pack);
        assertThat(readBack.types()).isEmpty();
        // the empty types/ dir is still present (with a .gitkeep placeholder)
        assertThat(Files.isDirectory(dir.resolve("types"))).isTrue();
    }

    @Test
    void rewritesIdempotentlyClearingStaleFiles(@TempDir Path dir) throws Exception {
        SkillPackWriter writer = new SkillPackWriter();

        // First write a pack that has two steps and an intermediate type.
        writer.write(SampleSkillPacks.explainer(), dir);
        assertThat(Files.isRegularFile(dir.resolve("types/TopicBrief.yml"))).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("steps/extractTopic.yml"))).isTrue();

        // Re-write the SAME directory with a pack that has one step and no types.
        writer.write(SampleSkillPacks.singleStep(), dir);

        SkillPack readBack = new SkillPackReader().read(dir);
        assertThat(readBack).isEqualTo(SampleSkillPacks.singleStep());
        assertThat(readBack.types()).isEmpty();
        // Stale files from the first write are gone; the empty types/ now has a .gitkeep.
        assertThat(Files.isRegularFile(dir.resolve("types/TopicBrief.yml"))).isFalse();
        assertThat(Files.isRegularFile(dir.resolve("steps/extractTopic.yml"))).isFalse();
        assertThat(Files.isRegularFile(dir.resolve("steps/say.yml"))).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("types/.gitkeep"))).isTrue();
    }
}
