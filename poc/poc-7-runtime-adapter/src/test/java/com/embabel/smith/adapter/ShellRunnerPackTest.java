package com.embabel.smith.adapter;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof that the CLI agent spec is a well-formed spec whose action opts into the
 * {@code shell} tool group (the actual GOAP run over a live command is endpoint-gated).
 */
class ShellRunnerPackTest {

    private final AgentSpecReader reader = new AgentSpecReader();
    private final AgentSpecValidator validator = new AgentSpecValidator();

    @Test
    void theCliPackIsValidAndItsActionUsesTheShellToolGroup() throws IOException {
        Path pack = repoRoot().resolve("agent-specs/shell-runner");

        assertThat(validator.validate(pack).isValid())
                .as("issues: " + validator.validate(pack).issues())
                .isTrue();

        AgentSpec loaded = reader.read(pack);
        ActionStepSpec action = loaded.step("runCli");
        assertThat(action).isNotNull();
        assertThat(action.toolGroups()).containsExactly(ShellToolGroup.ROLE);
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("agent-specs/shell-runner"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root (agent-specs/shell-runner not found)");
    }
}
