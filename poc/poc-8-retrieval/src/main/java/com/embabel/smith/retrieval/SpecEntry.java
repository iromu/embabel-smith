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

package com.embabel.smith.retrieval;

import java.util.List;

/**
 * A lightweight index entry for a spec in the registry — the metadata the pre-filter
 * needs to route a task, without loading the spec's full action chain (progressive
 * disclosure: only this metadata is held hot; the pack is read on demand once a spec is
 * selected).
 *
 * @param id stable spec id
 * @param name spec name
 * @param description what the spec does — the text that gets matched against the task
 * @param inputTypes the input type names the spec's action consumes
 * @param outputType the output type name the spec produces
 * @author Ivan Rodriguez
 */
public record SpecEntry(String id, String name, String description, List<String> inputTypes, String outputType) {

	public SpecEntry {
		inputTypes = (inputTypes != null) ? List.copyOf(inputTypes) : List.of();
	}
}
