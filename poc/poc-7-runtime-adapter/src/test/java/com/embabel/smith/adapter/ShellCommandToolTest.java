package com.embabel.smith.adapter;

import com.embabel.agent.api.tool.Tool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof that the shell bridge actually executes a command (no model involved):
 * the {@code runCommand} tool runs a real process and returns its exit code and stdout.
 */
class ShellCommandToolTest {

	@Test
	void runsARealCommandAndReturnsItsOutput() {
		ShellCommandTool.CommandResult result = ShellCommandTool
			.run(new ShellCommandTool.CommandRequest("echo smith-poc7"));

		assertThat(result.exitCode()).isEqualTo(0);
		assertThat(result.stdout()).contains("smith-poc7");
	}

	@Test
	void surfacesNonZeroExitCodes() {
		ShellCommandTool.CommandResult result = ShellCommandTool.run(new ShellCommandTool.CommandRequest("exit 3"));

		assertThat(result.exitCode()).isEqualTo(3);
	}

	@Test
	void exposesAToolNamedRunCommand() {
		Tool tool = ShellCommandTool.tool();
		assertThat(tool.getDefinition().getName()).isEqualTo(ShellCommandTool.NAME);
	}

}
