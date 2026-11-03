package com.embabel.smith.retrieval.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the report shape and the model gate offline, using a deterministic stub embedder (no ONNX,
 * no model). The live three-method benchmark is the local-model-gated part.
 */
class EmbeddingRetrievalAppTest {

    /** A deterministic, dependency-free embedder: a small hashed dense vector per text. */
    private static final class HashEmbedder implements Embedder {
        private static final int DIMS = 64;

        @Override
        public float[] embed(String text) {
            float[] v = new float[DIMS];
            for (String w : (text == null ? "" : text).toLowerCase().split("[^a-z0-9]+")) {
                if (w.length() >= 2) {
                    int h = w.hashCode();
                    v[Math.floorMod(h, DIMS)] += (h >>> 31) == 0 ? 1 : -1;
                }
            }
            return EmbeddingMath.normalize(v);
        }
    }

    @Test
    void runPrintsTheThreeMethodReport() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EmbeddingRetrievalApp.run(new HashEmbedder(), Path.of("models"), new PrintStream(baos, true));
        String out = baos.toString(StandardCharsets.UTF_8);

        assertThat(out).contains("=== POC 20: real semantic EmbeddingService ===");
        assertThat(out).contains("Precision as the library grows");
        assertThat(out).contains("Precision at full scale");
        assertThat(out).contains("Growth curve");
        assertThat(out).contains("Per-target top-1 at full scale (embedding)");
        assertThat(out).contains("Verdict");
        // all three methods appear as rows.
        assertThat(out).contains("lexical (word TF)");
        assertThat(out).contains("dense (feature hash)");
        assertThat(out).contains("embedding (MiniLM)");
    }

    @Test
    void isModelPresentRequiresBothTheOnnxAndTheTokenizer(@TempDir Path dir) throws Exception {
        assertThat(EmbeddingRetrievalApp.isModelPresent(dir)).isFalse();

        Files.createDirectories(dir.resolve("onnx"));
        Files.writeString(dir.resolve("onnx/model_quantized.onnx"), "stub");
        assertThat(EmbeddingRetrievalApp.isModelPresent(dir)).isFalse(); // tokenizer still missing

        Files.writeString(dir.resolve("tokenizer.json"), "{}");
        assertThat(EmbeddingRetrievalApp.isModelPresent(dir)).isTrue();
    }
}
