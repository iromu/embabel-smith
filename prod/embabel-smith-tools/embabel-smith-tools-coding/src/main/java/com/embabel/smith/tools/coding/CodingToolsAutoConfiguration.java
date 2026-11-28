/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.List;
import java.util.Map;

import com.embabel.agent.core.ToolGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * The coding addon's one entry point: it publishes the {@code file} and {@code shell}
 * tool groups into whatever process puts this jar on its classpath, with no host source
 * change and no host rebuild.
 *
 * <p>
 * <b>Registration is not reach.</b> A group bean only says the process <em>can</em>
 * answer a role. A pack's {@code toolGroups} entry still passes the runtime's trust-tier
 * gate before anything is offered, and a pack that declares nothing is offered nothing.
 * What this class decides is which roles are in the registry at all.
 *
 * <p>
 * <b>The defaults follow a rule, not a list.</b> A group registers by default when the
 * runtime's default trust tier already names its role <em>and</em> a class here backs it.
 * {@code file} satisfies both — {@code file} is in the default tier's vocabulary and the
 * platform ships the tools — so it is on. {@code shell} is named only in the tier's
 * highest band, so it is off until an operator asks for it. That is why presence on a
 * classpath from a transitive dependency cannot widen what a pack can reach: the added
 * group is the one nothing switches on.
 *
 * <p>
 * <b>A switch's caveat.</b> {@code @ConditionalOnProperty} is decided during Spring's AOT
 * pass in a native build, so a default-on group is frozen there. This addon's groups
 * reach a JVM boot today, where the property is read live; the boot report bean below is
 * what makes a frozen build legible rather than surprising.
 *
 * @author Ivan Rodriguez
 */
// Boot 4's @AutoConfiguration is meta-annotated @Configuration(proxyBeanMethods = false),
// and
// the annotation no longer exposes the attribute to restate it.
@AutoConfiguration
@EnableConfigurationProperties(CodingToolsProperties.class)
public class CodingToolsAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(CodingToolsAutoConfiguration.class);

	/**
	 * The reads this deployment answers. Registered whatever the switches say, in the
	 * same spirit as the boot report: a run that registered nothing still has to be able
	 * to say it read nothing, and a host that wants to print what a run touched needs one
	 * object to ask rather than three — the file group and every composed capability that
	 * names a file capability are doors to the same log.
	 * @return the log the published file tools note their reads in
	 */
	@Bean
	public ReadPathLog codingReadPathLog() {
		return new ReadPathLog();
	}

	/**
	 * The file tools, at the configured root. On unless switched off: the role is in the
	 * runtime's default tier vocabulary and the platform ships the tools behind it. What
	 * that group may do to a filesystem is decided here, once: {@code writes} off means
	 * no writer is ever built for it.
	 * @param properties the addon's settings, of which this uses the run root and whether
	 * a writer may exist
	 * @param reads the log this group's reads are noted in
	 * @return the file-tools holder, kept as a bean so what it read is reportable
	 */
	@Bean
	@ConditionalOnProperty(prefix = "smith.tools.file", name = "enabled", havingValue = "true", matchIfMissing = true)
	public CodingFileTools codingFileTools(CodingToolsProperties properties, ReadPathLog reads) {
		return CodingFileTools.at(RunRoot.of(properties.getCoding().getRoot()), properties.getCoding().isWrites(),
				reads);
	}

	/**
	 * The {@code file} group.
	 * @param fileTools the platform's file tools at the run root
	 * @return the group a step reaches by declaring {@code file}
	 */
	@Bean
	@ConditionalOnProperty(prefix = "smith.tools.file", name = "enabled", havingValue = "true", matchIfMissing = true)
	public ToolGroup fileToolGroup(CodingFileTools fileTools) {
		return CodingToolGroups.fileGroup(fileTools);
	}

	/**
	 * The {@code shell} group. Off until asked: {@code shell} is a role of the tier
	 * vocabulary's highest band, so a dependency that arrives by itself must not start
	 * offering a process-spawning tool.
	 * @param properties the addon's settings, of which this uses the run root and the two
	 * bounds on a command
	 * @return the group a step reaches by declaring {@code shell}
	 */
	@Bean
	@ConditionalOnProperty(prefix = "smith.tools.shell", name = "enabled", havingValue = "true")
	public ToolGroup shellToolGroup(CodingToolsProperties properties) {
		CodingToolsProperties.Coding coding = properties.getCoding();
		return CodingToolGroups.shellGroup(RunRoot.of(coding.getRoot()), coding.getCommandTimeout(),
				coding.getMaxOutputChars());
	}

	/**
	 * The roles {@code smith.tools.expose.<role>} composes. Published as one
	 * {@code List<ToolGroup>} bean, which is the channel upstream's own
	 * {@code includedToolGroups} uses: the platform appends the contents of every such
	 * bean after the individual {@code ToolGroup} beans, so a composed role adds names
	 * without displacing the two this addon owns. Empty by default, and empty is the
	 * documented state — nothing here registers a role nobody named.
	 * @param properties the addon's settings, of which this uses the expose map, the run
	 * root, and the two bounds a composed {@code command} capability carries
	 * @param reads the log a composed file capability's reads are noted in, which is the
	 * same log the {@code file} group writes to
	 * @return the composed groups, in role order
	 * @throws IllegalStateException when an entry names a capability outside
	 * {@link ExposedToolGroups#CAPABILITIES}, which refuses the boot rather than
	 * registering a role that answers with nothing
	 */
	@Bean
	public List<ToolGroup> exposedToolGroups(CodingToolsProperties properties, ReadPathLog reads) {
		CodingToolsProperties.Coding coding = properties.getCoding();
		return ExposedToolGroups.groups(properties.getExpose(), RunRoot.of(coding.getRoot()),
				coding.getCommandTimeout(), coding.getMaxOutputChars(), reads);
	}

	/**
	 * The record of which roles arrived and how, plus the switches naming a group this
	 * module does not contain. Registered whatever the switches say, because a run that
	 * registered nothing still has to say why.
	 * <p>
	 * The groups are an input, not an afterthought: the switches say what this module was
	 * asked to do, and only the registry says what is actually answerable — including the
	 * roles that arrived without any switch of ours, {@code math} being the one that
	 * matters. Both channels the platform reads are read here, in the same two shapes:
	 * individual {@code ToolGroup} beans, and the contents of every
	 * {@code List<ToolGroup>} provider bean, which is how upstream's
	 * {@code includedToolGroups} reaches a run. The second channel is read for role names
	 * only — asking an MCP-backed group for its tools would make it shake hands with its
	 * server during start-up, which is the platform's job at execution time and nobody's
	 * here.
	 * @param environment the environment the context was built with
	 * @param registered every {@link ToolGroup} bean in the context, by bean name
	 * @param providerGroups every {@code List<ToolGroup>} bean by bean name, which is the
	 * other way a group reaches the resolver, and the report needs the names to tell its
	 * own composed roles from somebody else's provider
	 * @return the report, already logged
	 */
	@Bean
	public CodingToolsBootReport codingToolsBootReport(Environment environment, Map<String, ToolGroup> registered,
			Map<String, List<ToolGroup>> providerGroups) {
		CodingToolsBootReport report = CodingToolsBootReport.from(environment, registered, providerGroups);
		logger.info("[smith-tools] {}", report.describe());
		return report;
	}

}
