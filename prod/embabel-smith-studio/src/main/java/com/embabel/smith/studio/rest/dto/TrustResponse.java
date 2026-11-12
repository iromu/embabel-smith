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

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The result of a trust-tier check (the Trust pane): whether the spec's declared tool
 * groups are all permitted by the tier, the disallowed groups, and the groups the spec
 * declares.
 *
 * @param error the structured error (null on success).
 * @param specName the spec that was checked.
 * @param tier the trust tier checked against.
 * @param allowed whether all declared groups are permitted by the tier.
 * @param disallowedGroups the declared groups the tier does not permit.
 * @param declaredToolGroups the tool groups the spec declares.
 * @author Ivan Rodriguez
 */
public record TrustResponse(String error, String specName, String tier, boolean allowed, List<String> disallowedGroups,
		List<String> declaredToolGroups) {

	public static TrustResponse ok(String specName, String tier, boolean allowed, List<String> disallowedGroups,
			List<String> declaredToolGroups) {
		return new TrustResponse(null, specName, tier, allowed, disallowedGroups, declaredToolGroups);
	}

	public static TrustResponse error(String error) {
		return new TrustResponse(error, null, null, false, List.of(), List.of());
	}
}
