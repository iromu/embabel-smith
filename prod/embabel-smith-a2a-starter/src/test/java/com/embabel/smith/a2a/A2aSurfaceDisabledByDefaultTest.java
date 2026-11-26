/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import com.embabel.agent.a2a.server.AgentCardHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Depending on this library opens no endpoint: with {@code smith.a2a.enabled} unset, a
 * servlet application gets neither the Smith beans nor the Embabel library's own
 * Autonomy-backed ones.
 *
 * <p>
 * The second half is the reason the library's
 * {@code com.embabel.agent.autoconfigure.a2a.AgentA2AAutoConfiguration} is suppressed
 * from the environment rather than only from an application class. It is a scan of
 * {@code com.embabel.agent.a2a} with no property condition, so in a servlet app it would
 * register its own card handler and a working {@code /a2a} that runs goals through
 * Autonomy — no trust-tier gate, no trace record — on a module that never asked for a
 * surface. Both assertions here are about the state a host sees when it does nothing, so
 * both fail if that suppression stops happening.
 *
 * <p>
 * The web-application-type property is stated because without it the test context comes
 * up non-web, where there are no request mappings to inspect and the second assertion
 * would pass for the wrong reason.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1",
				"OPENAI_CUSTOM_API_KEY=sk-a2a-test", "OPENAI_CUSTOM_MODELS=a2a-test-model",
				"embabel.models.default-llm=a2a-test-model", })
class A2aSurfaceDisabledByDefaultTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void noAgentCardHandlerBeanIsRegisteredWhenTheSurfaceIsNotEnabled() {
		assertThat(this.context.getBeanNamesForType(AgentCardHandler.class))
			.as("card handlers with smith.a2a.enabled unset")
			.isEmpty();
	}

	@Test
	void noRequestMappingIsRegisteredUnderTheA2aPathWhenTheSurfaceIsNotEnabled() {
		RequestMappingHandlerMapping mappings = this.context.getBean(RequestMappingHandlerMapping.class);

		assertThat(mappings.getHandlerMethods().keySet()).as("mappings registered under /a2a")
			.noneMatch((info) -> info.toString().contains("/" + A2aConfiguration.ENDPOINT_PATH));
	}

}
