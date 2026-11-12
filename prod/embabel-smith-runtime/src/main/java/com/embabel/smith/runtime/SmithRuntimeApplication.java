/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.runtime;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.cli.RunCommand;
import com.embabel.smith.runtime.cli.SmithCommand;
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
			CommandLine smith = new CommandLine(new SmithCommand());
			smith.addSubcommand(new RunCommand(platform));
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
