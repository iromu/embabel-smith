/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.List;

import com.embabel.agent.core.ToolGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The case the boot report exists for: a role that arrived with no switch of ours
 * anywhere in the deployment.
 *
 * <p>
 * The real instance of this is {@code math}, which is not this repo's choice at all — it
 * is upstream's own {@code ToolGroupsConfiguration.mathToolGroup()} bean, present because
 * {@code spring-ai-starter-mcp-client} puts {@code McpSyncClient} on the classpath. That
 * configuration cannot be imported into this module's test classpath (this addon depends
 * on no Embabel auto-configuration at all, deliberately), so the bean below stands in for
 * it under upstream's own bean name. What is under test is the report's attribution rule
 * — a group this module's beans did not produce is not reported as ours — and the
 * stand-in exercises exactly that rule. The role that answers in a real runtime boot is
 * asserted where the real bean is on the classpath: {@code ResolvedToolGroupsTest} in the
 * runtime module.
 *
 * @author Ivan Rodriguez
 */
class ForeignGroupAttributionTest {

	/**
	 * The boot.
	 * <p>
	 * The annotations sit on this class rather than on the outer one, which is the shape
	 * {@code UpstreamIncludesCollisionTest} already uses: a {@code @Nested} class
	 * carrying both {@code @SpringBootTest} and {@code @Import} composes the imported
	 * configuration with this addon's own auto-configuration, and the assertions below
	 * need both halves — the foreign group present <em>and</em> the addon having
	 * registered its own. Collected from the outer class instead, the addon's beans were
	 * absent from the boot, which fails as an unsatisfied field on the test rather than
	 * as anything an operator would recognise.
	 */
	@SpringBootTest(properties = "spring.main.web-application-type=none")
	@org.junit.jupiter.api.Nested
	@DisplayName("a context holding one group of ours and one of theirs")
	@Import(ForeignGroupAttributionTest.SomebodyElsesGroups.class)
	class OneOfOursOneOfTheirs {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private CodingToolsBootReport report;

		@Test
		@DisplayName("a group we did not register is in the registry, which is what makes the claim non-vacuous")
		void foreignGroupIsReallyThere() {
			assertThat(this.context.getBeansOfType(ToolGroup.class)).as("ours plus theirs").hasSize(2);
			assertThat(ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class))).contains("math",
					CodingToolGroups.FILE_ROLE);
		}

		@Test
		@DisplayName("and the report says it is not ours, while still owning its own")
		void reportAttributesIt() {
			assertThat(this.report.notOurs()).containsExactly("math");
			assertThat(this.report.registeredByDefault()).containsExactly(CodingToolGroups.FILE_ROLE);
			assertThat(this.report.describe()).contains("not ours [math]");
		}

		@Test
		@DisplayName("presence without a switch is presence: no stray switch is invented for it")
		void presenceIsNotReportedAsASwitch() {
			assertThat(this.report.unknownSwitches()).isEmpty();
			assertThat(this.report.switchedOn()).isEmpty();
		}

	}

	/**
	 * A group authored by somebody else, standing in for the upstream configuration this
	 * module's classpath cannot reach.
	 */
	@Configuration(proxyBeanMethods = false)
	static class SomebodyElsesGroups {

		/**
		 * Upstream's bean name, on purpose: the report attributes by bean name, so the
		 * stand-in has to be indistinguishable from the real thing in the one way the
		 * report can see.
		 * @return a group answering {@code math}
		 */
		@Bean
		ToolGroup mathToolGroup() {
			return CodingToolGroups.ofTools("math", "Arithmetic, from upstream.", List.of());
		}

	}

}
