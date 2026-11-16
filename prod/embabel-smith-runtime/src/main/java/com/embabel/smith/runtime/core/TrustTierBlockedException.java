/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
