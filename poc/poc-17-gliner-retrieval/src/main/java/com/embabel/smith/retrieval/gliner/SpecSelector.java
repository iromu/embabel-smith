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

package com.embabel.smith.retrieval.gliner;

import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;

/**
 * Selects the single most relevant spec from a same-typed candidate set for a task query
 * — the "selector → top-1 over a candidate set" seam POC 17 introduces so its GLiNER2
 * transformer classifier is directly comparable to POC 8's lexical and POC 12's dense
 * scorers on identical candidate sets.
 *
 * <p>
 * A selector is a <em>batch</em> decision over the whole candidate set, not a per-entry
 * scorer: the transformer scores every candidate at once and returns the top hit, which
 * is why this seam (rather than POC 12's {@code SpecScorer}) is the common denominator.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface SpecSelector {

	/**
	 * Select the top-1 spec for {@code query} from {@code candidates}.
	 * @param query the task query text
	 * @param candidates the same-typed candidate set to choose from
	 * @return the id of the top-ranked candidate, or {@code null} when {@code candidates}
	 * is empty
	 */
	String select(String query, List<SpecEntry> candidates);

}
