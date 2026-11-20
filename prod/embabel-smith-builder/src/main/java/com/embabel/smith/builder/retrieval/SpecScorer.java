/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

/**
 * The scorer seam: a retriever is defined by its scorer, so a denser one (an embedding, a
 * GLiNER classifier, a small LLM) replaces the ranking without changing the ranker, the
 * endpoint, the response shape or the pane.
 *
 * <p>
 * Deliberately one method. There is no registry, no loader and no selector property: the
 * POC evidence says a denser text-similarity scorer does <em>not</em> cure the
 * near-duplicate shadowing collapse, so buying one is a latency decision, and the fix
 * that does work is the library curation in {@link RetrievalCuration}.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface SpecScorer {

	/**
	 * A relevance score for {@code entry} against {@code query}; higher is more relevant.
	 * @param query the task query.
	 * @param entry the pack entry to score.
	 * @return the relevance score; higher is more relevant.
	 */
	double score(String query, SpecEntry entry);

}
