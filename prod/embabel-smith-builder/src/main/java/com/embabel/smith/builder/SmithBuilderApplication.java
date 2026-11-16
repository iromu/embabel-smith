/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.cli.BuildCommand;
import com.embabel.smith.builder.cli.SmithBuilderCommand;
import com.embabel.smith.builder.core.BuilderAgent;
import picocli.CommandLine;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Spring entry point of the builder product: boots the Embabel {@link AgentPlatform} and
 * {@link Ai} (model endpoint fully env-driven, see {@code application.yml}) and hands the
 * CLI arguments to the picocli command line ({@code smith build ...}).
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
			smith.addSubcommand(new BuildCommand(platform, ai));
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

}
