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

package com.embabel.smith.retrieval.scale;

import com.embabel.smith.retrieval.SpecEntry;

/**
 * Ranks a spec against a task query for the retrieval pre-filter. A retriever is defined
 * by its scorer, so the same {@link ScaleSpecRegistry} can be driven by the lexical
 * baseline (POC 8) or a denser semantic scorer — the comparison POC 12 makes at scale.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface SpecScorer {

	/**
	 * A relevance score for {@code entry} against {@code query}; higher is more relevant.
	 * @param query the task query.
	 * @param entry the spec to score.
	 * @return the relevance score; higher is more relevant.
	 */
	double score(String query, SpecEntry entry);

}
