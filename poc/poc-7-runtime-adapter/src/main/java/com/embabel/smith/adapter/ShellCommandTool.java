package com.embabel.smith.adapter;

import com.embabel.agent.api.tool.Tool;

import java.util.List;

/**
 * The real shell executor behind the {@code shell} tool group. Runs a command via
 * {@link ProcessBuilder} and returns its exit code, stdout and stderr. This is the "bridge" the
 * skill-pack's {@code toolGroups: [shell]} action gets injected (doc 03 §2.3, Option A — the shell
 * tool group, the simplest of Embabel's three CLI bridges).
 */
public final class ShellCommandTool {

    public record CommandRequest(String command) {
    }

    public record CommandResult(int exitCode, String stdout, String stderr) {
    }

    public static final String NAME = "runCommand";

    public static Tool tool() {
        return Tool.fromFunction(NAME,
                "Run a shell command on the host and return its exit code, stdout and stderr.",
                CommandRequest.class, CommandResult.class,
                ShellCommandTool::run);
    }

    /** Pure, offline-testable: execute {@code request.command()} and capture the outcome. */
    public static CommandResult run(CommandRequest request) {
        try {
            ProcessBuilder builder = new ProcessBuilder(List.of("bash", "-lc", request.command()));
            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, stdout, stderr);
        } catch (Exception e) {
            return new CommandResult(-1, "", e.toString());
        }
    }
}
