/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * Wraps material the builder has no reason to trust — a blueprint it was handed, another
 * model's answer, a repository's source — in an explicit tag, so the boundary between the
 * builder's instructions and the material they run on is present in the prompt bytes. A
 * model cannot infer where an instruction stops and pasted content begins, and a
 * blueprint or a generated answer can carry text that reads like an instruction.
 *
 * <p>
 * The tag is a boundary, not a sandbox: the caller still has to say, in its own prompt,
 * what the tag means and that its contents are data. This class only draws the line.
 *
 * @author Ivan Rodriguez
 */
public final class PromptData {

	private PromptData() {
	}

	/**
	 * The material as a delimited block, ready to append to a message.
	 * @param tag the tag name to wrap it in, for example {@code document}
	 * @param content the material to hand to the model
	 * @return the content between {@code <tag>} and {@code </tag>}, each on its own line
	 */
	public static String block(String tag, String content) {
		return '<' + tag + ">\n" + content + "\n</" + tag + '>';
	}

}
