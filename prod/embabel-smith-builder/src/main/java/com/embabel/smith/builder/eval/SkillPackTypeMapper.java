package com.embabel.smith.builder.eval;

import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.PropertyDefinition;
import com.embabel.agent.core.ValuePropertyDefinition;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.SkillPack;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the skill-pack's pure-data {@link DynamicTypeDef}s onto Embabel {@link DynamicType}s so the
 * platform's data dictionary knows the intermediate types a skill-pack introduces. Extracted from
 * the runtime (POC 1) so the builder's endpoint-gated eval can deploy the pack it just built.
 */
public class SkillPackTypeMapper {

    /** The pack's intermediate types, as Embabel {@link DynamicType}s. */
    public List<DynamicType> toDynamicTypes(SkillPack pack) {
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
     * The full set of domain types the runtime should make available: the built-in I/O types plus
     * the pack's intermediate types.
     */
    public List<DomainType> allRuntimeTypes(SkillPack pack) {
        List<DomainType> types = new ArrayList<>();
        types.add(new JvmType(UserInput.class));
        types.add(new JvmType(BackgroundMessage.class));
        types.addAll(toDynamicTypes(pack));
        return types;
    }
}
