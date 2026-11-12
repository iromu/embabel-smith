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

package com.embabel.smith.unified;

import java.util.Arrays;
import java.util.List;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.trust.TrustTier;
import com.embabel.smith.trust.TrustTierPolicy;
import com.embabel.smith.unified.dto.TrustRequest;
import com.embabel.smith.unified.dto.TrustResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Trust pane (POC 22): check a spec's declared tool groups against a POC 10
 * trust-tier allowlist via {@link TrustTierPolicy} — the "tool-group allowlist per trust
 * tier" that gates high-privilege specs (e.g. {@code shell}) behind {@code T3}+ contexts.
 * Pure and offline.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class TrustController {

	private final SpecCatalog catalog;

	private final TrustTierPolicy policy = new TrustTierPolicy();

	public TrustController(SpecCatalog catalog) {
		this.catalog = catalog;
	}

	/**
	 * Check a spec from the palette against a tier; report whether it is allowed and
	 * which groups are not.
	 * @param request the trust request (spec name + tier).
	 * @return the trust response (allowed flag + disallowed groups, or a named error).
	 */
	@PostMapping("/api/trust")
	public TrustResponse trust(@RequestBody TrustRequest request) {
		String specName = (request.specName() == null) ? "" : request.specName();
		if (specName.isBlank()) {
			return TrustResponse.error("No spec selected; pick a spec to check first.");
		}
		SpecNode node = this.catalog.get(specName);
		if (node == null) {
			return TrustResponse.error("Unknown spec: " + specName + ". Choose one from the palette.");
		}
		String tierName = (request.tier() == null || request.tier().isBlank()) ? "T3_PARTNER" : request.tier();
		TrustTier tier;
		try {
			tier = TrustTier.valueOf(tierName);
		}
		catch (IllegalArgumentException ex) {
			return TrustResponse
				.error("Unknown tier: " + tierName + ". Use one of " + Arrays.toString(TrustTier.values()));
		}
		AgentSpec pack = node.pack();
		TrustTierPolicy.CheckResult result = this.policy.check(pack, tier);
		List<String> declared = this.policy.declaredToolGroups(pack).stream().toList();
		return TrustResponse.ok(specName, tier.name(), result.allowed(), result.disallowedGroups(), declared);
	}

}
