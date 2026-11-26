/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

/**
 * The cap every builder tool puts on what it hands back, and the marker it appends when
 * the cap bit.
 *
 * <p>
 * <b>Why a tool caps itself.</b> Nothing on the builder's path caps a tool result today:
 * {@code eval.BuilderPackTools} renders findings with {@code String.join} and returns
 * {@code Tool.Result.text} directly. That is harmless while the only tool reports a
 * handful of validation findings, and not harmless the moment a tool can read a corpus —
 * a {@code blueprint} tool with no cap is a context blowout with a nice name, and the
 * tool loop runs up to 20 iterations inside one step. POC 26's script engine is this
 * repository's model of doing it right ({@code DEFAULT_MAX_OUTPUT_CHARS} plus a
 * {@code CappedOutputStream} that refuses the rest), and the corpus reader already sets
 * the wording precedent: {@code blueprint/BlueprintLoader} appends
 * {@code [corpus truncated at 200000 characters; …]} rather than quietly returning less.
 *
 * <p>
 * The number is one constant for every tool rather than a per-tool knob: no build needs a
 * different answer and a knob nobody asked for is a knob nobody tests.
 *
 * @author Ivan Rodriguez
 */
public final class ToolOutput {

	/**
	 * The most characters any builder tool returns. Chosen to be far below the 200,000-
	 * character corpus the loader will happily hand over, and large enough that a
	 * document listing or a scan report is never the thing that got cut.
	 */
	public static final int MAX_CHARS = 20_000;

	private ToolOutput() {
	}

	/**
	 * The text a tool returns: {@code body}, cut at {@link #MAX_CHARS} with a marker
	 * naming what was left out.
	 * @param body the rendered result, possibly longer than the cap
	 * @return the capped result
	 */
	public static String cap(String body) {
		if (body.length() <= MAX_CHARS) {
			return body;
		}
		return body.substring(0, MAX_CHARS) + System.lineSeparator() + "[truncated at " + MAX_CHARS + " characters; "
				+ (body.length() - MAX_CHARS) + " characters were not included]";
	}

}
