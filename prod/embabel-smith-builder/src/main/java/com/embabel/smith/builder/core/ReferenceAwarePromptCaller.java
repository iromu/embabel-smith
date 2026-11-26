/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import com.embabel.agent.api.reference.LlmReference;

/**
 * The view of the builder's model layer that lets a single call carry an Embabel
 * {@link LlmReference} — the framework's way of offering a capability's <em>tools</em> to
 * a model, rather than its text. A {@code Skills} pack is one: attaching it puts
 * {@code activate}, {@code listResources}, {@code readResource} and, with a script engine
 * installed, the pack's {@code <skill>_<script>} tools in the request next to the prompt.
 *
 * <p>
 * <b>Why this is its own type instead of another overload of
 * {@link SpecPromptCaller}.</b> That seam has about twenty users, and its test doubles
 * are lambdas. A {@code default call(system, user, reference)} that forwards to
 * {@code call(system, user)} would compile everywhere and honour the reference nowhere:
 * every lambda fake would go on answering as though nothing were attached, including in
 * the test written to prove the arm carries it. {@code SpecPromptCaller}'s three-argument
 * overload is already that trap in this repo — a fake that quietly drops the model name —
 * so a call that carries something the test must be able to see gets its own type, where
 * a double either implements it or cannot be used as one.
 *
 * <p>
 * {@link com.embabel.smith.builder.core.AiSpecPromptCaller} implements both types, which
 * is the production case: one model layer, one endpoint configuration, one set of roles,
 * and a call that carries a reference differing from a plain one only by that reference.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface ReferenceAwarePromptCaller {

	/**
	 * Makes one model call with a capability attached to it.
	 * @param system the system prompt, or empty for none
	 * @param user the user turn
	 * @param reference the capability whose tools the model is offered alongside the
	 * prompt
	 * @return the model's answer
	 */
	String call(String system, String user, LlmReference reference);

}
