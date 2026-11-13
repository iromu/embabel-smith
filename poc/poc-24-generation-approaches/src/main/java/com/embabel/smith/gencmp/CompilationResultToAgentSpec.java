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

package com.embabel.smith.gencmp;

import java.util.List;

import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.NamedPropertyDefinition;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.model.GoalSpec;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.PropertyDef;

/**
 * The seam between Embabel's NL-compiler output ({@link AgentCompilationResult}) and the shared
 * agent spec format ({@link AgentSpec}) — a minimal version of the builder's
 * {@code DraftToAgentSpec}. It maps the compiler's actions, goal and intermediate types and
 * force-exports the goal (a freshly compiled agent's goal is callable by definition). The
 * builder's further goal-output normalisation is intentionally omitted so this POC reports the
 * compiler's raw output.
 *
 * @author Ivan Rodriguez
 */
public class CompilationResultToAgentSpec {

	public AgentSpec toAgentSpec(AgentCompilationResult result, String agentName) {
		List<ActionStepSpec> steps = result.getActions().stream().map(this::toActionStep).toList();
		GoalStepSpec goal = (result.getGoal() != null) ? toGoal(result.getGoal()) : null;
		List<DynamicTypeDef> types = result.getIntermediateTypes().stream().map(this::toType).toList();
		return new AgentSpec(new AgentSpecManifest(agentName, "1.0.0"), goal, steps, types);
	}

	private ActionStepSpec toActionStep(PromptedActionSpec spec) {
		return new ActionStepSpec(ActionStepSpec.STEP_TYPE, spec.getName(), spec.getDescription(),
				List.copyOf(spec.getInputTypeNames()), spec.getOutputTypeName(), spec.getPrompt(), spec.getPre(),
				spec.getPost(), spec.getCost(), spec.getValue(), spec.getCanRerun(), spec.getNullable(),
				spec.getToolGroups());
	}

	private GoalStepSpec toGoal(GoalSpec spec) {
		return GoalStepSpec.exportedGoal(spec.getName(), spec.getDescription(), spec.getOutputTypeName());
	}

	private DynamicTypeDef toType(DynamicType type) {
		List<PropertyDef> properties = type.getOwnProperties()
			.stream()
			.map((p) -> new PropertyDef(p.getName(), (p instanceof NamedPropertyDefinition named) ? named.getType()
					: null, p.getDescription()))
			.toList();
		List<String> parents = type.getParents().stream().map(DomainType::getName).toList();
		return new DynamicTypeDef(type.getName(), type.getDescription(), properties, parents, true);
	}

}
