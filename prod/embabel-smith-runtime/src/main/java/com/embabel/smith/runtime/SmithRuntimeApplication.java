/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.runtime.cli.RunCommand;
import com.embabel.smith.runtime.cli.SmithCommand;
import com.embabel.smith.runtime.core.ScriptCallLog;
import picocli.CommandLine;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Spring entry point of the runtime product: boots the Embabel {@link AgentPlatform}
 * (model endpoint fully env-driven, see {@code application.yml}) and hands the CLI
 * arguments to the picocli command line ({@code smith run ...}).
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class SmithRuntimeApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(SmithRuntimeApplication.class)
			.web(WebApplicationType.NONE)
			.run(args);
		int exitCode;
		try {
			AgentPlatform platform = context.getBean(AgentPlatform.class);
			// The engine is taken from the context rather than read from the switch
			// again:
			// the bean is the one thing that decided whether this host may run scripts at
			// all, and a container choice the host cannot honour already failed the
			// refresh. Nothing here means nothing was selected, which is `none`.
			SkillScriptExecutionEngine scripts = context.getBeanProvider(SkillScriptExecutionEngine.class)
				.getIfAvailable();
			// The same boot's log, not a new one: the engine bean was handed this very
			// object, so whatever the run's scripts wrote is what the command prints.
			ScriptCallLog scriptCalls = context.getBeanProvider(ScriptCallLog.class).getIfAvailable(ScriptCallLog::new);
			CommandLine smith = new CommandLine(new SmithCommand());
			smith.addSubcommand(new RunCommand(platform, scripts, scriptCalls));
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
