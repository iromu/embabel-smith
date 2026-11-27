/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroupMetadata;
import com.embabel.agent.core.ToolGroupRequirement;
import com.embabel.agent.core.ToolGroupResolution;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MEASURED probe of what a booted {@code embabel-smith-runtime} can actually resolve by
 * tool-group role, with no tool property set. Embabel {@code 1.5.4}
 * ({@code embabel-agent.version} in the root pom; {@code embabel-agent-spec} resolves
 * from {@code embabel-agent-experimental.version} = {@code 1.5.4-SNAPSHOT}).
 *
 * <p>
 * {@code availableToolGroups()} as printed by a plain boot, on Linux, with no tool
 * property set: <pre>
 * [math]
 * </pre>
 *
 * <p>
 * Per-role {@code resolveToolGroup} outcome for the roles this change cares about. Each
 * unresolved role answers {@code No tool group matching role '<role>'}; only {@code math}
 * resolves: <pre>
 * AppleScript  unresolved   file        unresolved   search      unresolved
 * blueprint    unresolved   github      unresolved   security    unresolved
 * chat         unresolved   library     unresolved   shell       unresolved
 * process      unresolved   traces      unresolved   validate    unresolved
 * web          unresolved   math        RESOLVES
 * </pre> {@code AppleScript} is absent on Linux as expected, and {@code file} and
 * {@code shell} resolve to nothing at all — nothing in {@code prod/} publishes them on
 * this checkout, which is the gap group 3 closes.
 *
 * <p>
 * The MCP flip — the same boot with
 * {@code spring.ai.mcp.client.stdio.connections.docker-mcp.command} set and nothing else
 * changed — registers: <pre>
 * [browser_automation, github, maps, math, web]
 * </pre> so machine configuration alone adds {@code browser_automation}, {@code github},
 * {@code maps} and {@code web}, exactly as design D3 predicts:
 * {@code @ConditionalOnMcpConnection} keys off the presence of the property, not a
 * credential check. {@code web} is already inside {@code TrustTier.STANDARD}'s allowlist,
 * which is why the set below is an expectation the build defends rather than a snapshot.
 * Neither {@code file} nor {@code shell} is claimed by an upstream group in either boot,
 * so group 3 has both names free at the bean level. They are not free at the property
 * level, though: a mutation check confirmed that
 * {@code embabel.agent.platform.tools.includes.shell.tools=anything} — upstream's own
 * map, empty by default — registers a group under {@code shell} in an otherwise untouched
 * boot, which is the D10 two-sources-claim-one-role case measured rather than assumed.
 *
 * <p>
 * {@code math} is the one role an untouched runtime already answers with. It is not this
 * repo's choice: it is upstream's own {@code ToolGroupsConfiguration.mathToolGroup()}
 * bean, conditional only on {@code McpSyncClient} being on the classpath, which
 * {@code spring-ai-starter-mcp-client} (a non-optional dependency of
 * {@code embabel-agent-api}) makes true. That single entry is what falsifies "nothing is
 * on by default", which is why the set below is pinned rather than described.
 *
 * <p>
 * The builder's six published roles ({@code validate}, {@code process},
 * {@code blueprint}, {@code library}, {@code security}, {@code traces}) are probed here
 * too, and none of them resolves — measured. The reason is packaging, not a silent
 * registration failure: {@code embabel-smith-builder} is not a dependency of this module
 * at all, so its six {@code @Configuration} classes are not on this classpath. The rest
 * of this paragraph is read from source, not booted: all six are unconditional (a grep
 * for {@code @Conditional}, {@code @Profile}, {@code @Import} and {@code @Lazy} across
 * {@code com.embabel.smith.builder.tools} comes back empty), each publishes one
 * {@code @Bean ToolGroup(<collaborator>)}, and the only thing that reaches them is
 * {@code SmithBuilderApplication}'s {@code @ComponentScan(basePackages =
 * "com.embabel.smith.builder")}; there is no builder {@code AutoConfiguration.imports}
 * ({@code prod/embabel-smith-builder/src/main/resources/META-INF/spring} does not exist).
 * So a context that component-scans the builder reaches all six equally, and the earlier
 * probe's "only {@code validate} resolved" was a property of that probe's context rather
 * than evidence of five classes that failed to load. The question this boot can settle —
 * whether a group this repo publishes can silently fail to appear — it answers the same
 * way for both halves: it does not appear unless a bean of that type is in the context,
 * and {@code availableToolGroups()} says so in one line.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=test",
		"OPENAI_CUSTOM_MODELS=test-model", "embabel.models.default-llm=test-model" })
class ResolvedToolGroupsTest {

	/**
	 * The roles a runtime boot resolves with no tool property set at all. Pinned so a
	 * platform or addon change that registers an extra file-, command- or network-backed
	 * group fails the build instead of quietly widening what a pack can reach.
	 * <p>
	 * {@code math} is upstream's own bean (see the class note), not a default this repo
	 * chose. Everything else in this repo's vocabulary — {@code file}, {@code shell} —
	 * arrives from the coding addon in group 3, which does not exist yet.
	 */
	private static final List<String> PINNED_DEFAULT_ROLES = List.of("math");

	/**
	 * The roles the probe asks about by name: the nine this change names, plus the five
	 * builder roles not already in that list ({@code validate} is in both).
	 */
	private static final List<String> PROBED_ROLES = List.of("file", "shell", "search", "web", "math", "chat",
			"AppleScript", "validate", "github", "process", "blueprint", "library", "security", "traces");

	@Autowired
	private AgentPlatform platform;

	@Test
	void printsAndResolvesEveryNamedRole() {
		List<String> available = availableRoles(this.platform);
		System.out.println("[toolgroups] availableToolGroups() = " + available);
		Map<String, String> outcomes = resolveRoles(this.platform, PROBED_ROLES);
		outcomes.forEach((role, outcome) -> System.out.println("[toolgroups]   " + role + " -> " + outcome));
	}

	@Test
	void pinsTheRolesARuntimeResolvesWithNothingConfigured() {
		assertThat(availableRoles(this.platform)).containsExactlyInAnyOrderElementsOf(PINNED_DEFAULT_ROLES);
	}

	@Test
	void resolvingARoleOutsideThePinnedSetReportsItAsUnresolved() {
		Map<String, String> outcomes = resolveRoles(this.platform, PROBED_ROLES);
		for (String role : PROBED_ROLES) {
			assertThat(outcomes.get(role)).as("role '%s'", role)
				.startsWith(PINNED_DEFAULT_ROLES.contains(role) ? "resolves" : "unresolved");
		}
	}

	/**
	 * The same boot with an MCP connection configured by machine property alone. Design
	 * D3 claims {@code @ConditionalOnMcpConnection} keys off the <em>presence</em> of the
	 * property rather than a credential check, so this boot gains {@code web},
	 * {@code maps}, {@code github} and {@code browser_automation} with no repo change —
	 * which is the reason the pinned set above is an expectation rather than a snapshot.
	 */
	@Nested
	@SpringBootTest(properties = { "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=test",
			"OPENAI_CUSTOM_MODELS=test-model", "embabel.models.default-llm=test-model",
			// Presence is the whole point of the flip; /bin/true keeps the stdio client
			// from spawning a real server that would have to be killed with the test JVM.
			"spring.ai.mcp.client.stdio.connections.docker-mcp.command=/bin/true" })
	class WithAnMcpConnectionConfigured {

		/**
		 * The roles the MCP-backed {@code ToolGroupsConfiguration} beans add by property.
		 */
		private static final List<String> MCP_ADDED_ROLES = List.of("browser_automation", "github", "maps", "web");

		@Autowired
		private AgentPlatform platform;

		@Test
		void recordsWhichGroupsThePropertyAdds() {
			List<String> available = availableRoles(this.platform);
			System.out.println("[toolgroups][mcp] availableToolGroups() = " + available);
			System.out.println("[toolgroups][mcp] added over the pinned default set = "
					+ available.stream().filter((role) -> !PINNED_DEFAULT_ROLES.contains(role)).toList());
			Map<String, String> outcomes = resolveRoles(this.platform, PROBED_ROLES);
			outcomes.forEach((role, outcome) -> System.out.println("[toolgroups][mcp]   " + role + " -> " + outcome));

			assertThat(available).containsExactlyInAnyOrderElementsOf(
					Stream.concat(PINNED_DEFAULT_ROLES.stream(), MCP_ADDED_ROLES.stream()).toList());
		}

	}

	/**
	 * The roles of the groups the resolver knows about, sorted for a stable print.
	 * @param platform the booted platform whose resolver to read
	 * @return the registered role names
	 */
	private static List<String> availableRoles(AgentPlatform platform) {
		return platform.getToolGroupResolver()
			.availableToolGroups()
			.stream()
			.map(ToolGroupMetadata::getRole)
			.sorted()
			.toList();
	}

	/**
	 * What {@code resolveToolGroup} answers for each role, as the text the run would see.
	 * @param platform the booted platform whose resolver to ask
	 * @param roles the roles to ask about
	 * @return role name to either {@code resolves} or {@code unresolved: <platform
	 * message>}, in role order
	 */
	private static Map<String, String> resolveRoles(AgentPlatform platform, List<String> roles) {
		Map<String, String> outcomes = new TreeMap<>();
		for (String role : roles) {
			// The shape upstream's own call sites use: ToolGroupRequirement(role), whose
			// Kotlin defaults are an empty requiredToolNames and a null terminationScope.
			// The resolver matches on role alone, so neither default can change the
			// answer.
			ToolGroupResolution resolution = platform.getToolGroupResolver()
				.resolveToolGroup(new ToolGroupRequirement(role, Set.of(), null));
			outcomes.put(role, (resolution.getResolvedToolGroup() != null) ? "resolves"
					: "unresolved: " + resolution.getFailureMessage());
		}
		return outcomes;
	}

}
