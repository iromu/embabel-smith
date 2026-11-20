/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What greedy selection would have chosen over the very same generations the archive
 * selection ran on.
 *
 * <p>
 * The archive's reason for keeping losers is that a losing variant can lead somewhere a
 * champion cannot. That is a claim about selection, and the only honest way to test it is
 * to run the other selection rule over identical data — same variants, same scores, same
 * parents — and report both curves. Reporting only the archive's own curve would be
 * grading the archive by its own choice of what to remember.
 *
 * <p>
 * Greedy here means: extend only the current champion. A variant descended from anything
 * else is invisible to it, so when the archive's best variant turns out to be the child
 * of a loser, greedy cannot reach it and the two curves part. Where they part is reported
 * rather than smoothed over.
 *
 * @author Ivan Rodriguez
 */
public final class SelectionCurves {

	private SelectionCurves() {
	}

	/**
	 * Replays both selection rules over the scored variants, generation by generation.
	 * @param records the variants the run scored, in scoring order
	 * @return the two curves and the generation they first disagree at.
	 */
	public static Curves over(List<VariantRecord> records) {
		Map<Integer, List<VariantRecord>> byGeneration = new LinkedHashMap<>();
		for (VariantRecord record : records) {
			byGeneration.computeIfAbsent(record.generation(), (key) -> new ArrayList<>()).add(record);
		}
		List<Point> archive = new ArrayList<>();
		List<Point> greedy = new ArrayList<>();
		VariantRecord archiveBest = null;
		VariantRecord champion = null;
		for (Map.Entry<Integer, List<VariantRecord>> generation : byGeneration.entrySet()) {
			for (VariantRecord record : generation.getValue()) {
				if (archiveBest == null || record.frozenScore() > archiveBest.frozenScore()) {
					archiveBest = record;
				}
			}
			VariantRecord extendable = null;
			for (VariantRecord record : generation.getValue()) {
				boolean reachable = (champion == null) || champion.variantId().equals(record.parentVariantId());
				if (reachable && (extendable == null || record.frozenScore() > extendable.frozenScore())) {
					extendable = record;
				}
			}
			if (extendable != null && (champion == null || extendable.frozenScore() > champion.frozenScore())) {
				champion = extendable;
			}
			archive.add(new Point(generation.getKey(), archiveBest.variantId(), archiveBest.frozenScore()));
			greedy.add(new Point(generation.getKey(), champion.variantId(), champion.frozenScore()));
		}
		return new Curves(List.copyOf(archive), List.copyOf(greedy), divergedAt(archive, greedy));
	}

	private static Integer divergedAt(List<Point> archive, List<Point> greedy) {
		for (int index = 0; index < archive.size(); index++) {
			if (Double.compare(archive.get(index).score(), greedy.get(index).score()) != 0) {
				return archive.get(index).generation();
			}
		}
		return null;
	}

	/**
	 * One generation's pick.
	 *
	 * @param generation the generation the pick belongs to
	 * @param variantId the variant that rule was standing on afterwards
	 * @param score its frozen score
	 * @author Ivan Rodriguez
	 */
	public record Point(int generation, String variantId, double score) {
	}

	/**
	 * The two curves, same length, one point per generation.
	 *
	 * @param archive the best-so-far over everything retained, which is what the run
	 * itself selected from
	 * @param greedy the best reachable by extending only the previous champion
	 * @param divergedAtGeneration the first generation where the two disagree, or null
	 * when they never do
	 * @author Ivan Rodriguez
	 */
	public record Curves(List<Point> archive, List<Point> greedy, Integer divergedAtGeneration) {

		/**
		 * Both curves in words, for the run report.
		 * @return one line per curve plus where they part.
		 */
		public String describe() {
			String outcome = "archive: " + render(this.archive) + System.lineSeparator() + "greedy:  "
					+ render(this.greedy);
			if (this.divergedAtGeneration == null) {
				return outcome + System.lineSeparator() + "the two rules picked the same variant every generation"
						+ " of this run, so it does not separate them";
			}
			return outcome + System.lineSeparator() + "the two rules first disagree at generation "
					+ this.divergedAtGeneration;
		}

		private static String render(List<Point> curve) {
			StringBuilder out = new StringBuilder();
			for (Point point : curve) {
				out.append("g")
					.append(point.generation())
					.append('=')
					.append(String.format("%.3f", point.score()))
					.append('(')
					.append(point.variantId())
					.append(") ");
			}
			return out.toString().trim();
		}

	}

}
