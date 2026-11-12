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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.ScaleSpecLibrary;

/**
 * The POC 17 shadowing benchmark (design D6). Drives each {@link SpecSelector} over the
 * six {@link ScaleSpecLibrary} targets in two candidate-set variants, computes
 * Precision@1 per method per variant, and prints a comparison table. The three methods
 * are scored on identical candidate sets, so the shadowing result is directly comparable
 * to POC 12.
 *
 * @author Ivan Rodriguez
 */
public final class ShadowingRunner {

	/** The two candidate-set variants (design D3). */
	public enum Variant {

		/**
		 * Canonical + every near-duplicate (16 total) — directly comparable to POC 12.
		 */
		MATCHING,
		/**
		 * Canonical + near-duplicates minus the near-exact duplicate (15 total) — the
		 * meaningful signal.
		 */
		STRICT

	}

	private final List<ScaleSpecLibrary.QueryTarget> targets;

	public ShadowingRunner() {
		this(ScaleSpecLibrary.benchmark());
	}

	public ShadowingRunner(List<ScaleSpecLibrary.QueryTarget> targets) {
		this.targets = List.copyOf(targets);
	}

	/**
	 * The candidate set for a target under a variant.
	 * <ul>
	 * <li>{@link Variant#MATCHING} — the canonical spec + all of its
	 * near-duplicates.</li>
	 * <li>{@link Variant#STRICT} — the canonical spec + its near-duplicates, excluding
	 * the near-exact duplicate (the empty-tail {@code v0}).</li>
	 * </ul>
	 * @param targetId the benchmark target to build the candidate set for
	 * @param variant which candidate-set variant to use
	 * @return the candidate specs for the target under the variant
	 */
	public List<SpecEntry> candidatesFor(String targetId, Variant variant) {
		List<SpecEntry> dups = ScaleSpecLibrary.nearDuplicatesOf(targetId);
		if (variant == Variant.STRICT) {
			dups = dups.stream().filter((e) -> !e.id().equals(targetId + "-v0")).toList();
		}
		List<SpecEntry> candidates = new ArrayList<>();
		candidates.add(canonical(targetId));
		candidates.addAll(dups);
		return candidates;
	}

	/**
	 * Precision@1 for one method (selector) over one variant: the fraction of targets
	 * whose canonical spec is the selector's top hit.
	 * @param selector the method under test
	 * @param variant which candidate-set variant to score
	 * @return the fraction of targets whose canonical spec is the selector's top hit
	 */
	public double precisionAt1(SpecSelector selector, Variant variant) {
		if (this.targets.isEmpty()) {
			return 0.0;
		}
		int hits = 0;
		for (ScaleSpecLibrary.QueryTarget target : this.targets) {
			String top = selector.select(target.query(), candidatesFor(target.targetId(), variant));
			if (top != null && top.equals(target.targetId())) {
				hits++;
			}
		}
		return (double) hits / this.targets.size();
	}

	/**
	 * Print the three-method comparison table: Precision@1 per method (in {@code methods}
	 * order) per variant.
	 * @param methods the method name to selector map to score
	 */
	public void printComparisonTable(Map<String, SpecSelector> methods) {
		System.out.printf("  %-24s  %s  %s%n", "method", "matching@1", "strict@1");
		for (Map.Entry<String, SpecSelector> method : methods.entrySet()) {
			double matching = precisionAt1(method.getValue(), Variant.MATCHING);
			double strict = precisionAt1(method.getValue(), Variant.STRICT);
			System.out.printf("  %-24s  %5s  %5s%n", method.getKey(), pct(matching), pct(strict));
		}
	}

	private static SpecEntry canonical(String targetId) {
		return ScaleSpecLibrary.core()
			.stream()
			.filter((e) -> e.id().equals(targetId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no canonical spec for target " + targetId));
	}

	private static String pct(double value) {
		return String.format("%.0f%%", value * 100);
	}

}
