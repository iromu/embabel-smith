/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A run request (the Run pane): the spec source, the exported goal to run, the input, an
 * optional planner override, the trust tier, and the LLM to run the steps through. A
 * blank goal runs the source's single exported goal; a blank model leaves each step on
 * the model its own spec names.
 *
 * @param source the spec source to run.
 * @param goal the exported goal to run (blank runs the source's single exported goal).
 * @param input the input bound to the run.
 * @param planner an optional planner override.
 * @param trustTier the trust tier to run under.
 * @param model the LLM every action step runs on (blank uses the configured default).
 * @author Ivan Rodriguez
 */
public record RunRequest(String source, String goal, String input, String planner, String trustTier, String model) {
}
