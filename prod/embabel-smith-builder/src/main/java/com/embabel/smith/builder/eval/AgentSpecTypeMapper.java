/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * agent spec introduces. Extracted from the runtime (POC 1) so the builder's
 * endpoint-gated eval can deploy the pack it just built.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecTypeMapper {

	/**
	 * The pack's intermediate types, as Embabel {@link DynamicType}s.
	 * @param pack the agent spec whose intermediate types are mapped
	 * @return the pack's intermediate types as Embabel dynamic types
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
	 * @param pack the agent spec whose intermediate types are included
	 * @return the full set of runtime domain types
	 */
	public List<DomainType> allRuntimeTypes(AgentSpec pack) {
		return allRuntimeTypes(List.of(pack));
	}

	/**
	 * The domain types a composed run needs: the built-in I/O types plus every pack's
	 * intermediate types, deduplicated by name — the shared type two agents hand off
	 * through is declared by both, and the dictionary holds it once.
	 * @param packs the agent specs whose intermediate types are included
	 * @return the full set of runtime domain types
	 */
	public List<DomainType> allRuntimeTypes(List<AgentSpec> packs) {
		List<DomainType> types = new ArrayList<>();
		types.add(new JvmType(UserInput.class));
		types.add(new JvmType(BackgroundMessage.class));
		Set<String> seen = new HashSet<>();
		for (AgentSpec pack : packs) {
			for (DynamicType type : toDynamicTypes(pack)) {
				if (seen.add(type.getName())) {
					types.add(type);
				}
			}
		}
		return types;
	}

}
