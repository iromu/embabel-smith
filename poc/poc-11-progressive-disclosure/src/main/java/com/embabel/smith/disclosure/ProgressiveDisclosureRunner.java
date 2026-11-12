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

package com.embabel.smith.disclosure;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Measures the context cost of progressive vs. eager disclosure for a spec that ships a
 * bundled reference (POC 11). Pure and offline.
 *
 * @author Ivan Rodriguez
 */
public class ProgressiveDisclosureRunner {

	private final PlanContextMeter meter = new PlanContextMeter();

	/**
	 * Measures the context costs for a spec with a bundled reference.
	 * @param pack the agent spec to measure
	 * @param reference the bundled reference text
	 * @return the measured context costs
	 */
	public ContextCost measure(AgentSpec pack, String reference) {
		return new ContextCost(this.meter.planContextChars(pack), this.meter.progressivePromptChars(pack),
				this.meter.eagerPromptChars(pack, reference), reference.length());
	}

	/**
	 * The measured context costs for a spec with a bundled reference of
	 * {@code referenceChars}.
	 *
	 * @param planContextChars bounded, independent of the reference (what the planner
	 * loads)
	 * @param progressivePromptChars base prompt only (reference fetched on demand)
	 * @param eagerPromptChars base prompt + inlined reference
	 * @param referenceChars size of the bundled reference
	 */
	public record ContextCost(int planContextChars, int progressivePromptChars, int eagerPromptChars,
			int referenceChars) {

		/**
		 * How many times larger the eager execution context is than the progressive one.
		 * @return the eager-to-progressive context size ratio (0.0 when progressive is
		 * zero)
		 */
		public double eagerToProgressiveRatio() {
			return (this.progressivePromptChars != 0) ? (double) this.eagerPromptChars / this.progressivePromptChars
					: 0.0;
		}
	}

}
