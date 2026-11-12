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

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * The request for the curation re-eval: which spec to re-score, and (optionally) the eval
 * tasks to run against it. When no tasks are supplied, {@link #tasksOrDefault()} falls
 * back to a small default set (the same defaults the eval/refinement pane uses).
 *
 * @param specName the name of the spec to re-score
 * @param tasks the eval tasks to run, or null to use the defaults
 * @author Ivan Rodriguez
 */
public record CurateRequest(String specName, List<EvalTask> tasks) {

	public List<EvalTask> tasksOrDefault() {
		return (this.tasks == null || this.tasks.isEmpty()) ? EvalTask.DEFAULTS : this.tasks;
	}
}
