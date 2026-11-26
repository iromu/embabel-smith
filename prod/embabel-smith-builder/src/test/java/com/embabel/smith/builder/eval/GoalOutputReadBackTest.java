/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.smith.builder.core.BuilderPackPaths;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What {@link AgentSpecInvokerFactory} hands back as "the goal output's content" when the
 * goal's output type is one of the pack's own value types rather than a
 * {@link BackgroundMessage} — the boundary that made the pack flow build nothing on
 * 2026-11-22 (task 4.2 of {@code builder-executes-its-own-packs}: two of three inputs
 * died with {@code Unexpected character ('j' (code 106}) ... line: 1, column: 2}, the
 * text {@code {json={"manifest":{…}}}).
 *
 * <p>
 * <b>Why the answer is a {@code Map}.</b> A step whose output type is a {@code
 * DynamicType} is answered by {@code PromptedActionSpecAction.callLlmWithDynamicType}
 * (read from the {@code embabel-agent-spec-1.5.4-20261116.002600-80.jar} bytecode: it
 * generates a JSON schema for the type with {@code JsonSchemaGenerator}, then {@code
 * jacksonObjectMapper().readValue(text, Map::class.java)}), so the value the blackboard
 * holds for the goal is the raw property map — {@code {json=<the spec text>}} for {@code
 * builder-spec-one-shot}'s {@code AgentSpecJson}. There is no {@code DynamicDomainObject}
 * class anywhere on the classpath to read a property off, so {@code String.valueOf} on
 * that value renders the <em>wrapper</em> — a Java map {@code toString}, with an unquoted
 * key — and the generator's parse dies at the property name. The model's answer was
 * well-formed; the read-back lost it, which is why this is a binding fix and not a prompt
 * fix.
 *
 * <p>
 * <b>The doubles.</b> The platform is {@code eval/ToolGroupBindingTest}'s (a mock whose
 * {@code plus(DataDictionary)} returns its argument and whose {@code deploy(AgentScope)}
 * returns the platform, since the scope builder chains onto it), plus {@code
 * runAgentFrom} answering a completed process whose {@code lastResult()} is whatever this
 * test is asking about. Everything between the pack directory and that call — the reader,
 * the writer, the assembler, the type mapper, the scope builder — runs unmocked, and the
 * pack is the shipped one, read from this module's {@code specs/} resources the way the
 * flow reads it, so the test is about the goal boundary of the flow {@code --flow pack}
 * actually runs. No model is called: the answer is supplied.
 *
 * @author Ivan Rodriguez
 */
class GoalOutputReadBackTest {

	/**
	 * The pack {@code --flow pack} deploys, read from the classpath as the shipped
	 * contract.
	 */
	private static final Path PACK_DIR = BuilderPackPaths.directory("builder-spec-one-shot");

	/** The spec text the value type's single {@code string} property carries. */
	private static final String SPEC_TEXT = """
			{"manifest": {"name": "drafted", "version": "1.0.0"}, "goal": null, "steps": [], "types": []}""";

	/**
	 * Deploys the shipped builder pack and returns an invoker whose run ends with the
	 * given goal output, so the assertion is about what the invoker reads back from it.
	 * @param goalOutput the object a completed run's {@code lastResult()} returns
	 * @return the invoker to call
	 * @throws IOException when the shipped pack cannot be read
	 */
	private static AgentSpecInvoker invokerEndingWith(Object goalOutput) throws IOException {
		AgentSpec pack = new AgentSpecReader().read(PACK_DIR);
		AgentPlatform platform = mock(AgentPlatform.class);
		when(platform.plus(any(DataDictionary.class))).thenAnswer((invocation) -> invocation.getArgument(0));
		when(platform.deploy(any(AgentScope.class))).thenReturn(platform);
		AgentProcess process = mock(AgentProcess.class);
		when(process.getStatus()).thenReturn(AgentProcessStatusCode.COMPLETED);
		when(process.lastResult()).thenReturn(goalOutput);
		when(platform.runAgentFrom(any(Agent.class), any(ProcessOptions.class), any())).thenReturn(process);
		return new AgentSpecInvokerFactory(platform).invokerFor(pack);
	}

	/**
	 * The measured failure. A goal whose output type is a value type wrapping one string
	 * must yield that string — the {@code AgentSpecInvoker} contract is the goal output's
	 * <em>content</em>, and rendering the map around it hands the generator a document
	 * whose first key is the property name rather than the spec's.
	 * @throws IOException when the shipped pack cannot be read
	 */
	@Test
	void theContentOfAValueTypeGoalOutputIsItsBoundStringRatherThanTheRenderedMapAroundIt() throws IOException {
		AgentSpecInvoker invoker = invokerEndingWith(Map.of("json", SPEC_TEXT));

		assertThat(invoker.invoke("Ship orders end to end")).as("what the pack flow parses as the draft")
			.isEqualTo(SPEC_TEXT);
	}

	/**
	 * The control, and the half of the read-back that already worked: a goal ending in a
	 * {@link BackgroundMessage} still gives its content, so the value-type case above is
	 * an addition to this path and not a rewrite of it.
	 * @throws IOException when the shipped pack cannot be read
	 */
	@Test
	void aBackgroundMessageGoalOutputStillYieldsItsContent() throws IOException {
		AgentSpecInvoker invoker = invokerEndingWith(new BackgroundMessage("the answer"));

		assertThat(invoker.invoke("Ship orders end to end")).isEqualTo("the answer");
	}

}
