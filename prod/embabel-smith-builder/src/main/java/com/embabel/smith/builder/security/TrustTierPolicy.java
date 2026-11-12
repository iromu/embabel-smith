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

package com.embabel.smith.builder.security;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Enforces the per-tier tool-group allowlist (doc 03 §2.1): a spec is runnable under a
 * tier only if every tool group its actions declare is permitted by that tier. Extracted
 * from POC 10 so the builder's pre-publish scanner is self-contained.
 *
 * @author Ivan Rodriguez
 */
public class TrustTierPolicy {

	/**
	 * The distinct tool-group roles a spec's actions declare.
	 * @param spec the agent spec to inspect.
	 * @return the distinct tool-group roles declared by the spec's actions.
	 */
	public Set<String> declaredToolGroups(AgentSpec spec) {
		Set<String> groups = new TreeSet<>();
		spec.steps().forEach((step) -> groups.addAll(step.toolGroups()));
		return groups;
	}

	/**
	 * Checks a spec against a tier.
	 * @param spec the agent spec to check.
	 * @param tier the trust tier to check against.
	 * @return the outcome of the tier check.
	 */
	public CheckResult check(AgentSpec spec, TrustTier tier) {
		List<String> disallowed = declaredToolGroups(spec).stream()
			.filter((group) -> !tier.allows(group))
			.sorted()
			.toList();
		return new CheckResult(disallowed.isEmpty(), disallowed);
	}

	/**
	 * The outcome of a tier check: whether the spec is allowed, and which of its groups
	 * are not.
	 *
	 * @param allowed whether the spec is permitted under the tier.
	 * @param disallowedGroups the tool-group roles the tier does not permit.
	 */
	public record CheckResult(boolean allowed, List<String> disallowedGroups) {
		public CheckResult {
			disallowedGroups = (disallowedGroups != null) ? List.copyOf(disallowedGroups) : List.of();
		}
	}
}
