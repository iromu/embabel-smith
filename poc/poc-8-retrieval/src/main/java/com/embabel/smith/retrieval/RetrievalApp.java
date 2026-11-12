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

import java.util.List;

/**
 * The retrieval / spec-shadowing report (POC 8). Offline and deterministic: it builds a
 * same-typed spec library, shows the shadowing factor a type-based router faces, the
 * pre-filter's top hits, precision@1 on a benchmark, and the growth curve as distractors
 * are added.
 *
 * <p>
 * Run: {@code mvn -pl poc/poc-8-retrieval exec:java}
 *
 * @author Ivan Rodriguez
 */
public final class RetrievalApp {

	private RetrievalApp() {
	}

	public static void main(String[] args) {
		RetrievalRunner runner = new RetrievalRunner();

		List<SpecEntry> base = SampleSpecLibrary.base();
		SpecRegistry registry = new SpecRegistry(base);
		List<String> taskInputs = List.of(SampleSpecLibrary.USER_INPUT);
		String taskOutput = SampleSpecLibrary.BACKGROUND_MESSAGE;

		System.out.println("=== POC 8: spec retrieval at scale / spec shadowing ===");
		System.out.println("Library size:              " + registry.size() + " specs (all "
				+ SampleSpecLibrary.USER_INPUT + " -> " + SampleSpecLibrary.BACKGROUND_MESSAGE + ")");
		System.out.println("Shadowing factor (type-):  " + runner.shadowingFactor(registry, taskInputs, taskOutput)
				+ " specs are type-compatible, so a pure type-based (GOAP) router cannot disambiguate");

		System.out.println("\n-- Top-3 pre-filter hits for benchmark queries --");
		for (SampleSpecLibrary.QueryTarget q : SampleSpecLibrary.benchmarkQueries()) {
			List<SpecEntry> top = registry.retrieve(q.query(), 3);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < top.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(top.get(i).id()).append((i == 0) ? " (top)" : "");
			}
			boolean hit = !top.isEmpty() && top.get(0).id().equals(q.targetId());
			System.out.printf("  %-52s -> %s   [%s]%n", q.query(), sb, hit ? "correct" : "WRONG");
		}

		double p1 = runner.precisionAt1(registry, SampleSpecLibrary.benchmarkQueries());
		System.out.printf("%nPrecision@1 (benchmark): %.1f%%%n", p1 * 100);

		System.out.println("\n-- Growth curve: 'coffee' target as same-verb explain-X distractors are added --");
		List<SpecEntry> distractors = SampleSpecLibrary.withExplainDistractors()
			.subList(base.size(), SampleSpecLibrary.withExplainDistractors().size());
		for (RetrievalRunner.GrowthPoint point : runner.growthCurve(base, distractors,
				"explain how a coffee machine works in plain language", "coffee-explainer")) {
			System.out.printf("  library=%-3d  coffee-explainer top-1: %s%n", point.librarySize(),
					point.targetTop1() ? "yes" : "NO");
		}
	}

}
