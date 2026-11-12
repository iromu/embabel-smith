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

package com.embabel.smith.studio.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.studio.improve.TraceWindowReader;
import com.embabel.smith.studio.rest.dto.TraceInfo;
import com.embabel.smith.studio.storage.StorageContext;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Traces surface (change 06): the rolling window of a spec's trace records, most
 * recent first, each carrying its per-step records (for step-level drill-down) and the
 * failure reason where present. Offline — reads the selected trace store (JSONL file or
 * database, per the storage settings), no model calls.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class TraceController {

	private final TraceWindowReader reader;

	public TraceController(StorageContext storageContext) {
		this.reader = new TraceWindowReader(storageContext.traceStore());
	}

	/**
	 * The recent traces for a spec, most recent first, bounded by {@code limit}.
	 * @param spec the spec whose trace window is read.
	 * @param limit the maximum number of recent traces to return.
	 * @return the recent traces, most recent first.
	 */
	@GetMapping("/api/traces")
	public List<TraceInfo> traces(@RequestParam("spec") String spec,
			@RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {
		List<TraceRecord> window = this.reader.window(spec);
		int from = Math.max(0, window.size() - Math.max(1, limit));
		List<TraceRecord> recent = new ArrayList<>(window.subList(from, window.size()));
		Collections.reverse(recent); // the window is completion order; the pane wants
										// most recent first
		return recent.stream().map(TraceInfo::of).toList();
	}

}
