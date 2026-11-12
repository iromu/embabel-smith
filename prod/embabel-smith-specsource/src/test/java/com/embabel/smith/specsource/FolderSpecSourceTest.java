package com.embabel.smith.specsource;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for folder-source resolution: one-level discovery, validation gating, warnings,
 * and missing-root failure.
 */
class FolderSpecSourceTest {

    @TempDir
    Path root;

    private final AgentSpecWriter writer = new AgentSpecWriter();

    private Path writeExplainer(String dirName) throws IOException {
        return writer.write(SampleAgentSpecs.explainer(), root.resolve(dirName));
    }

    @Test
    void discoversThreePacksOneLevelDeep() throws IOException {
        writeExplainer("alpha");
        writer.write(SampleAgentSpecs.singleStep(), root.resolve("beta"));
        writeExplainer("gamma");
        // not a pack candidate (no manifest.yml) — silently ignored
        Files.createDirectories(root.resolve("notes"));
        Files.writeString(root.resolve("notes").resolve("README.md"), "not a pack");

        ResolvedSpecSource resolved = new FolderSpecSource("folder:" + root, root).resolve();

        assertThat(resolved.packs()).hasSize(3);
        assertThat(resolved.packs()).extracting(ResolvedSpec::name)
                .containsExactly("explainer", "trivial", "explainer");
        assertThat(resolved.packs()).extracting(ResolvedSpec::root)
                .containsExactlyInAnyOrder(root.resolve("alpha"), root.resolve("beta"), root.resolve("gamma"));
        assertThat(resolved.warnings()).isEmpty();
        assertThat(resolved.origin().uri()).isEqualTo("folder:" + root);
        assertThat(resolved.origin().root()).isEqualTo(root);
    }

    @Test
    void invalidPackIsExcludedAndNamedInAWarning() throws IOException {
        writeExplainer("good");
        Path broken = writeExplainer("broken");
        Files.delete(broken.resolve("goal.yml"));

        ResolvedSpecSource resolved = new FolderSpecSource("folder:" + root, root).resolve();

        assertThat(resolved.packs()).hasSize(1);
        assertThat(resolved.packs().get(0).name()).isEqualTo("explainer");
        assertThat(resolved.warnings()).hasSize(1);
        assertThat(resolved.warnings().get(0)).contains("broken").contains("goal.yml");
    }

    @Test
    void emptyFolderResolvesToEmptyPackSetWithWarning() {
        ResolvedSpecSource resolved = new FolderSpecSource("folder:" + root, root).resolve();

        assertThat(resolved.packs()).isEmpty();
        assertThat(resolved.warnings()).hasSize(1);
        assertThat(resolved.warnings().get(0)).contains("no valid agent specs");
    }

    @Test
    void folderWithOnlyInvalidPacksResolvesToEmptyPackSetWithWarnings() throws IOException {
        Path broken = writeExplainer("broken");
        Files.delete(broken.resolve("goal.yml"));

        ResolvedSpecSource resolved = new FolderSpecSource("folder:" + root, root).resolve();

        assertThat(resolved.packs()).isEmpty();
        assertThat(resolved.warnings()).hasSize(2);
    }

    @Test
    void missingRootFailsWithAnErrorNamingThePath() {
        Path missing = root.resolve("does-not-exist");

        assertThatThrownBy(() -> new FolderSpecSource("folder:" + missing, missing).resolve())
                .isInstanceOf(SpecSourceException.class)
                .hasMessageContaining(missing.toString());
    }
}
