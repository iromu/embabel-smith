/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * One of the two candidates a build produces for the same description: the whole process
 * as a single agent, or decomposed into several. A candidate that failed validation was
 * never worth scoring, which is what its {@code valid} flag records.
 *
 * @param shape whether this candidate is one agent or several
 * @param name the name it publishes under
 * @param valid whether it cleared its validation gate
 * @param score its eval score, or {@code null} when it was never scored
 * @author Ivan Rodriguez
 */
public record Candidate(CandidateShape shape, String name, boolean valid, Double score) {

	/**
	 * The single-agent candidate, which reached scoring by definition of being scored.
	 * @param name the pack name
	 * @param score its eval score
	 * @return the candidate
	 */
	public static Candidate singleAgent(String name, double score) {
		return new Candidate(CandidateShape.SINGLE_AGENT, name, true, score);
	}

	/**
	 * The multi-agent candidate.
	 * @param name the process name
	 * @param valid whether the process validated (after the cycle repair)
	 * @param score its eval score, or {@code null} when it never got that far
	 * @return the candidate
	 */
	public static Candidate multiAgent(String name, boolean valid, Double score) {
		return new Candidate(CandidateShape.MULTI_AGENT, name, valid, score);
	}
}
