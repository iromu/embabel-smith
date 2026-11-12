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

package com.embabel.smith.unified;

import com.embabel.smith.retrieval.scale.SpecScorer;

/**
 * The active retrieval scorer and its display name — the shared {@link SpecScorer} seam
 * the Retrieve and Scale panes drive. The default is the offline lexical scorer; a denser
 * scorer (POC 17/19/20) drops in by supplying a different {@code ScorerBinding} bean (for
 * POC 20, {@code builder.scorer=embedding}), without changing the panes' interface (the
 * response carries the active scorer name).
 *
 * @param scorer The active retrieval scorer.
 * @param name The display name of the active scorer.
 * @author Ivan Rodriguez
 */
public record ScorerBinding(SpecScorer scorer, String name) {
}
