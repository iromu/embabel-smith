/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.eval.AgentSpecInvoker;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.common.model.AgentSpec;

/**
 * Answers each frozen prompt from a fixed table and records every prompt it was asked, so
 * a test can distinguish "this task was not scored" from "this task was scored and
 * failed". No model call.
 *
 * @author Ivan Rodriguez
 */
final class RecordingInvokerFactory implements InvokerFactory {

	private final FrozenTaskSet tasks;

	private final Map<String, String> answers;

	private final List<String> asked = new ArrayList<>();

	RecordingInvokerFactory(FrozenTaskSet tasks, Map<String, String> answers) {
		this.tasks = tasks;
		this.answers = answers;
	}

	@Override
	public AgentSpecInvoker invokerFor(AgentSpec pack) {
		return (input) -> {
			this.asked.add(input);
			return this.tasks.tasks()
				.stream()
				.filter((task) -> task.prompt().equals(input))
				.map((task) -> this.answers.getOrDefault(task.id(), ""))
				.findFirst()
				.orElse("");
		};
	}

	/**
	 * The prompts put to this candidate, in the order they were asked.
	 * @return every prompt the gate and the referee asked this candidate
	 */
	List<String> asked() {
		return List.copyOf(this.asked);
	}

}
