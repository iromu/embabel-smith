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

import java.util.Set;

/**
 * The trust tiers from doc 03 §2.1 (and the survey's §6). Each tier allows a set of
 * tool-group roles; a spec may only run under a tier whose allowlist covers every tool
 * group the spec declares. {@code T4} allows everything. Extracted from POC 10 so the
 * builder's pre-publish scanner is self-contained.
 *
 * @author Ivan Rodriguez
 */
public enum TrustTier {

	/** Instructions only — no tool access, full isolation. */
	T1_COMMUNITY(Set.of()),

	/** Instructions + read-only tools (search, web fetch). */
	T2_VERIFIED(Set.of("search", "web")),

	/** Instructions + write tools (file, shell) + MCP. */
	T3_PARTNER(Set.of("search", "web", "file", "shell")),

	/** Full capabilities, including host-level execution. */
	T4_PLATFORM(Set.of("*"));

	private final Set<String> allowedToolGroups;

	TrustTier(Set<String> allowedToolGroups) {
		this.allowedToolGroups = allowedToolGroups;
	}

	/**
	 * Whether this tier permits the named tool-group role.
	 * @param toolGroupRole the tool-group role to check.
	 * @return true if this tier permits the role, false otherwise.
	 */
	public boolean allows(String toolGroupRole) {
		return this.allowedToolGroups.contains("*") || this.allowedToolGroups.contains(toolGroupRole);
	}

}
