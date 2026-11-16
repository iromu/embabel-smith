/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The ranked retrieval results and the name of the scorer that produced them.
 *
 * @param scorer The name of the scorer that produced the results.
 * @param results The ranked retrieval results.
 * @author Ivan Rodriguez
 */
public record RetrieveResponse(String scorer, List<RetrieveResult> results) {
}
