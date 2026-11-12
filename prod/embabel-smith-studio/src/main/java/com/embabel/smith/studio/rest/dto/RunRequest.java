/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A run request (the Run pane): the spec source, the exported goal to run, the input, an
 * optional planner override, and the trust tier. A blank goal runs the source's single
 * exported goal.
 *
 * @param source the spec source to run.
 * @param goal the exported goal to run (blank runs the source's single exported goal).
 * @param input the input bound to the run.
 * @param planner an optional planner override.
 * @param trustTier the trust tier to run under.
 * @author Ivan Rodriguez
 */
public record RunRequest(String source, String goal, String input, String planner, String trustTier) {
}
