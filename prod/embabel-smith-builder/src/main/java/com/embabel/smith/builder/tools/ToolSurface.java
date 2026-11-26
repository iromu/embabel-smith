/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.embabel.smith.common.trace.TraceStore;

import org.springframework.stereotype.Component;

/**
 * What the driver has opened for the model to read. This is the whole of a builder tool's
 * reach into the build: a tool has no path of its own, no source of its own and no store
 * of its own — it reads what the driver put here, and answers "nothing is open" where the
 * driver opened nothing.
 *
 * <p>
 * <b>Why the driver decides and not the model.</b> A model-named path is the failure this
 * module already measured: {@code validatePack} used to take a directory, a drafting step
 * invented one, and one blueprint input spent 116 tool calls against paths that did not
 * exist, 92 of 93 results the same {@code LAYOUT_MISSING_MANIFEST}
 * ({@code docs/12-backlog.md}). The argument that fixed it was the draft itself. The same
 * reasoning generalises: the ingestion roots, the library and the trace store are all
 * arguments the <em>command line</em> already carries, so a step gets content and
 * document names from a listing, never a location it has to guess.
 *
 * <p>
 * <b>Why a mutable bean rather than constructor arguments.</b> The groups are Spring
 * beans, built when the context starts, and the build's inputs are only known once the
 * CLI has parsed a command — a {@code @Bean} method cannot take them. This is the same
 * shape the builder already uses for per-build state ({@code BuildCommand} creates the
 * scratch dir and hands it to {@code BuilderAgent}); the difference is that a group is
 * registered before {@code main} reaches the subcommand. Nothing else reads or writes
 * these fields, and the builder's CLI is one build per process, so there is no
 * concurrency to design for.
 *
 * <p>
 * <b>The memo is not a cache of correctness.</b> {@link #remembered} serves an identical
 * repeat of the same call from the first answer, which is what closes the open backlog
 * item that "nothing dedupes a repeat, so a model that re-sends the same draft spends the
 * same call twice". It is keyed on the tool name and its argument, it is bounded, and
 * {@link #startRun()} empties it, so a second build in one process cannot be answered
 * from the first build's files. The deterministic services behind these tools are pure
 * functions of their argument over one run, which is what makes a repeat safely the same
 * answer rather than a stale one.
 *
 * @author Ivan Rodriguez
 */
@Component
public class ToolSurface {

	/** How many distinct calls one run remembers, which is the dedupe window. */
	private static final int MEMO_ENTRIES = 128;

	private final Map<String, String> memo = new LinkedHashMap<>();

	private List<Path> blueprintRoots = List.of();

	private Path libraryRoot;

	private TraceStore traceStore;

	/**
	 * Opens the document roots a step may list and read. The model cannot add to this
	 * list; it can only ask about documents inside it.
	 * @param roots the directories the driver was given as its documents, in the order it
	 * was given them
	 */
	public void openedTo(List<Path> roots) {
		this.blueprintRoots = List.copyOf(roots);
	}

	/**
	 * The directories a document tool may read.
	 * @return the roots the driver opened, empty when it opened none
	 */
	public List<Path> blueprintRoots() {
		return this.blueprintRoots;
	}

	/**
	 * Opens the pack library a step may list and rank.
	 * @param root the directory the driver treats as the library, or {@code null} for
	 * none
	 */
	public void openedLibrary(Path root) {
		this.libraryRoot = root;
	}

	/**
	 * The library a pack-listing tool may read.
	 * @return the library directory, or {@code null} when the driver opened none
	 */
	public Path libraryRoot() {
		return this.libraryRoot;
	}

	/**
	 * Opens the trace store a step may read. The builder deliberately has no trace-store
	 * implementation of its own — the file store lives in {@code embabel-smith-runtime},
	 * which this module does not depend on — so a driver that wants its runs readable
	 * hands one over, and one that does not leaves the trace tools to report that
	 * honestly.
	 * @param store the store the driver supplied, or {@code null} for none
	 */
	public void openedTraces(TraceStore store) {
		this.traceStore = store;
	}

	/**
	 * The store a trace tool may read.
	 * @return the driver's trace store, or {@code null} when none is wired
	 */
	public TraceStore traceStore() {
		return this.traceStore;
	}

	/**
	 * Begins a run: forgets every answer of the previous one. The driver calls this
	 * before it deploys a pack so a repeat is deduped <em>within</em> a build and never
	 * across two.
	 */
	public void startRun() {
		this.memo.clear();
	}

	/**
	 * The answer to a call that has already been made in this run, or the first answer.
	 * @param tool the tool's name
	 * @param arguments the arguments exactly as the model sent them
	 * @param firstAnswer the body that computes the answer when this call is new
	 * @return the rendered result, prefixed to say so when it is a repeat
	 */
	public String remembered(String tool, String arguments, Supplier<String> firstAnswer) {
		String key = tool + " " + ((arguments != null) ? arguments.trim() : "");
		String seen = this.memo.get(key);
		if (seen != null) {
			return "[identical to an earlier call in this run, answered from it rather than re-run]"
					+ System.lineSeparator() + seen;
		}
		String answer = firstAnswer.get();
		remember(key, answer);
		return answer;
	}

	private void remember(String key, String answer) {
		if (this.memo.size() >= MEMO_ENTRIES) {
			String oldest = this.memo.keySet().iterator().next();
			this.memo.remove(oldest);
		}
		this.memo.put(key, answer);
	}

}
