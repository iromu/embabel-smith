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

import java.util.ArrayList;
import java.util.List;

/**
 * The retrieval / shadowing measurement (POC 8). Pure and offline: it quantifies the spec
 * shadowing problem and shows the semantic pre-filter resolves it.
 *
 * <ul>
 * <li>{@link #shadowingFactor} — how many same-typed specs a type-based router must
 * consider.</li>
 * <li>{@link #precisionAt1} — how often the pre-filter returns the right spec first.</li>
 * <li>{@link #growthCurve} — precision@1 for a target query as same-typed distractors are
 * added.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public class RetrievalRunner {

	/**
	 * The shadowing factor for a task needing {@code inputTypes -> outputType}: the count
	 * of specs a pure type-based (GOAP) router would treat as candidates before any
	 * semantic narrowing.
	 * @param registry the spec registry to count over.
	 * @param inputTypes the input type names the task's action consumes.
	 * @param outputType the output type name the task's action produces.
	 * @return the number of type-compatible specs.
	 */
	public int shadowingFactor(SpecRegistry registry, List<String> inputTypes, String outputType) {
		return registry.typeCompatibleCount(inputTypes, outputType);
	}

	/**
	 * precision@1 across a set of benchmark queries: the fraction for which the
	 * pre-filter returns the expected spec as the top hit.
	 * @param registry the spec registry to query.
	 * @param queries the benchmark queries to measure.
	 * @return the fraction of queries for which the expected spec is the top hit.
	 */
	public double precisionAt1(SpecRegistry registry, List<SampleSpecLibrary.QueryTarget> queries) {
		if (queries.isEmpty()) {
			return 0.0;
		}
		int hits = 0;
		for (SampleSpecLibrary.QueryTarget q : queries) {
			List<SpecEntry> top = registry.retrieve(q.query(), 1);
			if (!top.isEmpty() && top.get(0).id().equals(q.targetId())) {
				hits++;
			}
		}
		return (double) hits / queries.size();
	}

	/**
	 * The growth curve for a single target query: start from {@code base}, add
	 * {@code distractors} one at a time, and record whether {@code targetId} is still
	 * retrieved first at each size. This is the "selection accuracy as the library grows"
	 * measurement from the POC brief.
	 * @param base the starting library of specs.
	 * @param distractors the same-typed distractors added one at a time.
	 * @param query the target query to retrieve.
	 * @param targetId the id of the spec that should stay top-1.
	 * @return the growth-curve points, one per library size.
	 */
	public List<GrowthPoint> growthCurve(List<SpecEntry> base, List<SpecEntry> distractors, String query,
			String targetId) {
		List<SpecEntry> current = new ArrayList<>(base);
		List<GrowthPoint> points = new ArrayList<>();
		points.add(measure(current, query, targetId));
		for (SpecEntry d : distractors) {
			current.add(d);
			points.add(measure(current, query, targetId));
		}
		return points;
	}

	private GrowthPoint measure(List<SpecEntry> library, String query, String targetId) {
		SpecRegistry registry = new SpecRegistry(library);
		List<SpecEntry> top = registry.retrieve(query, 1);
		boolean hit = !top.isEmpty() && top.get(0).id().equals(targetId);
		return new GrowthPoint(library.size(), hit);
	}

	/**
	 * A point on the growth curve: library size and whether the target was retrieved
	 * first.
	 *
	 * @param librarySize the library size at this point.
	 * @param targetTop1 whether the target spec was the top-1 hit.
	 */
	public record GrowthPoint(int librarySize, boolean targetTop1) {
	}

}
