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
 * The result of a trust-tier check (POC 10): whether the spec is allowed under the tier,
 * which of its tool groups are not permitted, and the full set it declares.
 * {@link #error} is non-null when the request could not be checked (no spec selected, an
 * unknown spec, or an unknown tier).
 *
 * @param error The error when the request could not be checked, or null.
 * @param specName The spec checked.
 * @param tier The trust tier checked.
 * @param allowed Whether the spec is allowed under the tier.
 * @param disallowedGroups The declared tool groups not permitted under the tier.
 * @param declaredToolGroups All the tool groups the spec declares.
 * @author Ivan Rodriguez
 */
public record TrustResponse(String error, String specName, String tier, boolean allowed, List<String> disallowedGroups,
		List<String> declaredToolGroups) {

	public static TrustResponse error(String error) {
		return new TrustResponse(error, null, null, false, List.of(), List.of());
	}

	public static TrustResponse ok(String specName, String tier, boolean allowed, List<String> disallowed,
			List<String> declared) {
		return new TrustResponse(null, specName, tier, allowed, disallowed, declared);
	}
}
