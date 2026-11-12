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

package com.embabel.smith.studio.rest;

import java.util.List;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.core.TrustTierPolicy;
import com.embabel.smith.studio.rest.dto.TrustRequest;
import com.embabel.smith.studio.rest.dto.TrustResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Trust pane (change 06): check a agent spec's declared tool groups against a
 * trust-tier allowlist with the change-03 runtime's policy. Offline — no model calls.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class TrustController {

	private final SourceService sourceService;

	private final TrustTierPolicy policy;

	public TrustController(SourceService sourceService) {
		this.sourceService = sourceService;
		this.policy = new TrustTierPolicy();
	}

	@PostMapping("/api/trust")
	public TrustResponse check(@RequestBody TrustRequest request) {
		if (request.source() == null || request.source().isBlank() || request.spec() == null
				|| request.spec().isBlank()) {
			return TrustResponse.error("No source or spec selected; pick one to check.");
		}
		TrustTier tier = parseTier(request.tier());
		try {
			AgentSpec pack = this.sourceService.readPack(request.source(), request.spec());
			List<TrustTierPolicy.Violation> violations = this.policy.violations(List.of(pack), tier);
			boolean allowed = violations.isEmpty();
			List<String> disallowed = (allowed) ? List.of() : violations.get(0).disallowedGroups();
			List<String> declared = List.copyOf(this.policy.declaredToolGroups(pack));
			return TrustResponse.ok(request.spec(), tier.name(), allowed, disallowed, declared);
		}
		catch (Exception ex) {
			String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
			return TrustResponse.error("Trust check failed: " + message);
		}
	}

	private static TrustTier parseTier(String tier) {
		if (tier == null || tier.isBlank()) {
			return TrustTier.STANDARD;
		}
		try {
			return TrustTier.valueOf(tier.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			return TrustTier.STANDARD;
		}
	}

}
