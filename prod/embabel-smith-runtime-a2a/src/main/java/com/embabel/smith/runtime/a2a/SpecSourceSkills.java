/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.core.TrustTierPolicy;
import com.embabel.smith.specsource.ResolvedSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;
import com.embabel.smith.specsource.SpecSource;
import io.a2a.spec.AgentSkill;

/**
 * The agent card's skill list, read from the spec source the endpoint is configured to
 * run: one skill per goal exported by a pack whose tool groups that tier allows.
 *
 * <p>
 * <b>Why the source and not the platform.</b> The library's card handler reads
 * {@code AgentPlatform.getGoals()}, which is a view over the agents deployed so far — and
 * {@code RuntimeCore.ingest()} ends in {@code platform.deploy(agent)} on <em>every</em>
 * run. So that set describes what this process has already done rather than what this
 * endpoint offers, and it is empty at boot: a client that fetches the card before the
 * first run is told the agent can do nothing. (Repeated runs do not add further copies,
 * as one might expect of a re-deploying ingest: {@code getGoals()} ends in {@code toSet}
 * over a value-equal {@code Goal}, so re-deploys collapse. The emptiness alone is why the
 * card is derived from the configured source instead, which is the thing the endpoint is
 * actually able to run.)
 *
 * <p>
 * <b>The tier predicate is the run-time one.</b> Tool groups are declared on steps, not
 * on goals, so a pack's tier requirement is decided per pack, through
 * {@link TrustTierPolicy#declaredToolGroups(AgentSpec)} against
 * {@link TrustTier#allows(String)} — the same check {@code RuntimeCore} makes before a
 * run. Card and gate therefore cannot disagree about which packs are the problem.
 *
 * <p>
 * <b>Read once, lazily, and only success is cached.</b> Not in the constructor: that
 * would resolve a {@code git:} source during boot, so a source unreachable at startup
 * would fail a boot that has nothing to do with the source. Not per request either —
 * {@link SpecSource#resolve()} re-clones a {@code git:} source into a fresh temp
 * directory every call, so a per-request read would hit the network on every card fetch.
 * And a failure is deliberately not remembered: a source that was briefly unreachable
 * must not leave the endpoint permanently unable to describe itself.
 *
 * <p>
 * <b>An unreadable source raises.</b> There is nothing to fall back to now that the
 * source is the only source of skills, and serving an empty skill list would tell a
 * client the agent can do nothing — false, and false in the direction that hides a
 * misconfiguration. The raised message names the source; the library's facade turns it
 * into a 500.
 *
 * @author Ivan Rodriguez
 */
public class SpecSourceSkills {

	/** The only content mode a no-code pack declares, and the one the library passes. */
	private static final String JSON_MODE = "application/json";

	private final TrustTierPolicy tierPolicy = new TrustTierPolicy();

	private final String sourceUri;

	private final TrustTier tier;

	private volatile List<AgentSkill> cached;

	/**
	 * Creates the skill list.
	 * @param sourceUri the spec source every run of this endpoint executes against, and
	 * the only thing the card's skills can be derived from
	 * @param tier the trust tier every run executes under, and the one that decides which
	 * packs are advertised
	 */
	public SpecSourceSkills(String sourceUri, TrustTier tier) {
		this.sourceUri = sourceUri;
		this.tier = tier;
	}

	/**
	 * The skills to advertise, read from the configured source on the first call and
	 * cached from then on.
	 * @return one skill per goal exported by a pack this tier can run
	 * @throws IllegalStateException when the configured source cannot be read, naming it
	 */
	public List<AgentSkill> skills() {
		List<AgentSkill> skills = this.cached;
		if (skills == null) {
			skills = read();
			this.cached = skills;
		}
		return skills;
	}

	private List<AgentSkill> read() {
		AgentSpecReader reader = new AgentSpecReader();
		List<AgentSkill> skills = new ArrayList<>();
		try {
			ResolvedSpecSource source = SpecSource.parse(this.sourceUri).resolve();
			for (ResolvedSpec pack : source.packs()) {
				AgentSpec spec = reader.read(pack.root());
				if (this.tierAllows(spec)) {
					skills.add(skill(spec));
				}
			}
			return List.copyOf(skills);
		}
		catch (IOException | RuntimeException ex) {
			throw new IllegalStateException(
					"Cannot read the spec source '" + this.sourceUri + "' to build the agent card's skill list", ex);
		}
	}

	/**
	 * Whether the endpoint's tier can run a pack, decided by the tool groups its steps
	 * declare. Same predicate {@code RuntimeCore.checkTier} applies before a run.
	 * @param pack the pack read from the source
	 * @return whether every tool group the pack declares is allowed by the tier
	 */
	boolean tierAllows(AgentSpec pack) {
		return this.tierPolicy.declaredToolGroups(pack).stream().allMatch(this.tier::allows);
	}

	/**
	 * The skill a pack is. {@code tags} and {@code examples} are empty because a
	 * {@code goal.yml} declares neither — those two lists belong to {@code @Agent} on a
	 * Java class, which a no-code pack does not have, and {@code AgentSpecReader} would
	 * reject a {@code tags} key in the goal file. The SDK asserts the four fields it
	 * checks are non-<em>null</em>, not non-empty, so empty lists are legal and nothing
	 * is invented to fill them.
	 * @param pack the pack to advertise
	 * @return its skill, keyed and named by its exported goal
	 */
	private static AgentSkill skill(AgentSpec pack) {
		String goal = pack.goal().name();
		return new AgentSkill(goal, goal, pack.goal().description(), List.of(), List.of(), List.of(JSON_MODE),
				List.of(JSON_MODE), null);
	}

}
