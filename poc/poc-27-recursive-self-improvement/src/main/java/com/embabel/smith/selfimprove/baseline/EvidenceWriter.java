/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes a piece of evidence to a file as JSON, which is the only thing the two evidence
 * artifacts of tasks 2.4 and 2.5 are: files a later section reads.
 *
 * <p>
 * There is no reader here on purpose. The referee checks for these files' presence
 * ({@code Referee.assertOuterLoopMayStart}) and task 7.4 reads the floor back, so the
 * shape is fixed by what is written and not by a class that could quietly reinterpret it
 * later.
 *
 * @author Ivan Rodriguez
 */
public final class EvidenceWriter {

	private static final ObjectMapper JSON = new ObjectMapper();

	private EvidenceWriter() {
	}

	/**
	 * Write one evidence record, replacing whatever was at that path.
	 * @param evidence the record to write
	 * @param file the file to write it to; parent directories are created
	 */
	public static void write(Object evidence, Path file) {
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), evidence);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot write the evidence file " + file, ex);
		}
	}

}
