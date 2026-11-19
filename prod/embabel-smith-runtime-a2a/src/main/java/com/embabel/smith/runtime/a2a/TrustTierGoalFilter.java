/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import com.embabel.agent.core.Goal;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.core.TrustTierPolicy;
import com.embabel.smith.specsource.ResolvedSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;
import com.embabel.smith.specsource.SpecSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The agent card's goal filter: the goals the endpoint's configured trust tier cannot run
 * are not advertised.
 *
 * <p>
 * A goal's tier requirement is a property of the pack that exports it — tool groups are
 * declared on steps, not on goals — so the filter reads the configured spec source and
 * applies {@link TrustTierPolicy} to each pack, excluding the goal of every pack with a
 * tool group the tier disallows. It is the same per-pack check {@code RuntimeCore} makes
 * before a run, so the card and the gate agree about which packs are the problem.
 *
 * <p>
 * <b>The source is read once, lazily.</b> Not in the constructor: that would resolve a
 * {@code git:} source during boot, and a source that is unreachable at startup would fail
 * a boot that has nothing to do with the source. Not on every card request either — the
 * packs do not change under a running endpoint.
 *
 * <p>
 * <b>An unreadable source admits every goal.</b> The card would then advertise more than
 * the endpoint can run, which is the lesser lie: the run still goes through the runtime's
 * own tier gate, so nothing disallowed executes, and the failure the client gets names
 * the source. Refusing to advertise anything would make a source that merely moved look
 * like an empty agent. The warning below is what makes the case visible.
 *
 * @author Ivan Rodriguez
 */
public class TrustTierGoalFilter {

	private static final Logger logger = LoggerFactory.getLogger(TrustTierGoalFilter.class);

	private final TrustTierPolicy tierPolicy = new TrustTierPolicy();

	private final String sourceUri;

	private final TrustTier tier;

	private volatile Set<String> disallowedGoals;

	/**
	 * Creates the filter.
	 * @param sourceUri the spec source whose packs decide which goals the tier can run,
	 * or empty when the endpoint has no source
	 * @param tier the trust tier every run of this endpoint executes under
	 */
	public TrustTierGoalFilter(String sourceUri, TrustTier tier) {
		this.sourceUri = sourceUri;
		this.tier = tier;
	}

	/**
	 * Whether the card may advertise a deployed goal.
	 * @param goal the goal the deployed agent exports
	 * @return whether the configured tier can run the pack that exports the goal
	 */
	public boolean allows(Goal goal) {
		return !disallowedGoals().contains(goal.getName());
	}

	private Set<String> disallowedGoals() {
		Set<String> disallowed = this.disallowedGoals;
		if (disallowed == null) {
			disallowed = readDisallowedGoals();
			this.disallowedGoals = disallowed;
		}
		return disallowed;
	}

	private Set<String> readDisallowedGoals() {
		if (this.sourceUri == null || this.sourceUri.isBlank()) {
			return Set.of();
		}
		AgentSpecReader reader = new AgentSpecReader();
		try {
			ResolvedSpecSource source = SpecSource.parse(this.sourceUri).resolve();
			Set<String> disallowed = new LinkedHashSet<>();
			for (ResolvedSpec spec : source.packs()) {
				AgentSpec pack = reader.read(spec.root());
				if (declaresDisallowedToolGroup(pack)) {
					disallowed.add(pack.goal().name());
				}
			}
			return Set.copyOf(disallowed);
		}
		catch (IOException | RuntimeException ex) {
			logger.warn(
					"Cannot read the spec source '{}' to filter the agent card by trust tier '{}':"
							+ " advertising every deployed goal. The run-time tier gate still applies.",
					this.sourceUri, this.tier, ex);
			return Set.of();
		}
	}

	private boolean declaresDisallowedToolGroup(AgentSpec pack) {
		return this.tierPolicy.declaredToolGroups(pack).stream().anyMatch((group) -> !this.tier.allows(group));
	}

}
