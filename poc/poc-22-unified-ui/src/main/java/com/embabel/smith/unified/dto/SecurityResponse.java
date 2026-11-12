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
 * The result of scanning a agent spec (POC 21): the aggregated verdict, the recommended
 * minimum trust tier, a one-line summary, and the ordered findings. {@link #error} is
 * non-null when the request could not be scanned (no spec selected, or an unknown spec).
 *
 * @param error The error when the request could not be scanned, or null.
 * @param specName The spec scanned.
 * @param verdict The aggregated verdict.
 * @param recommendedTier The recommended minimum trust tier.
 * @param summary A one-line summary.
 * @param findings The ordered findings.
 * @author Ivan Rodriguez
 */
public record SecurityResponse(String error, String specName, String verdict, String recommendedTier, String summary,
		List<SecurityFinding> findings) {

	public static SecurityResponse error(String error) {
		return new SecurityResponse(error, null, null, null, null, List.of());
	}

	public static SecurityResponse ok(String specName, String verdict, String tier, String summary,
			List<SecurityFinding> findings) {
		return new SecurityResponse(null, specName, verdict, tier, summary, findings);
	}
}
