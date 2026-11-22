/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Every variant the run scored, losers included, each still pointing at the variant it
 * was derived from.
 *
 * <p>
 * This is the {@code generation-archive} surface of {@code evidence/MUTABILITY.md}: fixed
 * for a run, which here means append-only. Nothing removes or rewrites a retained record,
 * and a variant id already held is refused rather than overwritten — an archive a
 * generation can edit is a generation editing its own history.
 *
 * <p>
 * Keeping the losers is the whole design (design D6): a variant that scored below its
 * parent may still carry the one change that lets the next generation pass a task its
 * parent could not, and a selection that throws it away cannot find out. The reasoning is
 * Darwin-Gödel-Machine <em>selection</em>, stated as an analogy — this POC does not
 * reproduce DGM's result, and the comparison the run actually reports is against greedy
 * selection over the identical generations ({@link SelectionCurves}).
 *
 * @author Ivan Rodriguez
 */
public final class Archive {

	/** The file the run writes the archive to, one record per line. */
	public static final String RECORD_FILE = "archive.jsonl";

	private static final ObjectMapper JSON = new ObjectMapper();

	private final Map<String, VariantRecord> byVariant = new LinkedHashMap<>();

	/**
	 * Retains a scored variant.
	 * @param record the variant to keep
	 * @throws IllegalArgumentException if its id is already held, because that would be
	 * an edit rather than an append
	 */
	public void retain(VariantRecord record) {
		if (this.byVariant.containsKey(record.variantId())) {
			throw new IllegalArgumentException("the archive already holds " + record.variantId()
					+ "; it is append-only, so a" + " variant id cannot be replaced with a different score");
		}
		this.byVariant.put(record.variantId(), record);
	}

	/**
	 * The variants descended from one variant — including the ones that scored below it,
	 * which is the query a greedy selection never needs to answer.
	 * @param parentVariantId the parent to look up, or null for the roots that descends
	 * from no archived variant
	 * @return the children, in retention order.
	 */
	public List<VariantRecord> withParent(String parentVariantId) {
		List<VariantRecord> children = new ArrayList<>();
		for (VariantRecord record : this.byVariant.values()) {
			if (Objects.equals(parentVariantId, record.parentVariantId())) {
				children.add(record);
			}
		}
		return List.copyOf(children);
	}

	/**
	 * A retained variant by id.
	 * @param variantId the id to look up
	 * @return the record, if the archive holds it.
	 */
	public Optional<VariantRecord> byVariant(String variantId) {
		return Optional.ofNullable(this.byVariant.get(variantId));
	}

	/**
	 * The frozen score of a retained variant, which is what the next generation's
	 * improvement is paired against.
	 * @param variantId the variant to read
	 * @return its frozen score
	 * @throws IllegalArgumentException if the archive does not hold it, because a delta
	 * against a score that was never recorded is not a delta
	 */
	public double frozenScoreOf(String variantId) {
		VariantRecord record = this.byVariant.get(variantId);
		if (record == null) {
			throw new IllegalArgumentException(
					"no archived score for " + variantId + ", so nothing can be paired against it");
		}
		return record.frozenScore();
	}

	/**
	 * The best variant held, which is what archive selection offers as the next
	 * generation's parent. Ties go to the variant retained first, so a run is
	 * reproducible.
	 * @return the best record, or empty for an empty archive.
	 */
	public Optional<VariantRecord> best() {
		return this.byVariant.values()
			.stream()
			.max((left, right) -> Double.compare(left.frozenScore(), right.frozenScore()));
	}

	/**
	 * Every variant held.
	 * @return the records, in retention order.
	 */
	public List<VariantRecord> all() {
		return List.copyOf(this.byVariant.values());
	}

	/**
	 * How many variants are held.
	 * @return the count.
	 */
	public int size() {
		return this.byVariant.size();
	}

	/**
	 * Writes the archive, one JSON record per line, so the lineage survives the run that
	 * built it.
	 * @param file the file to write
	 * @return the file written.
	 */
	public Path write(Path file) {
		StringBuilder out = new StringBuilder();
		for (VariantRecord record : this.byVariant.values()) {
			try {
				out.append(JSON.writeValueAsString(record.asMap())).append('\n');
			}
			catch (IOException ex) {
				throw new UncheckedIOException("cannot serialise the archive record for " + record.variantId(), ex);
			}
		}
		try {
			Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot write the archive to " + file, ex);
		}
		return file;
	}

}
