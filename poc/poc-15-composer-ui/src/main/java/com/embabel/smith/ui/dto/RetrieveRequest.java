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

package com.embabel.smith.ui.dto;

/**
 * A retrieval query: the natural-language task and how many specs to return.
 *
 * @param query the natural-language task to retrieve for
 * @param k how many specs to return, or null to use the default
 * @author Ivan Rodriguez
 */
public record RetrieveRequest(String query, Integer k) {

	public int kOrDefault() {
		return (this.k == null || this.k <= 0) ? 5 : this.k;
	}
}
