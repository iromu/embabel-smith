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

package com.embabel.smith.unified.dto;

/**
 * One ranked retrieval hit: the spec, its retrieval text, its (curation-adjusted) score,
 * its output type, and its lifecycle state (POC 18 — the retrieval pane curates the
 * library, not just scores it).
 *
 * @param name The spec name.
 * @param description The spec's retrieval text.
 * @param score The curation-adjusted retrieval score.
 * @param outputType The spec's output type.
 * @param state The spec's lifecycle state.
 * @author Ivan Rodriguez
 */
public record RetrieveResult(String name, String description, double score, String outputType, String state) {
}
