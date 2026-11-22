/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.embabel.smith.common.model.AgentSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A candidate the referee scores: the pack the gate judges, plus the directory the
 * candidate wrote its own proposed evaluation tasks into, if it wrote any.
 *
 * <p>
 * A candidate is allowed to <em>emit</em> tasks — it is not allowed for them to count.
 * The referee scores the frozen set and nothing else, and records what it dropped; see
 * {@code evidence/MUTABILITY.md}, fixed artifact {@code benchmark-task-set}.
 *
 * @param name the variant name, used to label the report
 * @param spec the pack handed to the gate
 * @param proposedTaskDir the directory holding task files the candidate emitted, or null
 * when it emitted none
 * @author Ivan Rodriguez
 */
public record Candidate(String name, AgentSpec spec, Path proposedTaskDir) {

	private static final ObjectMapper LENIENT_JSON = new ObjectMapper();

	/**
	 * The identifiers of the task files this candidate emitted. They are read for their
	 * identifier only and deliberately not validated: the referee's answer to a proposed
	 * task is to exclude it, and a malformed proposal is excluded the same as a
	 * well-formed one rather than becoming a scoring failure or a crash. When a proposal
	 * cannot be read at all, the file name is recorded so the exclusion still names
	 * something.
	 * @return the excluded task identifiers, in file-name order; empty when the candidate
	 * emitted no task directory
	 */
	public List<String> proposedTaskIds() {
		if (this.proposedTaskDir == null || !Files.isDirectory(this.proposedTaskDir)) {
			return List.of();
		}
		List<Path> files;
		try (var paths = Files.list(this.proposedTaskDir)) {
			files = paths.filter((path) -> path.getFileName().toString().endsWith(".json"))
				.sorted(Comparator.comparing((Path path) -> path.getFileName().toString()))
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot list the tasks proposed by candidate " + this.name, ex);
		}
		List<String> ids = new ArrayList<>();
		for (Path file : files) {
			ids.add(idOf(file));
		}
		return List.copyOf(ids);
	}

	private String idOf(Path file) {
		try {
			var id = LENIENT_JSON.readTree(file.toFile()).get("id");
			return (id == null || id.isNull()) ? file.getFileName().toString() : id.asText();
		}
		catch (IOException | RuntimeException ex) {
			return file.getFileName().toString();
		}
	}

}
