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

/**
 * The run was blocked because a pack declares tool groups the granted trust tier does not
 * allow. Carries the violations so the error can name both the offending packs and the
 * tier requirement.
 *
 * @author Ivan Rodriguez
 */
public class TrustTierBlockedException extends RuntimeException {

	private final List<TrustTierPolicy.Violation> violations;

	private final TrustTier grantedTier;

	public TrustTierBlockedException(TrustTier grantedTier, List<TrustTierPolicy.Violation> violations) {
		super(message(grantedTier, violations));
		this.violations = List.copyOf(violations);
		this.grantedTier = grantedTier;
	}

	private static String message(TrustTier grantedTier, List<TrustTierPolicy.Violation> violations) {
		List<String> parts = new ArrayList<>();
		for (TrustTierPolicy.Violation violation : violations) {
			parts.add("pack '" + violation.packName() + "' declares tool group(s) " + violation.disallowedGroups()
					+ " that require a higher trust tier (granted: " + grantedTier.name().toLowerCase() + ")");
		}
		return "Run blocked by trust tier: " + String.join("; ", parts)
				+ ". Re-run with the higher tier to allow these steps.";
	}

	public List<TrustTierPolicy.Violation> violations() {
		return this.violations;
	}

	public TrustTier grantedTier() {
		return this.grantedTier;
	}

}
