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
