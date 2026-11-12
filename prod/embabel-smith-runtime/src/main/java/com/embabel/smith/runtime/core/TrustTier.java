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

import java.util.Set;

/**
 * The product trust tiers (the POC 10 tier model collapsed to the two tiers the CLI
 * exposes). Each tier allows a set of tool-group roles; a step may only run under a tier
 * whose allowlist covers the tool groups its pack declares.
 *
 * @author Ivan Rodriguez
 */
public enum TrustTier {

	/**
	 * The default tier: everything except high-trust tool groups such as {@code shell}.
	 */
	STANDARD(Set.of("search", "web", "file")),

	/** The high-trust tier: all tool groups, including {@code shell}. */
	HIGH(Set.of("*"));

	private final Set<String> allowedToolGroups;

	TrustTier(Set<String> allowedToolGroups) {
		this.allowedToolGroups = allowedToolGroups;
	}

	/**
	 * Whether this tier permits the named tool-group role.
	 * @param toolGroupRole the tool-group role to check
	 * @return whether the tier permits the role
	 */
	public boolean allows(String toolGroupRole) {
		return this.allowedToolGroups.contains("*") || this.allowedToolGroups.contains(toolGroupRole);
	}

}
