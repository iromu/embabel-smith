/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.cli.BlueprintCommand;
import com.embabel.smith.builder.cli.BuildCommand;
import com.embabel.smith.builder.cli.SkillRunCommand;
import com.embabel.smith.builder.cli.SmithBuilderCommand;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.tools.ToolSurface;
import picocli.CommandLine;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Spring entry point of the builder product: boots the Embabel {@link AgentPlatform} and
 * {@link Ai} (model endpoint fully env-driven, see {@code application.yml}) and hands the
 * CLI arguments to the picocli command line ({@code smith build ...} and
 * {@code smith blueprint ...}).
 *
 * <p>
 * {@link BuilderAgent} is a code-backed {@code @Agent} (hence a Spring {@code @Component}
 * via the annotation's meta-annotation) but is always constructed manually — by
 * {@code BuildCommand} and the tests — with an explicit scratch dir and compiler, never
 * via the {@code AgentPlatform}. It is excluded from component scanning so the context
 * does not try (and fail) to instantiate it.
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.embabel.smith.builder",
		excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = BuilderAgent.class))
public class SmithBuilderApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(SmithBuilderApplication.class)
			.web(WebApplicationType.NONE)
			.run(args);
		int exitCode;
		try {
			AgentPlatform platform = context.getBean(AgentPlatform.class);
			Ai ai = context.getBean(Ai.class);
			CommandLine smith = new CommandLine(new SmithBuilderCommand());
			// The surface is the bean the tool groups were built with, so the CLI and the
			// tools see the same opened roots.
			// The script engine is asked for rather than required: it exists only when
			// smith.builder.skill-scripts.enabled is on, and with no bean the skill path
			// gets no script host and its eval arms stay prompt substitution.
			// One lookup for both consumers of the engine: the build's eval arm and the
			// reader of a published folder are the same switch, and two lookups could
			// disagree about what it decided.
			SkillScriptExecutionEngine scriptEngine = scriptEngine(context);
			smith.addSubcommand(new BuildCommand(platform, ai, context.getBean(ToolSurface.class), scriptEngine));
			smith.addSubcommand(new SkillRunCommand(ai, scriptEngine));
			smith.addSubcommand(new BlueprintCommand(ai));
			exitCode = smith.execute(args);
		}
		catch (Exception ex) {
			System.err.println((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
			exitCode = 1;
		}
		finally {
			context.close();
		}
		System.exit(exitCode);
	}

	/**
	 * The skill-script engine exactly as this launcher obtains it: <em>asked for</em>
	 * through the bean provider, so a context that did not register one answers null and
	 * both of its consumers — the build's eval arm and the published-folder reader — run
	 * without a script tool. Constructing an engine here instead would make
	 * {@code smith.builder.skill-scripts.enabled} a value nothing consults, which is why
	 * the pair test goes through this method rather than through the bean directly.
	 * @param beans the booted context
	 * @return the engine the switch installed, or null
	 */
	static SkillScriptExecutionEngine scriptEngine(BeanFactory beans) {
		return beans.getBeanProvider(SkillScriptExecutionEngine.class).getIfAvailable();
	}

}
