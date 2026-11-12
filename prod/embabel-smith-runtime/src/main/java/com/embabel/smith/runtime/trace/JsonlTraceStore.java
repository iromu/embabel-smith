/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.runtime.trace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The file-based trace store: one JSON object per line, one line per run, append-only.
 *
 * <p>
 * Default location {@code ~/.smith/traces/runs.jsonl}; the directory is created on first
 * write. The writer stays dumb — no locking beyond {@code SYNC} appends (v1 is
 * single-process per host), no retention. Readers skip corrupt lines and report them as
 * warnings.
 *
 * <p>
 * This is the default implementation of the {@link TraceStore} SPI (change:
 * smith-studio-postgres-storage); the CLI {@code --trace-dir} selects it, and a
 * database-backed store may be substituted at the studio layer without changing this file
 * store.
 *
 * @author Ivan Rodriguez
 */
public class JsonlTraceStore implements TraceStore {

	private final Path file;

	private final TraceRecordJson codec = new TraceRecordJson();

	public JsonlTraceStore(Path file) {
		this.file = file;
	}

	/**
	 * The default trace file: {@code ~/.smith/traces/runs.jsonl}.
	 * @return the default trace file path
	 */
	public static Path defaultPath() {
		return Path.of(System.getProperty("user.home"), ".smith", "traces", "runs.jsonl");
	}

	public Path file() {
		return this.file;
	}

	/**
	 * Appends one record as one line, creating the parent directory on first write.
	 * @param record the trace record to append
	 */
	public void append(TraceRecord record) {
		try {
			if (this.file.getParent() != null) {
				Files.createDirectories(this.file.getParent());
			}
			Files.writeString(this.file, this.codec.serialize(record) + System.lineSeparator(),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot append trace record to " + this.file, ex);
		}
	}

	/**
	 * Reads the store in completion order. Corrupt (non-blank, unparseable) lines are
	 * skipped and reported in {@link ReadResult#warnings()}.
	 * @return the records in completion order plus the corrupt-line warnings
	 */
	public ReadResult read() {
		List<TraceRecord> records = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		if (!Files.isRegularFile(this.file)) {
			return new ReadResult(List.of(), List.of());
		}
		try (Stream<String> lines = Files.lines(this.file)) {
			AtomicInteger lineNumber = new AtomicInteger();
			lines.forEach((line) -> {
				int n = lineNumber.incrementAndGet();
				if (line.isBlank()) {
					return;
				}
				try {
					records.add(this.codec.deserialize(line));
				}
				catch (IllegalArgumentException ex) {
					warnings.add("skipping corrupt line " + n + ": " + ex.getMessage());
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read trace store " + this.file, ex);
		}
		return new ReadResult(List.copyOf(records), List.copyOf(warnings));
	}

}
