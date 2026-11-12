package com.embabel.smith.selector.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ServingGateTest {

	@TempDir
	Path dir;

	@Test
	void missingWeightsNameTheFileAndTheDownloadCommand() throws IOException {
		Path gguf = dir.resolve("Qwen3-0.6B-openjev-Q8_0.gguf");
		ServingGate.Result result = ServingGate.check(gguf, () -> true);
		assertThat(result.ready()).isFalse();
		assertThat(result.failure()).contains(gguf.toAbsolutePath().toString())
			.contains("hf download iromu/Qwen3-0.6B-openjev-GGUF Qwen3-0.6B-openjev-Q8_0.gguf --local-dir "
					+ dir.toAbsolutePath());
	}

	@Test
	void unreachableDockerNamesIt() throws IOException {
		Path gguf = dir.resolve("Qwen3-0.6B-openjev-Q8_0.gguf");
		Files.createFile(gguf);
		ServingGate.Result result = ServingGate.check(gguf, () -> false);
		assertThat(result.ready()).isFalse();
		assertThat(result.failure()).contains("Docker daemon not reachable").contains("compose.yml");
	}

	@Test
	void weightsPresentAndDockerUpIsReady() throws IOException {
		Path gguf = dir.resolve("Qwen3-0.6B-openjev-Q8_0.gguf");
		Files.createFile(gguf);
		assertThat(ServingGate.check(gguf, () -> true).ready()).isTrue();
	}

}
