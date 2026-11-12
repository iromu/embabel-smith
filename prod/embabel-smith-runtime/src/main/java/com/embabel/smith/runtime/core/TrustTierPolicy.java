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

package com.embabel.smith.runtime.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Enforces the per-tier tool-group allowlist (POC 10): a pack is runnable under a tier
 * only if every tool group its actions declare is permitted by that tier. Checked per
 * run, before execution, so a blocked run never executes a high-trust step.
 *
 * @author Ivan Rodriguez
 */
public class TrustTierPolicy {

	/**
	 * The distinct tool-group roles a pack's actions declare.
	 * @param pack the agent spec to inspect
	 * @return the distinct tool-group roles the pack declares
	 */
	public Set<String> declaredToolGroups(AgentSpec pack) {
		Set<String> groups = new TreeSet<>();
		pack.steps().forEach((step) -> groups.addAll(step.toolGroups()));
		return groups;
	}

	/**
	 * The packs that cannot run under the given tier; empty when every pack is allowed.
	 * @param packs the agent specs to check
	 * @param tier the trust tier to check against
	 * @return the violations, one per pack that cannot run under the tier
	 */
	public List<Violation> violations(List<AgentSpec> packs, TrustTier tier) {
		List<Violation> violations = new ArrayList<>();
		for (AgentSpec pack : packs) {
			List<String> disallowed = declaredToolGroups(pack).stream()
				.filter((group) -> !tier.allows(group))
				.sorted()
				.toList();
			if (!disallowed.isEmpty()) {
				violations.add(new Violation(pack.name(), disallowed));
			}
		}
		return violations;
	}

	/**
	 * One pack whose declared tool groups are not all permitted by the granted tier.
	 * @param packName the name of the offending pack
	 * @param disallowedGroups the tool groups the tier does not allow
	 */
	public record Violation(String packName, List<String> disallowedGroups) {
		public Violation {
			disallowedGroups = List.copyOf(disallowedGroups);
		}
	}

}
