/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.nio.file.Path;
import java.time.Duration;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The command tool, as the {@code shell} group offers it.
 *
 * <p>
 * This is the {@code poc/poc-7-runtime-adapter} {@code bash -lc} tool extracted, not
 * depended on: the poc tree is frozen evidence and the enforcer ban in
 * {@code prod/pom.xml} makes the edge impossible, so the behaviour was copied and the
 * four things the poc tool did not do were added — the working directory is confined to
 * the run root, the command has a wall-clock limit and gets its whole process tree killed
 * at it, both streams are captured rather than inherited, and output past the allowance
 * is truncated with the truncation stated instead of returning a shortened answer that
 * reads as complete.
 *
 * <p>
 * The bounds are set low here (two seconds, two hundred characters) so the two cases that
 * depend on them finish fast; the defaults are the documented 60 seconds and 20,000
 * characters.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.shell.enabled=true" })
class CommandToolTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-shell-");

	@Autowired
	private ApplicationContext context;

	@DynamicPropertySource
	static void runRoot(DynamicPropertyRegistry registry) {
		registry.add("smith.tools.coding.root", ROOT::toString);
		registry.add("smith.tools.coding.command-timeout", () -> "PT2S");
		registry.add("smith.tools.coding.max-output-chars", () -> "200");
	}

	@Test
	@DisplayName("a short command reports its exit code and its output")
	void shortCommandReportsCleanly() {
		String answer = run("echo hello from the run root");
		assertThat(answer).contains("exit code: 0").contains("timed out: no").contains("hello from the run root");
		assertThat(answer).contains("working directory: " + ROOT);
		assertThat(answer).doesNotContain("truncated");
	}

	@Test
	@DisplayName("a failing command reports the exit code it exited with")
	void failureIsReportedNotSwallowed() {
		assertThat(run("echo complaining >&2; exit 7")).contains("exit code: 7")
			.contains("stderr:")
			.contains("complaining");
	}

	@Test
	@DisplayName("a command past the limit is killed and reported as timed out")
	void longCommandTimesOut() {
		long started = System.nanoTime();
		String answer = run("sleep 120");
		long elapsed = Duration.ofNanos(System.nanoTime() - started).toSeconds();
		// The exit code is whatever the killed process reported, so only its line is
		// asserted; the verdict the run has to get right is the timeout one.
		assertThat(answer).contains("timed out: yes, killed at the limit").contains("exit code: ");
		assertThat(elapsed).as("killed at the two-second limit rather than waiting for sleep").isLessThan(60);
	}

	@Test
	@DisplayName("a runaway printer is truncated with the truncation stated")
	void runawayOutputIsTruncatedAndSaysSo() {
		String answer = run("for i in $(seq 1 4000); do echo \"line $i of the flood\"; done");
		assertThat(answer).contains("stdout truncated").contains("kept 200 of");
		assertThat(answer).contains("line 1 of the flood").doesNotContain("line 4000 of the flood");
	}

	@Test
	@DisplayName("a working directory outside the root is refused, naming path and root")
	void escapingWorkingDirectoryIsRefused() {
		String answer = ToolCalls.call(command(), "command", "pwd", "workingDirectory", "../");
		assertThat(answer).startsWith("ERROR:").contains("path=../").contains("root=" + ROOT);
		assertThat(answer).doesNotContain("stdout:");
	}

	@Test
	@DisplayName("a relative working directory inside the root is honoured")
	void innerWorkingDirectoryIsHonoured() throws Exception {
		Path sub = ROOT.resolve("sub");
		java.nio.file.Files.createDirectories(sub);
		java.nio.file.Files.writeString(sub.resolve("here.txt"), "here");
		assertThat(ToolCalls.call(command(), "command", "ls", "workingDirectory", "sub")).contains("here.txt");
	}

	private Tool command() {
		ToolGroup shell = this.context.getBeansOfType(ToolGroup.class)
			.values()
			.stream()
			.filter((group) -> group.getMetadata().getRole().equals(CodingToolGroups.SHELL_ROLE))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no shell group registered; roles "
					+ ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class))));
		return ToolCalls.tool(shell, CommandTool.RUN_COMMAND);
	}

	private String run(String command) {
		return ToolCalls.call(command(), CommandTool.COMMAND, command);
	}

}
