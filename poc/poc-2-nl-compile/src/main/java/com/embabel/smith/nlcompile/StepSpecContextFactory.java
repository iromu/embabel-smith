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

package com.embabel.smith.nlcompile;

import java.util.List;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.model.StepSpecContext;

/**
 * Builds the {@link StepSpecContext} the natural-language compiler needs: the platform's
 * data dictionary (the types it may reference) plus the available tool groups, tools and
 * LLM references. The POC agent specs are pure-LLM, so the tool/tool/reference lists are
 * empty.
 *
 * @author Ivan Rodriguez
 */
public class StepSpecContextFactory {

	public StepSpecContext forAgent(String agentName, DataDictionary dataDictionary) {
		return new StepSpecContext(agentName, dataDictionary, List.of(), List.of(), List.of());
	}

}
