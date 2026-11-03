package com.embabel.smith.retrieval.embedding;

/**
 * Produces a dense, L2-normalised embedding for a piece of text. The real backend is a local ONNX
 * sentence-encoder ({@link MiniLmEmbedder}); offline tests substitute a deterministic stub so the
 * retrieval logic is testable without the model.
 */
@FunctionalInterface
public interface Embedder {

    /** An L2-normalised embedding for {@code text} (higher-dimensional dense vector). */
    float[] embed(String text);
}
