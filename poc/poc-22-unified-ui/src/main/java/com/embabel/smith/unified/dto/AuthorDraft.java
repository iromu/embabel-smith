/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
