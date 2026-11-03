package com.embabel.smith.selector.llm;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * The docker gate for the POC 19 launcher: the benchmark runs only when the model weights are
 * present and the Docker daemon is reachable (the model server is a container). Each missing
 * piece fails with a clear, actionable message rather than an opaque error.
 */
public final class ServingGate {

    /** The one-time command that obtains the weights (the {@code --local-dir} is filled in). */
    private static final String DOWNLOAD =
            "hf download iromu/Qwen3-0.6B-openjev-GGUF Qwen3-0.6B-openjev-Q8_0.gguf --local-dir ";

    /** The gate outcome: ready, or a failure message naming the missing piece. */
    public record Result(boolean ready, String failure) {
    }

    /** Docker-daemon reachability check (injectable for tests). */
    @FunctionalInterface
    public interface DockerProbe {
        boolean reachable();
    }

    /** Gate on the weights being present and Docker being reachable. */
    public static Result check(Path gguf, DockerProbe docker) {
        if (!gguf.toFile().exists()) {
            return new Result(false,
                    "Model weights missing: " + gguf.toAbsolutePath() + "\n"
                            + "Obtain them once with:\n  " + DOWNLOAD + gguf.getParent().toAbsolutePath());
        }
        if (!docker.reachable()) {
            return new Result(false,
                    "Docker daemon not reachable. The model server runs in a container defined in "
                            + "this module's compose.yml; start Docker and re-run.");
        }
        return new Result(true, null);
    }

    /** The default probe: the Docker CLI answering {@code docker info}. */
    public static DockerProbe dockerCli() {
        return () -> {
            try {
                Process process = new ProcessBuilder("docker", "info")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (Exception e) {
                return false;
            }
        };
    }

    private ServingGate() {
    }
}
