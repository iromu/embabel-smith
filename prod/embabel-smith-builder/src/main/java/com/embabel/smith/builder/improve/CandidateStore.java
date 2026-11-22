/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.improve;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Records a published candidate so the version the destination replaced stays addressable
 * (change: publish-keeps-versions, decision 7). The builder owns the seam and nothing
 * else: the loop's publish policy calls it once, after the artifact is written, and reads
 * nothing back, which is why the method returns nothing and no persistence type appears
 * here. Implementations live in the host application — the studio's
 * {@code ProcessStoreCandidateStore} delegates to its own store and does nothing while no
 * database is bound.
 *
 * @author Ivan Rodriguez
 */
public interface CandidateStore {

	/**
	 * Records the candidate that was just published, at the version its artifact carries.
	 * @param candidate the published candidate pack
	 */
	void save(AgentSpec candidate);

}
