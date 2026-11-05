package com.embabel.smith.runtime.core;

import com.embabel.smith.runtime.TestPacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepSpecAssemblerTest {

    private final StepSpecAssembler assembler = new StepSpecAssembler();

    @Test
    void assemblesASinglePackWithRenamedGoalAndSteps(@TempDir Path tmp) throws IOException {
        Path target = assembler.assemble(List.of(TestPacks.root().resolve("extract")), tmp.resolve("steps"));

        assertThat(Files.isRegularFile(target.resolve("goal-0.yml"))).isTrue();
        assertThat(Files.isRegularFile(target.resolve("0-extractTopic.yml"))).isTrue();
        // manifest and types are not step specs
        assertThat(Files.isRegularFile(target.resolve("manifest.yml"))).isFalse();
        assertThat(Files.isDirectory(target.resolve("types"))).isFalse();
    }

    @Test
    void sameNamedStepFilesFromDifferentPacksDoNotClobberEachOther(@TempDir Path tmp) throws IOException {
        Path target = assembler.assemble(
                List.of(TestPacks.root().resolve("dup-a"), TestPacks.root().resolve("dup-b")),
                tmp.resolve("steps"));

        assertThat(Files.isRegularFile(target.resolve("goal-0.yml"))).isTrue();
        assertThat(Files.isRegularFile(target.resolve("goal-1.yml"))).isTrue();
        assertThat(Files.isRegularFile(target.resolve("0-aStep.yml"))).isTrue();
        assertThat(Files.isRegularFile(target.resolve("1-bStep.yml"))).isTrue();
    }

    @Test
    void aPackWithoutGoalYamlFails(@TempDir Path tmp) {
        assertThatThrownBy(() -> assembler.assemble(List.of(TestPacks.root().resolve("dup-a/steps")), tmp.resolve("steps")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no goal.yml");
    }
}
