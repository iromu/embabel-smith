/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * The ranked retrieval results and the name of the scorer that produced them.
 *
 * @param scorer the name of the scorer that produced the results
 * @param results the ranked retrieval hits
 * @author Ivan Rodriguez
 */
public record RetrieveResponse(String scorer, List<RetrieveResult> results) {
}
