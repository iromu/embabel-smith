/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.util.List;

/**
 * The requested goal name is not exported by any pack in the source. The available goal
 * names are carried so the error can list them.
 *
 * @author Ivan Rodriguez
 */
public class GoalNotFoundException extends RuntimeException {

	private final String goalName;

	private final List<String> availableGoals;

	public GoalNotFoundException(String goalName, List<String> availableGoals) {
		super("No goal named '" + goalName + "' is exported by the source. Available goals: " + availableGoals);
		this.goalName = goalName;
		this.availableGoals = List.copyOf(availableGoals);
	}

	public String goalName() {
		return this.goalName;
	}

	public List<String> availableGoals() {
		return this.availableGoals;
	}

}
