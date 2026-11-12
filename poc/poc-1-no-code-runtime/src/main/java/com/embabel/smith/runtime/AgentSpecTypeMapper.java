package com.embabel.smith.runtime;

import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.PropertyDefinition;
import com.embabel.agent.core.ValuePropertyDefinition;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.AgentSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the agent spec's pure-data {@link DynamicTypeDef}s onto Embabel {@link DynamicType}s so the
 * platform's data dictionary knows the intermediate types a agent spec introduces.
 *
 * <p>This is the seam between the format owner ({@code smith-common}) and the Embabel runtime:
 * the format stays a plain data model, and only the runtime translates it into framework types.
 */
public class AgentSpecTypeMapper {

    /** The pack's intermediate types, as Embabel {@link DynamicType}s. */
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
     * The full set of domain types the runtime should make available: the pack's intermediate
     * types plus the built-in I/O types the agent spec's steps and goal reference.
     */
    public List<DomainType> allRuntimeTypes(AgentSpec pack) {
        List<DomainType> types = new ArrayList<>();
        types.add(new com.embabel.agent.core.JvmType(com.embabel.agent.domain.io.UserInput.class));
        types.add(new com.embabel.agent.core.JvmType(com.embabel.agent.spec.model.BackgroundMessage.class));
        types.addAll(toDynamicTypes(pack));
        return types;
    }
}
