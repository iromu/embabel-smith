/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * One executed step of a run (mirrors {@code TraceRecord.StepRecord}).
 *
 * @param name the step name.
 * @param status the step status.
 * @param durationMs the step duration in milliseconds.
 * @param outputExcerpt a short excerpt of the step output.
 * @author Ivan Rodriguez
 */
public record StepInfo(String name, String status, long durationMs, String outputExcerpt) {

	public static StepInfo of(com.embabel.smith.common.trace.TraceRecord.StepRecord step) {
		return new StepInfo(step.name(), step.status(), step.durationMs(), step.outputExcerpt());
	}
}
