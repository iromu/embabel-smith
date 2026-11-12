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
import java.util.stream.Stream;

import com.embabel.smith.common.samples.SampleAgentSpecs;

/**
 * A same-typed spec library for the shadowing scenario: every spec consumes
 * {@code UserInput} and produces a {@code BackgroundMessage}, so a type-based (GOAP)
 * router cannot tell them apart — only their descriptions do. Several specs share the
 * "explain X" verb on purpose, to show the pre-filter discriminates within a same-typed,
 * same-verb family.
 *
 * @author Ivan Rodriguez
 */
public final class SampleSpecLibrary {

	private SampleSpecLibrary() {
	}

	/** The input type name shared by every spec in the library. */
	public static final String USER_INPUT = SampleAgentSpecs.USER_INPUT;

	/** The output type name shared by every spec in the library. */
	public static final String BACKGROUND_MESSAGE = SampleAgentSpecs.BACKGROUND_MESSAGE;

	/**
	 * The base library (14 same-typed specs).
	 * @return the base library of specs.
	 */
	public static List<SpecEntry> base() {
		return List.of(
				entry("coffee-explainer",
						"Explains how a coffee machine works in plain language for a general audience"),
				entry("rocket-explainer", "Explains how a rocket engine works, covering thrust and staging"),
				entry("engine-explainer", "Explains how a car engine works, from intake to exhaust"),
				entry("contract-summarizer", "Summarizes a legal contract into plain-language key obligations"),
				entry("fr-translator", "Translates a French passage into idiomatic English"),
				entry("haiku-writer", "Writes a short haiku about a given subject"),
				entry("fibonacci-computer", "Computes the first N Fibonacci numbers and returns them"),
				entry("trip-planner", "Plans a three-day travel itinerary for a destination"),
				entry("java-debugger", "Diagnoses a Java stack trace and suggests the likely fix"),
				entry("sql-generator", "Generates a SQL query from a natural-language request"),
				entry("cover-letter-writer", "Writes a tailored cover letter for a job application"),
				entry("temp-converter", "Converts a temperature between Celsius and Fahrenheit"),
				entry("slogan-drafter", "Drafts a punchy marketing slogan for a product"),
				entry("checkbook-balancer", "Balances a checkbook from a list of transactions"));
	}

	/**
	 * The base library plus extra same-verb "explain X" distractors, to stress the
	 * pre-filter.
	 * @return the base library augmented with the distractor specs.
	 */
	public static List<SpecEntry> withExplainDistractors() {
		List<SpecEntry> extra = List.of(
				entry("toaster-explainer", "Explains how a toaster works, including the heating elements"),
				entry("tap-explainer", "Explains how a household tap and its valve work"),
				entry("kettle-explainer", "Explains how an electric kettle works and its safety cut-off"),
				entry("lamp-explainer", "Explains how an LED lamp and its driver work"));
		return Stream.concat(base().stream(), extra.stream()).toList();
	}

	/**
	 * Benchmark queries: each maps to a distinct spec in {@link #base()}.
	 * @return the benchmark query-to-target pairs.
	 */
	public static List<QueryTarget> benchmarkQueries() {
		return List.of(new QueryTarget("explain how a coffee machine works in plain language", "coffee-explainer"),
				new QueryTarget("summarize this legal contract for me", "contract-summarizer"),
				new QueryTarget("translate this French passage into English", "fr-translator"),
				new QueryTarget("write a short haiku about autumn", "haiku-writer"),
				new QueryTarget("compute the first ten fibonacci numbers", "fibonacci-computer"),
				new QueryTarget("plan a three day trip to Lisbon", "trip-planner"));
	}

	private static SpecEntry entry(String id, String description) {
		return new SpecEntry(id, id, description, List.of(USER_INPUT), BACKGROUND_MESSAGE);
	}

	/**
	 * A realistic task query paired with the spec that should satisfy it — the accuracy
	 * benchmark.
	 *
	 * @param query the task query text.
	 * @param targetId the id of the spec that should satisfy the query.
	 */
	public record QueryTarget(String query, String targetId) {
	}

}
