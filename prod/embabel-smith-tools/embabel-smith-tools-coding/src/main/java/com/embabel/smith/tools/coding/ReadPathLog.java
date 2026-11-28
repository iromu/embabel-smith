/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the published file tools read, kept so a run can say what it touched rather than
 * only what it changed.
 *
 * <p>
 * <b>Why the addon keeps this at all.</b> Upstream keeps a log of both halves of the
 * story it can keep — {@code FileChangeLog.getChanges()} for what a write instance
 * changed, and {@code FileReadLog.getPathsRead()} for what a reader looked at — and
 * {@link CodingFileTools} holds the instances precisely so a host can reach them. The
 * read half is the narrower of the two, and it is the half with the gap: that log belongs
 * to the read-only instance, so it misses the searches, which this addon publishes from
 * {@code LocalDirectory}; and it records a path and nothing else. A run that says "it
 * read {@code notes.txt}" without saying <em>which tool</em> answered and <em>under which
 * root</em> has described an event it cannot check, and the root is the whole point of
 * this addon. So the record here carries three things per call: the file, the tool that
 * answered, the root it was confined to.
 *
 * <p>
 * <b>Reads, not everything.</b> The partition below is stated rather than inferred: a
 * call is recorded when the tool that answered is one that only looks. A write is not a
 * read and a log that quietly absorbed it would stop meaning "what the run saw" and start
 * meaning "what the run did". The partition is asserted against the group's own offered
 * surface by this module's test, so a platform release that adds a reader nothing here
 * names fails a test rather than shrinking the record in silence.
 *
 * <p>
 * <b>A refused call records nothing.</b> Confinement refused means nothing was read, and
 * a record for it would claim a touch that did not happen. The refusal is already in the
 * tool result, naming the path and the root, which is where a model reading it needs it.
 *
 * <p>
 * <b>The list is per boot, and a run attributes itself.</b> Tools are beans, so one
 * instance answers every run in the process — the same reason {@link RunRoot} is a
 * boot-time value. A run that wants only its own reads takes {@link #reads()} at its
 * start and reports what was appended after that point; nothing here needs a run boundary
 * to be correct, and nothing here keeps a second list that could disagree with the first.
 *
 * @author Ivan Rodriguez
 */
public final class ReadPathLog {

	/**
	 * The offered methods that only look. The searches are in this list because walking
	 * the root and naming what is in it is a read, whether or not a file's contents came
	 * back.
	 */
	public static final Set<String> READ_TOOLS = Set.of("readFile", "listFiles", "findFiles", "findPatternInProject",
			"fileSize", "fileCount");

	/**
	 * The offered methods that change something, stated so the test can assert that the
	 * two lists cover the surface rather than trusting that they do.
	 */
	public static final Set<String> WRITE_TOOLS = Set.of("createFile", "writeFile", TargetedEditTool.EDIT_FILE,
			"appendFile", "delete", "createDirectory");

	private final List<Read> reads = new CopyOnWriteArrayList<>();

	/**
	 * Whether a named tool is one this log records.
	 * @param toolName the name as the group offers it
	 * @return true when calling it reads rather than writes
	 */
	public static boolean records(String toolName) {
		return READ_TOOLS.contains(toolName);
	}

	/**
	 * Note one answered read.
	 * @param file the file or directory touched, resolved inside the root
	 * @param tool the tool that answered
	 * @param root the root it was confined to
	 */
	void record(String file, String tool, String root) {
		this.reads.add(new Read(file, tool, root));
	}

	/**
	 * Every read answered since the process booted, oldest first.
	 * @return the records
	 */
	public List<Read> reads() {
		return List.copyOf(this.reads);
	}

	/**
	 * The records as lines, one per read, in the order they happened — the shape a run
	 * report prints, matching the per-call lines a script call is reported in.
	 * @return the text, empty when nothing was read
	 */
	public String describe() {
		StringBuilder text = new StringBuilder();
		for (Read read : this.reads) {
			text.append("read ")
				.append(read.file())
				.append(" with ")
				.append(read.tool())
				.append(" under root ")
				.append(read.root())
				.append('\n');
		}
		return text.toString();
	}

	/**
	 * One answered read.
	 *
	 * @param file the file or directory that was touched
	 * @param tool the tool that answered, which is what distinguishes a listing from a
	 * read of the same path
	 * @param root the directory the tool was confined to when it answered
	 */
	public record Read(String file, String tool, String root) {
	}

}
