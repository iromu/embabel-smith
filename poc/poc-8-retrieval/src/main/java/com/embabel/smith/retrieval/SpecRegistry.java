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

package com.embabel.smith.retrieval;

import java.util.Comparator;
import java.util.List;

/**
 * A minimal SpecRegistry: an in-memory index over {@link SpecEntry} metadata with two
 * operations that expose the retrieval problem doc 03 §2.1 and doc 02 caveat #1 describe.
 *
 * <ul>
 * <li>{@link #typeCompatibleCount} — the <em>shadowing factor</em>: how many specs the
 * type-based GOAP planner alone would have to consider for a task. When specs share
 * input/output types, this is large and type matching cannot disambiguate.</li>
 * <li>{@link #retrieve} — the semantic pre-filter: rank specs by relevance to the task
 * and return the top-k, so the planner only sees a narrow, on-topic candidate set.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public class SpecRegistry {

	private final List<SpecEntry> entries;

	public SpecRegistry(List<SpecEntry> entries) {
		this.entries = List.copyOf(entries);
	}

	public int size() {
		return this.entries.size();
	}

	/**
	 * The shadowing factor: the number of specs whose types match a task needing
	 * {@code inputTypes -> outputType}. This is the candidate set a pure type-based
	 * (GOAP) router would face before any semantic narrowing.
	 * @param inputTypes the input type names the task's action consumes.
	 * @param outputType the output type name the task's action produces.
	 * @return the number of type-compatible specs.
	 */
	public int typeCompatibleCount(List<String> inputTypes, String outputType) {
		return (int) this.entries.stream()
			.filter((e) -> e.inputTypes().containsAll(inputTypes) && e.outputType().equals(outputType))
			.count();
	}

	/**
	 * The semantic pre-filter: rank specs by lexical similarity between the task and each
	 * spec's {@code name + description}, returning the top {@code k}.
	 * @param query the task query to retrieve specs for.
	 * @param k the maximum number of specs to return.
	 * @return the top-k specs ranked by lexical similarity to the query.
	 */
	public List<SpecEntry> retrieve(String query, int k) {
		List<String> queryTokens = LexicalSimilarity.tokenize(query);
		return this.entries.stream()
			.map((entry) -> new Scored(entry, score(queryTokens, entry)))
			.sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
			.limit(Math.max(0, k))
			.map(Scored::entry)
			.toList();
	}

	private double score(List<String> queryTokens, SpecEntry entry) {
		String text = entry.name() + " " + entry.description();
		return LexicalSimilarity.cosine(LexicalSimilarity.tf(queryTokens),
				LexicalSimilarity.tf(LexicalSimilarity.tokenize(text)));
	}

	private record Scored(SpecEntry entry, double score) {
	}

}
