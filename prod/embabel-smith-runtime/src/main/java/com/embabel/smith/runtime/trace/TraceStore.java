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

import java.util.List;

/**
 * The pluggable trace store SPI (change: smith-studio-postgres-storage). A completed run
 * appends one {@link TraceRecord}; readers list the records in completion order.
 *
 * <p>
 * The JSONL file store ({@link JsonlTraceStore}, one JSON object per line in a
 * configurable directory, defaulting to {@code ~/.smith/traces/runs.jsonl}) is the
 * default implementation, and the CLI {@code --trace-dir} keeps selecting it. Alternative
 * stores (e.g. a database) may be provided without changing the record shape or run
 * behavior; the record shape is carried by {@link TraceRecord}, and a store that cannot
 * represent a corrupt-line warning simply reports an empty warnings list.
 *
 * @author Ivan Rodriguez
 */
public interface TraceStore {

	/**
	 * Appends one record (one run), in completion order.
	 * @param record the trace record to append
	 */
	void append(TraceRecord record);

	/**
	 * Reads the records in completion order, plus any read warnings.
	 * @return the records in completion order plus the read warnings
	 */
	ReadResult read();

	/**
	 * The records read from the store plus the corrupt-line warnings (empty for non-file
	 * stores).
	 * @param records the records in completion order
	 * @param warnings the corrupt-line warnings, empty for non-file stores
	 */
	record ReadResult(List<TraceRecord> records, List<String> warnings) {
	}

}
