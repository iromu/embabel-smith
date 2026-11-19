/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.improve;

import java.io.IOException;

import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.Publisher;

/**
 * The self-improvement loop's publish policy: whether an approved candidate is pushed to
 * the improve source's remote, and whether the publish may be attempted at all.
 *
 * <p>
 * Pushing defaults to off, so an unattended loop cannot write to shared state — the
 * invariant stated in {@code docs/05-architecture.md} is that publishing commits and
 * never pushes. A {@code git:} improve source with pushing off cannot be published:
 * {@code GitPublisher} commits inside a temporary clone that {@code close()} deletes, so
 * the commit would vanish. That publish is refused with a message naming the setting that
 * allows it, and the candidate stays pending. A {@code folder:} source is already
 * durable, so the flag is ignored there (the folder publisher ignores it too) and
 * publishing proceeds unchanged.
 *
 * <p>
 * The loop's forced {@code overwrite} is deliberately not part of this policy — it is a
 * separate, still-open decision.
 *
 * @author Ivan Rodriguez
 */
public final class LoopPublishPolicy {

	/**
	 * The studio setting that decides whether the loop pushes a published candidate. Not
	 * runtime-toggleable: pushing is the credential-and-intent decision.
	 */
	public static final String PUSH_SETTING = "smith.studio.loop.push";

	private final boolean push;

	private final boolean gitSource;

	/**
	 * Creates the policy for one improve source.
	 * @param push whether a published candidate is pushed to the remote — the configured
	 * value of {@code smith.studio.loop.push}
	 * @param improveSourceUri the configured improve-source URI, or empty when no source
	 * is configured (then there is no git target to refuse)
	 */
	public LoopPublishPolicy(boolean push, String improveSourceUri) {
		this.push = push;
		this.gitSource = (improveSourceUri != null && !improveSourceUri.isBlank())
				&& PublishTarget.parse(improveSourceUri).type() == PublishTarget.Type.GIT;
	}

	/**
	 * Whether pushing to the remote is enabled.
	 * @return whether pushing to the remote is enabled.
	 */
	public boolean push() {
		return this.push;
	}

	/**
	 * Whether a publish made now would actually persist: always true for a folder source,
	 * and true for a git source only when pushing is enabled.
	 * @return whether a publish made now would persist.
	 */
	public boolean canPublish() {
		return !this.gitSource || this.push;
	}

	/**
	 * Publishes a candidate under this policy, with the loop's existing forced
	 * {@code overwrite}.
	 * @param publisher the publisher writing to the improve source
	 * @param candidate the approved candidate to publish
	 * @return the publish result
	 * @throws LoopPushDisabledException when the source is git and pushing is disabled
	 * @throws IOException when the publish itself fails
	 */
	public PublishResult publish(Publisher publisher, PendingCandidate candidate) throws IOException {
		if (!canPublish()) {
			throw new LoopPushDisabledException("Candidate for '" + candidate.spec()
					+ "' was not published: the improve " + "source is a git repository and " + PUSH_SETTING
					+ " is off, so the commit would live only in the "
					+ "publisher's temporary clone and be deleted with it. Set " + PUSH_SETTING
					+ "=true to let the loop push, or point the improve source at a folder: source. The candidate stays "
					+ "pending.");
		}
		// overwrite stays forced as it always was; only push is now a configured value.
		boolean overwrite = true;
		return publisher.publish(candidate.candidate(), candidate.proposedLifecycle(), overwrite, this.push);
	}

}
