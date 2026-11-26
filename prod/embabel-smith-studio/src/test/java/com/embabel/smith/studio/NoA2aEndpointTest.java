/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The studio exposes no A2A surface by default.
 *
 * <p>
 * This is the whole-repo invariant the A2A change has to keep. This module now depends on
 * {@code embabel-smith-a2a-starter} on purpose — it is the repo's servlet host, so it is
 * the module the surface is turned on in — and what keeps it shut is not the classpath
 * but two conditions: the starter's beans are conditional on {@code smith.a2a.enabled},
 * and the starter's {@code EnvironmentPostProcessor} keeps the Embabel library's own
 * {@code AgentA2AAutoConfiguration} out of the process even here. Without that
 * suppression the library's unconditional, Autonomy-backed card handler would register
 * {@code GET /a2a/.well-known/agent.json} and {@code POST /a2a} in this app on a module
 * whose contract is a UI utility — a surface that runs goals with no trust-tier gate and
 * no trace record, which is exactly what the starter exists to replace.
 * {@link A2aEndpointWhenEnabledTest} is the other half of the same claim: set the flag
 * and the mappings do appear.
 *
 * <p>
 * <b>Why the assertion is on the mappings and not on a 404.</b> It cannot be a 404:
 * {@code SpaWebConfig} maps the frontend catch-all with an {@code index.html} fallback,
 * so every unmapped path in this application answers 200, and 405 to a POST. Measured:
 * {@code GET /a2a/.well-known/agent.json} answered 200 and {@code POST /a2a} answered 405
 * with no A2A code anywhere on the classpath. So the test asks the two questions that
 * actually distinguish the cases — no request mapping is registered under {@code /a2a},
 * and the card route does not answer with an agent card.
 *
 * <p>
 * The web-application-type property is stated explicitly because without it the test
 * context comes up non-web in this module and there are no request mappings to inspect;
 * the application sets the same value as a system property for the same class of problem.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1",
				"OPENAI_CUSTOM_API_KEY=sk-studio-test", "OPENAI_CUSTOM_MODELS=studio-test-model",
				"embabel.models.default-llm=studio-test-model", })
class NoA2aEndpointTest {

	private static final String AGENT_CARD_PATH = "/a2a/.well-known/agent.json";

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext context;

	@Test
	void noRequestMappingIsRegisteredUnderTheA2aPath() {
		RequestMappingHandlerMapping mappings = this.context.getBean(RequestMappingHandlerMapping.class);

		assertThat(mappings.getHandlerMethods().keySet()).as("mappings registered under /a2a")
			.noneMatch((info) -> info.toString().contains("/a2a"));
	}

	@Test
	void theAgentCardRouteDoesNotAnswerWithAnAgentCard() {
		String body = get(AGENT_CARD_PATH);

		// The frontend shell answers here for every unmapped path; an agent card carries
		// the protocol version and the endpoint URL.
		assertThat(body).doesNotContain("protocolVersion").doesNotContain("\"capabilities\"");
	}

	private String get(String path) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
				.GET()
				.build();
			return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
