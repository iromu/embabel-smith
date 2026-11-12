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

package com.embabel.smith.runtime.core;

import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.PropertyDefinition;
import com.embabel.agent.core.ValuePropertyDefinition;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.PropertyDef;

/**
 * Maps the agent spec's pure-data {@link DynamicTypeDef}s onto Embabel
 * {@link DynamicType}s so the platform's data dictionary knows the intermediate types a
 * agent spec introduces.
 *
 * <p>
 * This is the seam between the format owner ({@code smith-common}) and the Embabel
 * runtime: the format stays a plain data model, and only the runtime translates it into
 * framework types.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecTypeMapper {

	/**
	 * The pack's intermediate types, as Embabel {@link DynamicType}s.
	 * @param pack the agent spec to map
	 * @return the pack's intermediate types
	 */
	public List<DynamicType> toDynamicTypes(AgentSpec pack) {
		List<DynamicType> types = new ArrayList<>();
		for (DynamicTypeDef def : pack.types()) {
			types.add(toDynamicType(def));
		}
		return types;
	}

	public DynamicType toDynamicType(DynamicTypeDef def) {
		List<PropertyDefinition> properties = new ArrayList<>();
		for (PropertyDef property : def.ownProperties()) {
			properties.add(new ValuePropertyDefinition(property.name(), property.type()));
		}
		return new DynamicType(def.name(), def.description(), properties, List.of(), def.isCreationPermitted());
	}

	/**
	 * The full set of domain types the runtime should make available: the built-in I/O
	 * types plus the pack's intermediate types.
	 * @param pack the agent spec to map
	 * @return the full set of domain types
	 */
	public List<DomainType> allRuntimeTypes(AgentSpec pack) {
		List<DomainType> types = new ArrayList<>();
		types.add(new JvmType(UserInput.class));
		types.add(new JvmType(BackgroundMessage.class));
		types.addAll(toDynamicTypes(pack));
		return types;
	}

}
