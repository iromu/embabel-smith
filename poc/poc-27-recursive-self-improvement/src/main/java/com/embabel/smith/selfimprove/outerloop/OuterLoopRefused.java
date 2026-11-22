/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

/**
 * A pass the launcher stopped rather than took, carrying the reason it stopped for.
 *
 * <p>
 * These refusals are findings, not crashes. A run whose arithmetic does not fit the
 * wall-clock ceiling, and a run that caught a candidate answering two different questions
 * identically, have both produced the thing worth knowing, and the run owes the reader
 * the reason rather than a stack trace. The type exists so {@code OuterLoopProcess.main}
 * can put that reason on the same {@code REFUSED} line it uses for its artifact refusals,
 * and so a test can assert on the reason instead of on an exit code.
 *
 * @author Ivan Rodriguez
 */
public final class OuterLoopRefused extends RuntimeException {

	/**
	 * A pass that was stopped rather than taken.
	 * @param reason what the launcher observed, in the words it prints
	 */
	public OuterLoopRefused(String reason) {
		super(reason);
	}

}
