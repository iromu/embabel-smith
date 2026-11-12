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

package com.embabel.smith.studio.improve;

import java.util.List;

import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.runtime.trace.TraceStore;

/**
 * Reads a rolling window of the most recent traces per spec from the trace store (change
 * 03). The store is a single append-only sequence across all specs (a JSONL file by
 * default, or a database — see the {@link TraceStore} SPI); this reader filters it by the
 * pack that exported each run's goal and keeps only the most recent {@code windowSize}
 * per spec. Corrupt lines are already skipped (and reported) by the file store, so the
 * window is built from well-formed records only.
 *
 * @author Ivan Rodriguez
 */
public class TraceWindowReader {

	/** The default rolling window size (design D3). */
	public static final int DEFAULT_WINDOW = 200;

	private final TraceStore store;

	private final int windowSize;

	public TraceWindowReader(TraceStore store) {
		this(store, DEFAULT_WINDOW);
	}

	public TraceWindowReader(TraceStore store, int windowSize) {
		if (windowSize <= 0) {
			throw new IllegalArgumentException("window size must be positive: " + windowSize);
		}
		this.store = store;
		this.windowSize = windowSize;
	}

	/**
	 * The most recent {@code windowSize} traces for the named spec, in completion order.
	 * A spec with no traces yields an empty list.
	 * @param spec the spec to read the window for
	 * @return the most recent traces for the spec, in completion order.
	 */
	public List<TraceRecord> window(String spec) {
		List<TraceRecord> all = this.store.read()
			.records()
			.stream()
			.filter((record) -> spec.equals(packName(record)))
			.toList();
		int from = Math.max(0, all.size() - this.windowSize);
		return List.copyOf(all.subList(from, all.size()));
	}

	/**
	 * The distinct spec names that have at least one trace in the store.
	 * @return the distinct spec names with at least one trace.
	 */
	public List<String> specs() {
		return this.store.read()
			.records()
			.stream()
			.map(TraceWindowReader::packName)
			.filter((name) -> name != null)
			.distinct()
			.sorted()
			.toList();
	}

	private static String packName(TraceRecord record) {
		return (record.pack() != null) ? record.pack().name() : null;
	}

}
