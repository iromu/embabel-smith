/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.retrieval.gliner;

import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.SemanticSimilarity;

/**
 * The POC 12 dense baseline exposed as a {@link SpecSelector}: argmax over
 * feature-hashing char/word n-gram cosine similarity. Reused verbatim from POC 12 (via
 * {@link SemanticSimilarity}) as the offline stand-in for a real embedding backend.
 *
 * @author Ivan Rodriguez
 */
public final class DenseSelector implements SpecSelector {

	@Override
	public String select(String query, List<SpecEntry> candidates) {
		if (candidates.isEmpty()) {
			return null;
		}
		double[] queryVec = SemanticSimilarity.embed(query);
		return candidates.stream()
			.max((a, b) -> Double.compare(score(queryVec, a), score(queryVec, b)))
			.map(SpecEntry::id)
			.orElse(null);
	}

	private static double score(double[] queryVec, SpecEntry entry) {
		String text = entry.name() + " " + entry.description();
		return SemanticSimilarity.cosine(queryVec, SemanticSimilarity.embed(text));
	}

}
