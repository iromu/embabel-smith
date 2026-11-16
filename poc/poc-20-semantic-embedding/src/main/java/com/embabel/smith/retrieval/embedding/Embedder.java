/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.retrieval.embedding;

/**
 * Produces a dense, L2-normalised embedding for a piece of text. The real backend is a
 * local ONNX sentence-encoder ({@link MiniLmEmbedder}); offline tests substitute a
 * deterministic stub so the retrieval logic is testable without the model.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface Embedder {

	/**
	 * An L2-normalised embedding for {@code text} (higher-dimensional dense vector).
	 * @param text the text to embed.
	 * @return an L2-normalised dense vector for the text.
	 */
	float[] embed(String text);

}
