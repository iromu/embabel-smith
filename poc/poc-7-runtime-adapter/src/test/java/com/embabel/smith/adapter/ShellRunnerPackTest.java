package com.embabel.smith.adapter;

import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.validation.SkillPackValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof that the CLI skill-pack is a well-formed skill whose action opts into the
 * {@code shell} tool group (the actual GOAP run over a live command is endpoint-gated).
 */
class ShellRunnerPackTest {

    private final SkillPackReader reader = new SkillPackReader();
    private final SkillPackValidator validator = new SkillPackValidator();

    @Test
    void theCliPackIsValidAndItsActionUsesTheShellToolGroup() throws IOException {
        Path pack = repoRoot().resolve("skill-packs/shell-runner");

        assertThat(validator.validate(pack).isValid())
                .as("issues: " + validator.validate(pack).issues())
                .isTrue();

        SkillPack loaded = reader.read(pack);
        ActionStepSpec action = loaded.step("runCli");
        assertThat(action).isNotNull();
        assertThat(action.toolGroups()).containsExactly(ShellToolGroup.ROLE);
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("skill-packs/shell-runner"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root (skill-packs/shell-runner not found)");
    }
}
