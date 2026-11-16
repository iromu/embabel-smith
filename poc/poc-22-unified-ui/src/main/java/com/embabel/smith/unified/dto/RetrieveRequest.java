/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * A retrieval query: the natural-language task and how many specs to return.
 *
 * @param query The natural-language retrieval task.
 * @param k How many specs to return, or null to use the default.
 * @author Ivan Rodriguez
 */
public record RetrieveRequest(String query, Integer k) {

	public int kOrDefault() {
		return (this.k == null || this.k <= 0) ? 5 : this.k;
	}
}
