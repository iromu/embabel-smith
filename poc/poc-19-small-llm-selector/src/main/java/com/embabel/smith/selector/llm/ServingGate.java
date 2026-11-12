/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.selector.llm;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * The docker gate for the POC 19 launcher: the benchmark runs only when the model weights
 * are present and the Docker daemon is reachable (the model server is a container). Each
 * missing piece fails with a clear, actionable message rather than an opaque error.
 *
 * @author Ivan Rodriguez
 */
public final class ServingGate {

	/**
	 * The one-time command that obtains the weights (the {@code --local-dir} is filled
	 * in).
	 */
	private static final String DOWNLOAD = "hf download iromu/Qwen3-0.6B-openjev-GGUF Qwen3-0.6B-openjev-Q8_0.gguf --local-dir ";

	/**
	 * Gate on the weights being present and Docker being reachable.
	 * @param gguf the path of the model weights file
	 * @param docker the probe that checks Docker-daemon reachability
	 * @return the gate outcome, ready or a failure message naming the missing piece
	 */
	public static Result check(Path gguf, DockerProbe docker) {
		if (!gguf.toFile().exists()) {
			return new Result(false, "Model weights missing: " + gguf.toAbsolutePath() + "\n"
					+ "Obtain them once with:\n  " + DOWNLOAD + gguf.getParent().toAbsolutePath());
		}
		if (!docker.reachable()) {
			return new Result(false, "Docker daemon not reachable. The model server runs in a container defined in "
					+ "this module's compose.yml; start Docker and re-run.");
		}
		return new Result(true, null);
	}

	/**
	 * The default probe: the Docker CLI answering {@code docker info}.
	 * @return the probe that shells out to the Docker CLI
	 */
	public static DockerProbe dockerCli() {
		return () -> {
			try {
				Process process = new ProcessBuilder("docker", "info").redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
				return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
			}
			catch (Exception ex) {
				return false;
			}
		};
	}

	private ServingGate() {
	}

	/**
	 * The gate outcome: ready, or a failure message naming the missing piece.
	 *
	 * @param ready whether the gate passed
	 * @param failure the failure message naming the missing piece, or {@code null} when ready
	 */
	public record Result(boolean ready, String failure) {
	}

	/** Docker-daemon reachability check (injectable for tests). */
	@FunctionalInterface
	public interface DockerProbe {

		boolean reachable();

	}

}
