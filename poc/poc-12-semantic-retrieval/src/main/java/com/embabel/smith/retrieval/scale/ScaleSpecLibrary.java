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

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.retrieval.SpecEntry;

/**
 * A same-typed spec library at the 200+ scale that doc 02 §3.4 and the "More Specs, Worse
 * Agents?" result (arXiv:2605.24050, up to −21% at 202 specs) describe.
 *
 * <p>
 * Every spec is {@code UserInput -> BackgroundMessage}, so a type-based (GOAP) router
 * cannot disambiguate — only the descriptions do. The library has two parts:
 *
 * <ul>
 * <li><b>core</b> — distinct-topic specs across six verb families (the "big folder").
 * Here the right spec is the unique best text match, so retrieval should be clean.</li>
 * <li><b>near-duplicates</b> — same-topic variants of each benchmark target (the
 * shadowing pressure). As these accumulate, the canonical target is no longer the
 * uniquely-best match, which is exactly the selection failure the literature attributes
 * to spec shadowing.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public final class ScaleSpecLibrary {

	private ScaleSpecLibrary() {
	}

	/** The task type every spec in the library takes. */
	public static final String USER_INPUT = SampleAgentSpecs.USER_INPUT;

	/** The goal type every spec in the library achieves. */
	public static final String BACKGROUND_MESSAGE = SampleAgentSpecs.BACKGROUND_MESSAGE;

	private static final List<Family> FAMILIES = List.of(new Family("Explains how a %s works", "coffee machine",
			", including the boiler and the portafilter", "explain how a coffee machine works",
			List.of("coffee machine", "espresso machine", "toaster", "electric kettle", "microwave oven", "blender",
					"dishwasher", "washing machine", "refrigerator", "air conditioner", "space heater", "water heater",
					"vacuum cleaner", "lawn mower", "robot vacuum", "inkjet printer", "laser scanner",
					"digital projector", "flat screen television", "fm radio", "bluetooth speaker", "stereo amplifier",
					"lithium battery", "solar panel", "wind turbine", "hydro generator", "car engine", "diesel engine",
					"jet engine", "rocket engine", "steam engine", "electric motor", "gearbox", "car transmission",
					"alternator", "starter motor", "disc brake system", "suspension spring", "power steering",
					"bicycle pump", "bicycle", "skateboard", "kayak", "surfboard", "snowboard", "canoe", "sailboat",
					"jet ski", "helicopter", "airplane", "quadcopter drone", "high speed train", "subway", "tram",
					"electric scooter")),
			new Family("Summarizes a %s into plain language", "legal contract",
					", with the key obligations and deadlines", "summarize this legal contract",
					List.of("legal contract", "sales agreement", "lease agreement", "employment contract",
							"non-disclosure agreement", "patent filing", "court ruling", "tax return", "medical report",
							"research paper", "news article", "book chapter", "meeting minutes", "project proposal",
							"investment memo", "insurance policy", "credit agreement", "service level agreement",
							"partnership deed", "merger document", "shareholder report", "audit report",
							"budget summary", "product spec", "user manual", "technical white paper",
							"grant application", "research abstract", "policy brief", "term sheet", "loan agreement",
							"settlement document")),
			new Family("Translates a %s passage into English", "French", ", preserving idiomatic phrasing",
					"translate this French passage",
					List.of("French", "Spanish", "German", "Italian", "Portuguese", "Dutch", "Swedish", "Norwegian",
							"Danish", "Finnish", "Polish", "Czech", "Hungarian", "Romanian", "Greek", "Turkish",
							"Russian", "Japanese", "Korean", "Mandarin", "Cantonese", "Arabic", "Hebrew", "Hindi",
							"Thai", "Vietnamese", "Indonesian", "Malay")),
			new Family("Writes a short %s about a subject", "haiku", ", in the traditional 5-7-5 structure",
					"write a short haiku",
					List.of("haiku", "limerick", "sonnet", "acrostic poem", "ballad", "epic verse", "free verse",
							"pantoum", "couplet", "quatrain", "septet", "villanelle", "sestina", "ode", "elegy",
							"epigram", "aphorism", "short story", "fable", "parable", "allegory", "anecdote",
							"monologue", "dialogue", "speech", "toast", "eulogy", "manifesto")),
			new Family("Computes %s and returns them", "Fibonacci numbers", ", with the sum of each term",
					"compute the fibonacci numbers",
					List.of("Fibonacci numbers", "prime numbers", "factorial values", "powers of two",
							"triangular numbers", "square numbers", "cube numbers", "Lucas numbers", "Catalan numbers",
							"Bell numbers", "binomial coefficients", "gcd pairs", "lcm pairs", "modular inverses",
							"matrix products", "eigenvalues", "vector magnitudes", "dot products", "cross products",
							"matrix inverses", "determinants", "regression coefficients", "standard deviations",
							"variances", "correlation coefficients", "z-scores", "p-values", "confidence intervals")),
			new Family("Plans a %s for a destination", "three-day trip", ", with lodging and meals included",
					"plan a three day trip",
					List.of("three-day trip", "weekend getaway", "city break", "road trip", "hiking itinerary",
							"beach holiday", "ski weekend", "museum tour", "food tour", "wine tasting route",
							"photography walk", "cycling tour", "camping trip", "island hop", "river cruise", "safari",
							"backpacking route", "rail tour", "roadshow", "festival itinerary")));

	/**
	 * Same-topic variant tails: appended to a target's base description to make
	 * near-duplicates.
	 */
	private static final List<String> NEAR_DUP_TAILS = List.of("", " for a child", " for an expert", " step by step",
			" with examples", " in one paragraph", " in bullet points", " with diagrams", " in under a minute",
			" for a general audience", " with analogies", " in simple terms", " as a quick guide", " for a blog post",
			" for a presentation");

	private static final int NEAR_DUPS_PER_TARGET = NEAR_DUP_TAILS.size();

	/**
	 * The core library: every distinct-topic spec across all families (no
	 * near-duplicates).
	 * @return the core library specs.
	 */
	public static List<SpecEntry> core() {
		List<SpecEntry> all = new ArrayList<>();
		for (Family f : FAMILIES) {
			for (String topic : f.topics()) {
				String id = slug(topic);
				String desc = topic.equals(f.targetTopic()) ? f.describe(topic) + f.targetDetail() : f.describe(topic);
				all.add(entry(id, desc));
			}
		}
		return all;
	}

	/**
	 * The shadowing pressure: {@link #NEAR_DUPS_PER_TARGET} same-topic variants of the
	 * spec with id {@code targetId}. Each is the target's base description (no
	 * distinguishing detail) plus a variant tail — so the first variant is a near-exact
	 * match to the benchmark query.
	 * @param targetId the id of the canonical spec to duplicate.
	 * @return the same-topic near-duplicate variants of that spec.
	 */
	public static List<SpecEntry> nearDuplicatesOf(String targetId) {
		Family f = familyForTarget(targetId);
		String base = f.describe(f.targetTopic());
		List<SpecEntry> dups = new ArrayList<>();
		for (int i = 0; i < NEAR_DUPS_PER_TARGET; i++) {
			dups.add(entry(targetId + "-v" + i, base + NEAR_DUP_TAILS.get(i)));
		}
		return dups;
	}

	/**
	 * The ordered near-duplicates to add after {@link #core()} for the growth curve,
	 * interleaved across the six targets so each target's shadowing pressure builds up as
	 * the library grows.
	 * @return the near-duplicates in the order they should be added.
	 */
	public static List<SpecEntry> growthSequence() {
		List<List<SpecEntry>> perTarget = benchmark().stream().map((bt) -> nearDuplicatesOf(bt.targetId())).toList();
		List<SpecEntry> sequence = new ArrayList<>();
		for (int i = 0; i < NEAR_DUPS_PER_TARGET; i++) {
			for (List<SpecEntry> per : perTarget) {
				sequence.add(per.get(i));
			}
		}
		return sequence;
	}

	/**
	 * The full library: core plus every near-duplicate (the 200+ same-typed "big
	 * folder").
	 * @return the full library specs.
	 */
	public static List<SpecEntry> full() {
		List<SpecEntry> all = new ArrayList<>(core());
		for (QueryTarget bt : benchmark()) {
			all.addAll(nearDuplicatesOf(bt.targetId()));
		}
		return all;
	}

	/**
	 * The six benchmark queries, one per family, each mapping to that family's canonical
	 * target.
	 * @return the benchmark queries, one per family.
	 */
	public static List<QueryTarget> benchmark() {
		return FAMILIES.stream().map((f) -> new QueryTarget(f.query(), slug(f.targetTopic()))).toList();
	}

	private static Family familyForTarget(String targetId) {
		for (Family f : FAMILIES) {
			if (slug(f.targetTopic()).equals(targetId)) {
				return f;
			}
		}
		throw new IllegalArgumentException("no family targets " + targetId);
	}

	private static SpecEntry entry(String id, String description) {
		return new SpecEntry(id, id, description, List.of(USER_INPUT), BACKGROUND_MESSAGE);
	}

	private static String slug(String topic) {
		return topic.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}

	/**
	 * A benchmark target: the task query and the id of the spec that should satisfy it.
	 * @param query the task query.
	 * @param targetId the id of the canonical spec that should satisfy the query.
	 */
	public record QueryTarget(String query, String targetId) {
	}

	/**
	 * One verb family: a description template, its distinct topics, and the benchmark
	 * target.
	 */
	private record Family(String template, String targetTopic, String targetDetail, String query, List<String> topics) {
		String describe(String topic) {
			return this.template.formatted(topic);
		}
	}

}
