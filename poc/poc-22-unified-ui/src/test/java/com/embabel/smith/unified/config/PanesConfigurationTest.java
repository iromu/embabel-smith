package com.embabel.smith.unified.config;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.embedding.Embedder;
import com.embabel.smith.unified.ScorerBinding;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the scorer-swap wiring (POC 22, task 7.3): the default binding is the offline lexical
 * scorer, and the opt-in embedding binding is named and ranks by the supplied embedder. The
 * embedder is a deterministic stub, so the seam is exercised offline (no ONNX model needed); the
 * model-loading path is only checked for its clear failure mode.
 */
class PanesConfigurationTest {

    private final PanesConfiguration config = new PanesConfiguration();

    @Test
    void theDefaultScorerBindingIsLexical() {
        assertThat(config.lexicalScorerBinding().name()).isEqualTo("lexical");
    }

    @Test
    void theEmbeddingBindingIsNamedAndRanksByTheEmbedder() {
        // A 2-d stub embedder: "extract"-flavoured texts point +x, everything else +y.
        Embedder stub = text -> {
            float[] v = new float[2];
            v[text.toLowerCase().contains("extract") ? 0 : 1] = 1f;
            return v;
        };
        ScorerBinding binding = config.embeddingScorerBinding(stub);
        assertThat(binding.name()).isEqualTo("embedding (MiniLM)");

        SpecEntry extract = entry("compose-extract", "extract the topic from the request");
        SpecEntry explain = entry("compose-explain", "write the explainer message");
        assertThat(binding.scorer().score("extract the topic", extract))
                .isGreaterThan(binding.scorer().score("extract the topic", explain));
    }

    @Test
    void aMissingModelDirFailsWithAClearError() {
        assertThatThrownBy(() -> config.miniLmEmbedder("no-such-model-dir"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("failed to load the MiniLM ONNX embedder");
    }

    private static SpecEntry entry(String name, String description) {
        return new SpecEntry(name, name, description, List.of("UserInput"), "BackgroundMessage");
    }
}
