/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

/**
 * The one scorer that ships: cosine over word term-frequency vectors of the entry's name
 * plus description. Exact whole-word overlap only — the lexical baseline the POC family
 * measured, reused verbatim so the product's numbers are comparable with the evidence.
 *
 * @author Ivan Rodriguez
 */
public final class LexicalScorer implements SpecScorer {

	@Override
	public double score(String query, SpecEntry entry) {
		String text = entry.name() + " " + entry.description();
		return LexicalSimilarity.cosine(LexicalSimilarity.tf(LexicalSimilarity.tokenize(query)),
				LexicalSimilarity.tf(LexicalSimilarity.tokenize(text)));
	}

}
