/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * The shape of a build's candidate: the whole process as one agent, or decomposed into
 * several. The build generates one of each and recommends between them.
 *
 * @author Ivan Rodriguez
 */
public enum CandidateShape {

	/** One agent spec covering the whole process. */
	SINGLE_AGENT("single-agent spec"),

	/** Several agent specs composed into a process. */
	MULTI_AGENT("multi-agent process");

	private final String label;

	CandidateShape(String label) {
		this.label = label;
	}

	/**
	 * The name of this shape as it reads in a build report.
	 * @return the human-readable shape label
	 */
	public String label() {
		return this.label;
	}

}
