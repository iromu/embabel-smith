package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SkillContentHash;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.studio.rest.dto.PackInfo;
import com.embabel.smith.studio.rest.dto.SourceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline tests for the sources surface: the configured sources and the packs' lifecycle + drift. */
class SourceControllerTest {

    @Test
    void listsConfiguredSourcesWithKind() {
        SourceService sourceService = new SourceService("folder:/a, git:https://x/repo@main", "");
        SourceController controller = new SourceController(sourceService);
        List<SourceInfo> sources = controller.sources();
        assertThat(sources).containsExactly(
                new SourceInfo("folder:/a", "folder"),
                new SourceInfo("git:https://x/repo@main", "git"));
    }

    @Test
    void listsNoSourcesWhenUnconfigured() {
        SourceController controller = new SourceController(new SourceService("", ""));
        assertThat(controller.sources()).isEmpty();
    }

    @Test
    void listsPacksWithLifecycleAndNoDrift(@TempDir Path tmp) throws Exception {
        SkillPack active = RestTestFixtures.pack("active", "s1");
        RestTestFixtures.writePack(tmp, active);
        // The sidecar records the pack's semantic content hash (what the builder stamps); a matching
        // hash means no drift, so the pack must not be reported as drifted.
        writeSidecar(tmp.resolve("active"), SkillContentHash.of(active),
                LifecycleState.ACTIVE, 1.0);
        RestTestFixtures.writePack(tmp, RestTestFixtures.pack("plain", "s1"));

        SourceController controller = new SourceController(new SourceService("folder:" + tmp, ""));
        List<PackInfo> packs = controller.packs("folder:" + tmp);
        assertThat(packs).hasSize(2);

        PackInfo activeInfo = packs.stream().filter(p -> p.name().equals("active")).findFirst().orElseThrow();
        assertThat(activeInfo.lifecycleState()).isEqualTo("ACTIVE");
        assertThat(activeInfo.baselinePassRate()).isEqualTo(1.0);
        assertThat(activeInfo.drift()).isFalse();

        PackInfo plainInfo = packs.stream().filter(p -> p.name().equals("plain")).findFirst().orElseThrow();
        assertThat(plainInfo.lifecycleState()).isNull();
        assertThat(plainInfo.baselinePassRate()).isNull();
        assertThat(plainInfo.drift()).isFalse();
    }

    @Test
    void reportsDriftWhenContentHashMovedFromSidecar(@TempDir Path tmp) throws Exception {
        RestTestFixtures.writePack(tmp, RestTestFixtures.pack("drifted", "s1"));
        writeSidecar(tmp.resolve("drifted"), "a-stale-hash", LifecycleState.ACTIVE, 0.5);

        SourceController controller = new SourceController(new SourceService("folder:" + tmp, ""));
        List<PackInfo> packs = controller.packs("folder:" + tmp);
        assertThat(packs).hasSize(1);
        assertThat(packs.get(0).drift()).isTrue();
        assertThat(packs.get(0).lifecycleState()).isEqualTo("ACTIVE");
    }

    private static void writeSidecar(Path packDir, String hash, LifecycleState state, double baseline)
            throws Exception {
        LifecycleMetadata metadata =
                new LifecycleMetadata(hash, baseline, state, "2026-01-01T00:00:00Z");
        Files.writeString(packDir.resolve("lifecycle.yml"), Yaml.mapper().writeValueAsString(metadata));
    }
}
