package com.embabel.smith.retrieval.gliner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the local-model gate: a missing or incomplete GLiNER2 bundle throws a clear,
 * actionable error (naming the missing files and the download command), and a complete
 * bundle passes.
 */
class GlinerModelTest {

	@Test
	void missingBundleRootThrowsAnActionableError(@TempDir Path dir) {
		Path absent = dir.resolve("does-not-exist");
		assertThatThrownBy(() -> new GlinerModel(absent).requireAvailable()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("GLiNER2 model bundle is missing or incomplete")
			.hasMessageContaining("does-not-exist")
			.hasMessageContaining("local-model-gated")
			.hasMessageContaining("hf download gravitee-io/gliner4j-gliner2-base-v1")
			.hasMessageContaining("BUILDER_GLINER_MODEL_DIR");
	}

	@Test
	void incompleteBundleNamesTheSpecificMissingFile(@TempDir Path dir) throws IOException {
		// A partial bundle: config + tokenizer present, but the ONNX graph is missing.
		Files.writeString(dir.resolve("gliner4j_config.json"), "{}");
		Files.writeString(dir.resolve("tokenizer.json"), "{}");

		assertThatThrownBy(() -> new GlinerModel(dir).requireAvailable()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(GlinerModel.VARIANT + "/classifier_full.onnx");
	}

	@Test
	void completeBundlePassesVerification(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("gliner4j_config.json"), "{}");
		Files.writeString(dir.resolve("tokenizer.json"), "{}");
		Files.createDirectories(dir.resolve(GlinerModel.VARIANT));
		Files.writeString(dir.resolve(GlinerModel.VARIANT).resolve("classifier_full.onnx"), "onnx-bytes");

		assertThatCode(() -> new GlinerModel(dir).requireAvailable()).doesNotThrowAnyException();
	}

}
