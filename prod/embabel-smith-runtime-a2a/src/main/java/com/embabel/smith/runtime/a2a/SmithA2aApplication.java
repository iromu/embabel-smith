/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import com.embabel.agent.autoconfigure.a2a.AgentA2AAutoConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Launcher of the runtime's A2A surface — the only prod module that gains a servlet
 * engine from a dependency rather than declaring one, and the only place the Embabel A2A
 * starter appears. The studio also listens, on its own starter, because it is the UI.
 *
 * <p>
 * <b>What this endpoint is.</b> A local development surface: an explicit non-default port
 * ({@code 18090}, never {@code 8080}), no authentication, no localhost bind. Anyone who
 * can reach the port can start a real GOAP run, which spends real billable model calls.
 * That posture is a decision, recorded in this module's README, not an oversight.
 *
 * <p>
 * <b>Why the servlet flip is a system property.</b> Spring Boot 4 re-binds
 * {@code spring.main.*} from the environment at startup, so a programmatic
 * {@code .web(WebApplicationType.SERVLET)} loses to the {@code application.yml} the
 * {@code embabel-smith-runtime} jar ships (which says {@code none}) and the context comes
 * up non-web with every A2A bean absent. A system property outranks any classpath config
 * file. This is the same override {@code SmithStudioApplication} carries for the same
 * reason.
 *
 * <p>
 * <b>The asymmetry that comes with it.</b> This override is the only thing that can turn
 * a server on inside a module whose dependency must not have one: the runtime stays
 * {@code WebApplicationType.NONE} because nothing but this launcher sets the property.
 * Anyone who copies the line into another launcher turns that module's CLI into a network
 * surface.
 *
 * <p>
 * <b>Why the A2A auto-configuration is excluded.</b> The library's own card handler is an
 * unconditional {@code @Bean}, so declaring ours alongside it produces two
 * {@code AgentCardHandler} beans and the library's registrar fails the boot with an
 * ambiguous mapping for {@code /a2a}. Excluding the auto-configuration and running its
 * component scan from {@link A2aConfiguration} with that one configuration filtered out
 * leaves exactly one card handler, and it is the run-backed one.
 *
 * <p>
 * The runtime's own {@code SmithRuntimeApplication} is untouched: {@code smith run} still
 * boots non-web, still exits with the CLI's exit code, and still opens no port.
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication(exclude = AgentA2AAutoConfiguration.class)
public class SmithA2aApplication {

	public static void main(String[] args) {
		// See the class javadoc: force servlet mode as a system property so the runtime
		// jar's
		// web-application-type=none cannot shadow this module's configuration.
		System.setProperty("spring.main.web-application-type", "SERVLET");
		SpringApplication.run(SmithA2aApplication.class, args);
	}

}
