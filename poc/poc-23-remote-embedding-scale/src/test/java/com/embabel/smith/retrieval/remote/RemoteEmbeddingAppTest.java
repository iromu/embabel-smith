package com.embabel.smith.retrieval.remote;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteEmbeddingAppTest {

	@Test
	void runPrintsBothScenariosAndAllMethods() {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

		// A stub transport keeps this fully offline; small scale sizes keep it fast.
		try (RemoteEmbedder embedder = StubEmbedding.embedder(StubEmbedding::hashVec)) {
			RemoteEmbeddingApp.run(embedder, "http://stub", "stub-model", List.of(12, 24), out);
		}

		String report = buffer.toString(StandardCharsets.UTF_8);
		assertThat(report).contains("Scenario 1: C10-comparable")
			.contains("Scenario 2: scaling dimension")
			.contains("lexical (word TF)")
			.contains("dense (feature hash)")
			.contains("embedding (nomic, remote)")
			.contains("index-build ms")
			.contains("per-query ms");
	}

	@Test
	void scaleSizesAreReportedAscendingEvenWhenGivenUnsorted() {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
		try (RemoteEmbedder embedder = StubEmbedding.embedder(StubEmbedding::hashVec)) {
			// given unsorted; run() sorts ascending
			RemoteEmbeddingApp.run(embedder, "http://stub", "stub-model", List.of(24, 12), out);
		}
		String report = buffer.toString(StandardCharsets.UTF_8);
		String scale = report.substring(report.indexOf("Scenario 2"));
		// the smaller size's row precedes the larger size's row
		assertThat(scale.indexOf(" 12 ")).isGreaterThan(-1);
		assertThat(scale.indexOf(" 24 ")).isGreaterThan(scale.indexOf(" 12 "));
	}

}
