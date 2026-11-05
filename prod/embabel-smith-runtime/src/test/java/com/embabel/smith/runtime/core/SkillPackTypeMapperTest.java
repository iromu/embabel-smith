package com.embabel.smith.runtime.core;

import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.SkillPack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillPackTypeMapperTest {

    private final SkillPackTypeMapper mapper = new SkillPackTypeMapper();

    private static SkillPack packWithTypes() {
        DynamicTypeDef topicBrief = new DynamicTypeDef("TopicBrief", "The extracted topic",
                List.of(new PropertyDef("topic", "string", "What to explain")), List.of(), true);
        return new SkillPack(null, null, List.of(), List.of(topicBrief));
    }

    @Test
    void mapsPackTypesToDynamicTypes() {
        DynamicType type = mapper.toDynamicTypes(packWithTypes()).get(0);

        assertThat(type.getName()).isEqualTo("TopicBrief");
        assertThat(type.getDescription()).isEqualTo("The extracted topic");
        assertThat(type.getOwnProperties()).extracting(com.embabel.agent.core.PropertyDefinition::getName)
                .containsExactly("topic");
    }

    @Test
    void allRuntimeTypesCombineBuiltinsAndPackTypes() {
        List<DomainType> types = mapper.allRuntimeTypes(packWithTypes());

        assertThat(types).extracting(DomainType::getName)
                .containsExactlyInAnyOrder(
                        "com.embabel.agent.domain.io.UserInput",
                        "com.embabel.agent.spec.model.BackgroundMessage",
                        "TopicBrief");
    }
}
