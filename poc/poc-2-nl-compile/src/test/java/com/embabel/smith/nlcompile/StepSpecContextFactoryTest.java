package com.embabel.smith.nlcompile;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.model.StepSpecContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the NL-compile wiring (offline): the {@link StepSpecContext} handed to the
 * compiler is bound to the platform's data dictionary and the pure-LLM
 * tool/tool/reference lists are empty. The actual {@code compileAgent} call is
 * endpoint-gated.
 */
class StepSpecContextFactoryTest {

	private final StepSpecContextFactory factory = new StepSpecContextFactory();

	@Test
	void buildsAContextBoundToTheProvidedDataDictionary() {
		DataDictionary dictionary = DataDictionary.fromDomainTypes("test-types", List.of());

		StepSpecContext context = factory.forAgent("draft-agent", dictionary);

		assertThat(context.getName()).isEqualTo("draft-agent");
		assertThat(context.getDataDictionary()).isSameAs(dictionary);
		assertThat(context.getToolGroups()).isEmpty();
		assertThat(context.getTools()).isEmpty();
		assertThat(context.getAvailableReferences()).isEmpty();
	}

}
