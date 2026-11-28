/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.List;
import java.util.Map;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.spi.config.spring.ToolGroupsConfiguration;
import com.embabel.agent.spi.config.spring.ToolGroupsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-sources-one-role case, provoked from a property.
 *
 * <p>
 * {@code ToolGroupsConfiguration.includedToolGroups()} is an unconditional {@code @Bean}
 * that walks {@code ToolGroupsProperties.includes} — a relaxed-bound
 * {@code Map<String, GroupConfig>} — and calls {@code toToolGroup(key, config)} per
 * entry, logging "Exposing tool group {role}". The map key becomes the role. So
 * {@code embabel.agent.platform.tools.includes.file.tools=anything} builds a second group
 * whose role is {@code file}, authored by upstream rather than by this addon, in a boot
 * that already has this addon's {@code file} bean. Nothing about that is inferred from a
 * search: both cases below are boots, and the second one is the negative that says where
 * the mechanism lives.
 *
 * <p>
 * <b>Which claimant a step actually gets.</b> This addon's, and the reason is upstream's
 * own {@code AgentPlatformConfiguration.toolGroupResolver}: it builds its list from the
 * individual {@code ToolGroup} beans first and appends the contents of every
 * {@code List<ToolGroup>} provider bean after them — which is where
 * {@code includedToolGroups} lands — and
 * {@code RegistryToolGroupResolver.resolveToolGroup} then takes the <em>first</em> entry
 * whose role matches. So the MCP-backed group is shadowed rather than absent, which is
 * why the report names it instead of pretending it is gone. That ordering is read from
 * the 1.5.4 source; the resolution itself, with a platform on the classpath, is asserted
 * from the runtime module, where an {@code AgentPlatform} exists.
 *
 * <p>
 * <b>Why the report's shadow claim is not a property lookup.</b> The second boot carries
 * the very same property with nothing on the classpath to build a group from it, and the
 * report stays silent there. So the claim is read from groups that exist, and the
 * environment is consulted only to say who built one — which is the difference between a
 * report an operator can act on and one that repeats configuration back at them.
 *
 * @author Ivan Rodriguez
 */
class UpstreamIncludesCollisionTest {

	@SpringBootTest(properties = { "spring.main.web-application-type=none",
			"embabel.agent.platform.tools.includes.file.tools=anything" })
	@org.junit.jupiter.api.Nested
	@DisplayName("with upstream's configuration present")
	@Import(UpstreamIncludesCollisionTest.UpstreamGroups.class)
	class WithUpstreamConfiguration {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private CodingToolsBootReport report;

		@Test
		@DisplayName("the property registers a second group claiming role file")
		void secondClaimantAppears() {
			assertThat(this.context.getBeanNamesForType(ToolGroup.class)).as("our own group").contains("fileToolGroup");
			List<?> included = this.context.getBean("includedToolGroups", List.class);
			assertThat(included).as("upstream built one group from the includes map").hasSize(1);
			List<String> upstreamRoles = included.stream().map(UpstreamIncludesCollisionTest::roleOf).toList();
			assertThat(upstreamRoles).containsExactly(CodingToolGroups.FILE_ROLE);
			Map<String, ToolGroup> ours = this.context.getBeansOfType(ToolGroup.class);
			assertThat(ours.values().stream().map(UpstreamIncludesCollisionTest::roleOf))
				.as("both claimants answer the same role")
				.contains(CodingToolGroups.FILE_ROLE);
		}

		@Test
		@DisplayName("the report names the claimant our group shadows, and what it is backed by")
		void reportNamesTheShadowedSource() {
			assertThat(this.report.shadowedByUs()).as("our group answers 'file', and so does upstream's")
				.containsKey(CodingToolGroups.FILE_ROLE);
			assertThat(this.report.shadowedByUs().get(CodingToolGroups.FILE_ROLE))
				.startsWith("embabel.agent.platform.tools.includes.file")
				.contains("MCP-backed");
			// Named in the shadowing clause specifically. The line carries the role in
			// other clauses too - which group registered, which source decided it - so a
			// bare "file=" would be satisfied by the report saying anything at all about
			// the group.
			assertThat(this.report.describe()).contains("ours shadowing {file=");
		}

	}

	@SpringBootTest(properties = { "spring.main.web-application-type=none",
			"embabel.agent.platform.tools.includes.file.tools=anything" })
	@org.junit.jupiter.api.Nested
	@DisplayName("without it")
	class WithoutUpstreamConfiguration {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private CodingToolsBootReport report;

		@Test
		@DisplayName("the same property registers nothing, so the collision is upstream's configuration")
		void propertyAloneRegistersNoGroup() {
			assertThat(this.context.getBeanNamesForType(ToolGroup.class)).containsExactly("fileToolGroup");
			assertThat(this.context.containsBean("includedToolGroups")).isFalse();
		}

		@Test
		@DisplayName("and nothing is shadowed here, which is what makes the other case's claim a claim")
		void nothingIsShadowed() {
			// The property is present in this boot too — that is what the pair is for. So
			// this rules out the cheap implementation: a report that read upstream's
			// includes
			// map and stopped there would call this a shadow, and no run can observe one.
			assertThat(
					this.context.getEnvironment().containsProperty("embabel.agent.platform.tools.includes.file.tools"))
				.as("nothing built a group from it, which is not the same as the property being missing")
				.isTrue();
			assertThat(this.report.shadowedByUs()).isEmpty();
			// The shadowing clause specifically, for the same reason the positive case
			// names it: the role appears elsewhere in the line - it registered, something
			// decided it - and a bare "file=" would pass on a report that said nothing
			// about any collision.
			assertThat(this.report.describe()).doesNotContain("ours shadowing {file");
		}

	}

	/**
	 * Upstream's own group-authoring configuration, with its properties bound — the
	 * configuration a boot that pulls the Embabel starter already has.
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ToolGroupsProperties.class)
	@Import(ToolGroupsConfiguration.class)
	static class UpstreamGroups {

	}

	private static String roleOf(Object group) {
		return ((ToolGroup) group).getMetadata().getRole();
	}

}
