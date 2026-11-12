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

import java.util.List;

/**
 * The model-dependent result of compiling a natural-language description into a draft
 * (POC 2): the goal shape, the actions, the {@code DraftQuality} verdict, and any issues
 * to review. Produced by the endpoint-gated compile collaborator; rendered by the Author
 * pane.
 *
 * @param goalName The compiled goal's name.
 * @param goalOutputType The compiled goal's output type.
 * @param goalExport Whether the goal is exported.
 * @param actions The compiled actions.
 * @param qualityVerdict The DraftQuality verdict.
 * @param degenerate Whether the draft is degenerate.
 * @param qualityReason The human-readable reason for the verdict.
 * @param issues The issues to review.
 * @author Ivan Rodriguez
 */
public record AuthorDraft(String goalName, String goalOutputType, boolean goalExport, List<ActionInfo> actions,
		String qualityVerdict, boolean degenerate, String qualityReason, List<String> issues) {
}
