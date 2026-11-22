/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.store;

import com.embabel.smith.builder.improve.CandidateStore;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The studio's {@link CandidateStore}: records a published candidate in the pack store,
 * which is where the version the destination replaced stays addressable (change:
 * publish-keeps-versions, decision 7). This is the only place the improvement loop's
 * builder-side seam meets the studio's persistence, and it is deliberately one call:
 * {@link ProcessStore#savePackWhenConfigured(AgentSpec)} returns nothing usable to the
 * loop, and returns {@code null} while no database is bound — that is a no-op, not a
 * failure, because the loop has already written its artifact by the time it gets here.
 *
 * @author Ivan Rodriguez
 */
public class ProcessStoreCandidateStore implements CandidateStore {

	private final ProcessStore store;

	public ProcessStoreCandidateStore(ProcessStore store) {
		this.store = store;
	}

	@Override
	public void save(AgentSpec candidate) {
		this.store.savePackWhenConfigured(candidate);
	}

}
