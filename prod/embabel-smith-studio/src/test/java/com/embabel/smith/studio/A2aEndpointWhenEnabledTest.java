/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio;

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.embabel.smith.a2a.SourceDerivedAgentCardHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other half of {@link NoA2aEndpointTest}: this studio really does host the A2A
 * surface, so "the studio depends on the starter" is not a claim resting on a pom alone.
 *
 * <p>
 * With {@code smith.a2a.enabled=true} the starter's beans arrive and the {@code /a2a}
 * mappings register in this application. The card handler asserted here is
 * {@link SourceDerivedAgentCardHandler} rather than any {@code AgentCardHandler}, because
 * the distinction is the point: the Embabel library's own handler routes goals through
 * Autonomy, skipping the trust-tier gate and the trace record, and this module's job is
 * to prove the studio gets the Smith one. A second handler would also mean two mappings
 * on {@code /a2a} and an ambiguous-mapping boot failure, so a single handler is doing
 * double duty as the surface's identity check.
 *
 * <p>
 * What is deliberately not asserted here is the card <em>document</em>. It is derived
 * from {@code smith.a2a.source}, and a studio test that named one would be asserting the
 * contents of a spec folder this module does not own; the served document, its protocol
 * version and its skill list are asserted over HTTP in
 * {@code embabel-smith-a2a-starter}'s own tests, and end to end by {@code make run-a2a}.
 *
 * <p>
 * The web-application-type property is stated for the same reason
 * {@code NoA2aEndpointTest} states it: without it the test context comes up non-web and
 * there are no mappings to inspect. It is not what turns the surface on — the flag is.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "smith.a2a.enabled=true", "spring.main.web-application-type=SERVLET",
				"OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=sk-studio-test",
				"OPENAI_CUSTOM_MODELS=studio-test-model", "embabel.models.default-llm=studio-test-model", })
class A2aEndpointWhenEnabledTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void theCardHandlerIsTheSourceDerivedOneAndThereIsOnlyOne() {
		assertThat(this.context.getBeanNamesForType(AgentCardHandler.class)).as("card handlers").hasSize(1);
		assertThat(this.context.getBean(AgentCardHandler.class)).isInstanceOf(SourceDerivedAgentCardHandler.class);
	}

	@Test
	void requestMappingsAreRegisteredUnderTheA2aPath() {
		RequestMappingHandlerMapping mappings = this.context.getBean(RequestMappingHandlerMapping.class);

		assertThat(mappings.getHandlerMethods().keySet()).as("mappings registered under /a2a")
			.anyMatch((info) -> info.toString().contains("/a2a"));
	}

}
