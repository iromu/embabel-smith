/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cuts the discovery answer into the document set the prompt asked for.
 * {@code business_process_discovery.jinja} requests seven named {@code .md} deliverables
 * and the model answers with one markdown document that carries a heading per
 * deliverable, so the answer has to be split on those headings to become a blueprint
 * directory.
 *
 * <p>
 * The headings are matched loosely and deliberately: a model writes
 * {@code ## BUSINESS_PROCESSES.md}, {@code ## Business Processes} and
 * {@code ## 3. Business processes} to mean the same thing, so a heading is compared after
 * stripping everything but letters and digits — which also makes hyphen, underscore and
 * space interchangeable. "and" is dropped from the comparison as well, because "States
 * &amp; Transitions" is a normal way to write STATES_AND_TRANSITIONS.md.
 *
 * <p>
 * A deliverable the answer never mentioned is absent from the result rather than invented
 * as an empty file; {@link BlueprintGenerator} reports the gap, because a blueprint that
 * quietly shipped five of seven documents would read as complete.
 *
 * @author Ivan Rodriguez
 */
public final class BlueprintSplitter {

	/**
	 * The deliverables the discovery prompt asks for, in the order a reader meets them.
	 */
	public static final List<String> DELIVERABLES = List.of("BUSINESS_OVERVIEW.md", "ACTORS_AND_ROLES.md",
			"BUSINESS_PROCESSES.md", "WORKFLOWS.md", "BUSINESS_RULES.md", "STATES_AND_TRANSITIONS.md",
			"EXCEPTIONS_AND_EDGE_CASES.md");

	/**
	 * Splits a discovery answer into documents keyed by deliverable filename.
	 * @param answer the model's answer, one markdown text carrying a heading per
	 * deliverable
	 * @return the documents found, in {@link #DELIVERABLES} order, empty when the answer
	 * named none of them
	 */
	public Map<String, String> split(String answer) {
		if (answer == null || answer.isBlank()) {
			return Map.of();
		}
		List<String> lines = answer.lines().toList();
		Map<String, Integer> markers = markers(lines);
		if (markers.isEmpty()) {
			return Map.of();
		}
		List<Integer> positions = markers.values().stream().sorted().toList();
		Map<String, String> byHeading = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> marker : markers.entrySet()) {
			int from = marker.getValue();
			int to = positions.stream().filter((position) -> position > from).findFirst().orElse(lines.size());
			byHeading.put(marker.getKey(), String.join("\n", lines.subList(from, to)).strip() + "\n");
		}
		Map<String, String> documents = new LinkedHashMap<>();
		for (String deliverable : DELIVERABLES) {
			if (byHeading.containsKey(deliverable)) {
				documents.put(deliverable, byHeading.get(deliverable));
			}
		}
		return documents;
	}

	/**
	 * The line each deliverable's document starts on, sorted by line, with each line
	 * claimed by at most one deliverable. Earlier deliverables claim first, so a heading
	 * that could name two of them goes to the one a reader meets first.
	 * @param lines the answer split into lines
	 * @return the claimed heading lines keyed by deliverable
	 */
	private Map<String, Integer> markers(List<String> lines) {
		Map<String, Integer> claimed = new LinkedHashMap<>();
		for (String deliverable : DELIVERABLES) {
			int line = findMarker(lines, deliverable, claimed.values());
			if (line >= 0) {
				claimed.put(deliverable, line);
			}
		}
		Map<String, Integer> byHeading = new LinkedHashMap<>();
		claimed.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByValue())
			.forEachOrdered((entry) -> byHeading.put(entry.getKey(), entry.getValue()));
		return byHeading;
	}

	/**
	 * The heading line that begins this deliverable's document, chosen from three tiers
	 * of match and skipping lines another deliverable has already claimed. A heading that
	 * spells the deliverable out — {@code ## WORKFLOWS.md}, or {@code ## Workflows}
	 * exactly — beats one that merely starts with the same word, so a section titled
	 * "Workflow steps" inside the overview cannot steal WORKFLOWS.md away from the
	 * heading that begins it; a heading that only mentions the word is the last resort.
	 * @param lines the answer split into lines
	 * @param deliverable the document to find the heading for
	 * @param taken the lines already claimed by another deliverable
	 * @return the zero-based line the document starts on, or -1 when the answer carries
	 * no heading for it
	 */
	private static int findMarker(List<String> lines, String deliverable, Collection<Integer> taken) {
		List<String> keys = keysOf(deliverable);
		int spelledOut = -1;
		int prefix = -1;
		int mention = -1;
		boolean fenced = false;
		for (int line = 0; line < lines.size(); line++) {
			String text = lines.get(line);
			if (text.stripLeading().startsWith("```")) {
				fenced = !fenced;
				continue;
			}
			if (fenced || !text.stripLeading().startsWith("#") || taken.contains(line)) {
				continue;
			}
			String heading = normalize(text.replaceFirst("^#+\\s*", ""));
			if (keys.stream().anyMatch((key) -> heading.equals(key) || heading.startsWith(key + "md"))) {
				spelledOut = first(spelledOut, line);
			}
			else if (keys.stream().anyMatch(heading::startsWith)) {
				prefix = first(prefix, line);
			}
			else if (keys.stream().anyMatch(heading::contains)) {
				mention = first(mention, line);
			}
		}
		return first(spelledOut, first(prefix, mention));
	}

	private static int first(int chosen, int candidate) {
		return (chosen >= 0) ? chosen : candidate;
	}

	/**
	 * The normalized forms a heading may take for one deliverable: with and without the
	 * extension, and with "and" dropped for the ampersand spelling.
	 * @param deliverable the document to build the heading forms for
	 * @return the normalized forms a heading may match
	 */
	private static List<String> keysOf(String deliverable) {
		String stem = normalize(deliverable.replace(".md", ""));
		return List.of(stem, stem.replace("and", ""));
	}

	private static String normalize(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

}
