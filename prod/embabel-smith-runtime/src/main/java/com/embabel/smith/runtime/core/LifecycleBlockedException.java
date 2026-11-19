/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

/**
 * The run was blocked because the goal is exported only by a pack the lifecycle state
 * machine put out of the library: a pack whose {@code lifecycle.yml} declares
 * {@code retired} is not ingested, so its goal is not deployed.
 *
 * <p>
 * This is deliberately <em>not</em> a {@link GoalNotFoundException}. Retirement makes the
 * goal genuinely absent, so a goal-not-found error would be indistinguishable from a typo
 * in the name — and the improvement loop reads failures by their message prefix, so it
 * would treat a policy outcome as a spec defect and try to refine prompts against it. The
 * block carries the trust-tier convention instead: a stable message prefix naming the
 * lifecycle block, plus the retired pack's name. The documented revival is an explicit
 * rebuild, which republishes the pack and leaves {@code retired}; there is no runtime
 * override.
 *
 * @author Ivan Rodriguez
 */
public class LifecycleBlockedException extends RuntimeException {

	/**
	 * The stable prefix of the message — the string the studio matches, as it matches the
	 * trust-tier prefix.
	 */
	public static final String PREFIX = "Run blocked by lifecycle state";

	private final String packName;

	private final String goalName;

	/**
	 * Blocks a goal that only the named retired pack exported.
	 * @param packName the retired pack that alone exported the goal
	 * @param goalName the goal the run asked for
	 */
	public LifecycleBlockedException(String packName, String goalName) {
		super(PREFIX + ": goal '" + goalName + "' is exported only by the retired pack '" + packName
				+ "', which was not deployed. Retirement takes a pack out of the library; the documented revival is an"
				+ " explicit rebuild that republishes it.");
		this.packName = packName;
		this.goalName = goalName;
	}

	/**
	 * The retired pack that alone exported the goal.
	 * @return the pack's name.
	 */
	public String packName() {
		return this.packName;
	}

	/**
	 * The goal the run asked for.
	 * @return the goal name.
	 */
	public String goalName() {
		return this.goalName;
	}

}
