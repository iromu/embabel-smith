/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.embabel.agent.api.common.Ai;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.SkillPublishable;
import com.embabel.smith.builder.skill.script.GraalJsSkillScriptExecutionEngine;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deterministic leg of "a published script executes in {@code prod/}": a published
 * skill folder, the real {@link AiSpecPromptCaller}, a real {@link Ai}, and an endpoint
 * that is a scripted local server. Nothing here talks to a live model.
 *
 * <p>
 * <b>What this adds over the two tests that already cover the host.</b>
 * {@code PublishedSkillHostTest} invokes the script tool by hand — it constructs the
 * {@code ScriptTool$ScriptInput} JSON and calls {@code Tool.call} itself — and
 * {@code cli/SkillRunCommandTest} substitutes the caller so that it can report what the
 * request arrived carrying. Neither of those runs Embabel's dispatch:
 * {@code PromptRunner.withReference} handing the pack's tools to the model, the model
 * naming one back, and the framework executing it. That dispatch is the whole claim that
 * a published script is something a request is answered <em>with</em> rather than
 * something a test can only invoke, so this file keeps it and substitutes only the
 * endpoint. The caller, the host, the loader, the published bytes, the engine and the
 * sandbox are the shipped code.
 *
 * <p>
 * <b>Why the assertion is on the second request and not on a return value.</b> The tool
 * result never comes back to this test — it goes into the conversation. So the evidence
 * is the follow-up request the stub was asked to answer: it carries the script's own
 * stdout, which is text no other party in this test could have written. The scripted
 * script prints a verdict that depends on stdin (9 against the script's own limit of 4),
 * the caller's prompt and the final answer are both phrased so that neither contains it,
 * and the first request is checked to show the tool was <em>offered</em> before it was
 * named — an answer that only asserted the tool ran would also pass if the framework had
 * been handed nothing.
 *
 * <p>
 * <b>The endpoint is pinned, not inherited.</b> The base URL is a dynamic property, so it
 * outranks this machine's environment, and the key, the model list and the default LLM
 * are pinned in the context properties beside it. A retry-heavy client would otherwise
 * turn one malformed canned answer into minutes of backoff, so the attempts are cut to
 * one and a rejected response fails the test rather than the build.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "OPENAI_CUSTOM_API_KEY=stub-key", "OPENAI_CUSTOM_MODELS=stub-model",
		"embabel.models.default-llm=stub-model",
		// One attempt, so a canned answer the client rejects is a test failure and not
		// ten
		// attempts with exponential backoff.
		"embabel.agent.platform.models.openai.custom.max-attempts=1",
		"embabel.agent.platform.models.openai.custom.backoff-millis=1",
		"embabel.agent.platform.action-qos.default.max-attempts=1" })
class PublishedSkillHostModelCallTest {

	private static final String NAME = "cold-store-goods-receipt";

	/**
	 * Embabel's rule: skill name, underscore, the script's file base name, unsanitized.
	 */
	private static final String SCRIPT_TOOL = "cold-store-goods-receipt_check-temperature";

	/**
	 * The name the model is actually offered. {@code withReference} applies the pack's
	 * naming strategy, which prefixes every tool with the reference's own name — the
	 * host's reference is {@code smith-published-skills}, so the script's unsanitized
	 * name reaches the wire behind {@code smith_published_skills_}. The host's own test
	 * names it unprefixed because it reads {@code Skills.tools()} directly, before any
	 * naming strategy; naming the prefixed form here is part of what makes this the
	 * dispatch rather than the listing.
	 */
	private static final String WIRE_TOOL = "smith_published_skills_" + SCRIPT_TOOL;

	private static final String SCRIPTED_BODY = "## Workflow\n1. Read the carrier temperature record.\n"
			+ "2. Run scripts/check-temperature.js and quote what it printed.\n";

	/**
	 * The verdict is a function of stdin, so it can only appear if the script ran: 9
	 * against the script's own default limit of 4 is a breach.
	 */
	private static final String SCRIPT = "const limit = Number(args[0] ?? '4');\n"
			+ "const reading = Number(stdin.trim());\n" + "if (!Number.isFinite(reading)) {\n"
			+ "  console.error('no temperature reading on stdin');\n" + "  process.exit(2);\n" + "}\n"
			+ "console.log(reading > limit ? 'reject and escalate' : 'accept and file the record');\n";

	/**
	 * What the model is asked, phrased so the verdict it must answer with appears nowhere
	 * in the prompt.
	 */
	private static final String REQUEST = "a chilled pallet arrived at 9 degrees";

	/**
	 * The arguments the model is scripted to pass. The shape is {@code ScriptTool}'s own
	 * input: args, stdin, inputFiles.
	 */
	private static final String TOOL_ARGUMENTS = "{\"args\": [], \"stdin\": \"9\", \"inputFiles\": []}";

	/**
	 * The closing turn. Deliberately worded so it shares no substring with the verdict,
	 * which keeps the assertion below from being satisfiable by the answer alone.
	 */
	private static final String FINAL_ANSWER = "The consignment fails its cold-chain check.";

	private static final EvalSuite EVALS = new EvalSuite(NAME, List.of(new EvalSuite.Eval(1, "receive a chilled pallet",
			"accepted after a check", List.of(), List.of("must apply FEFO"))));

	/** The stubbed endpoint, started on the first request for its port. */
	private static volatile HttpServer stubEndpoint;

	/** Every request body the stub answered, in order. */
	private static final List<String> REQUEST_BODIES = Collections.synchronizedList(new ArrayList<>());

	/** How many completions the stub has handed out, indexing the scripted responses. */
	private static final AtomicInteger SERVED = new AtomicInteger();

	private static synchronized HttpServer stubEndpoint() throws IOException {
		if (stubEndpoint == null) {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				String body = readFully(exchange.getRequestBody());
				REQUEST_BODIES.add(body);
				byte[] response = scriptedResponse(SERVED.getAndIncrement()).getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, response.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(response);
				}
			});
			server.start();
			stubEndpoint = server;
		}
		return stubEndpoint;
	}

	/**
	 * The scripted conversation: first a tool call for the published script, then a plain
	 * answer with no tool calls so the loop ends. Anything beyond the script's own stdout
	 * is invention, so the second response is kept as flat as the first.
	 * @param index which turn this is, counted from the start of the test
	 * @return an OpenAI-compatible completion for that turn
	 */
	private static String scriptedResponse(int index) {
		if (index > 0) {
			String message = """
					"role":"assistant","content":"%s"
					""".formatted(FINAL_ANSWER);
			return completion(message, "stop");
		}
		String message = """
				"role":"assistant","content":"",\
				"tool_calls":[{"id":"call-1","type":"function",\
				"function":{"name":"%s","arguments":"%s"}}]
				""".formatted(WIRE_TOOL, TOOL_ARGUMENTS.replace("\"", "\\\""));
		return completion(message, "tool_calls");
	}

	private static String completion(String message, String finishReason) {
		return """
				{"id":"chatcmpl-smith-stub","object":"chat.completion","created":1760000000,"model":"stub-model",\
				"choices":[{"index":0,"finish_reason":"%s","message":{%s}}],\
				"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
				""".formatted(finishReason, message);
	}

	private static String readFully(InputStream in) throws IOException {
		return new String(in.readAllBytes(), StandardCharsets.UTF_8);
	}

	@AfterAll
	static void stopStubEndpoint() {
		if (stubEndpoint != null) {
			stubEndpoint.stop(0);
		}
	}

	@BeforeEach
	void rewindTheScript() {
		REQUEST_BODIES.clear();
		SERVED.set(0);
	}

	/**
	 * Points the endpoint at the stub. A dynamic property outranks the process
	 * environment, so an {@code OPENAI_CUSTOM_BASE_URL} this machine happens to carry
	 * cannot answer instead of the script.
	 */
	@DynamicPropertySource
	static void stubEndpointUrl(DynamicPropertyRegistry registry) {
		registry.add("OPENAI_CUSTOM_BASE_URL", () -> {
			try {
				return "http://127.0.0.1:" + stubEndpoint().getAddress().getPort() + "/v1";
			}
			catch (IOException couldNotBind) {
				throw new IllegalStateException(couldNotBind);
			}
		});
	}

	@Autowired
	private Ai ai;

	@Test
	void aPublishedScriptRunsBecauseTheModelAskedForIt(@TempDir Path library) throws Exception {
		publish(library);

		String answer = new AiSpecPromptCaller(this.ai).call("", REQUEST,
				new PublishedSkillHost(library, new GraalJsSkillScriptExecutionEngine()).skills());

		// The call is the shipped one and the answer is the scripted one, so the loop ran
		// to completion rather than stopping at the tool.
		assertThat(answer).isEqualTo(FINAL_ANSWER);
		// A tool round-trip is a second request by definition: the model answered with a
		// call, so the framework had to ask it again to get the closing turn.
		assertThat(REQUEST_BODIES).as("the model was asked again, with the tool result in hand")
			.hasSizeGreaterThanOrEqualTo(2);
		// Offered before it was named: the first request lists the script among the
		// call's tools, which is withReference doing its job and not a name invented
		// here.
		assertThat(REQUEST_BODIES.get(0)).contains(WIRE_TOOL);
		// The execution, asserted where it is observable. Nothing in this test invoked a
		// tool: the verdict below is the script's stdout arriving in the conversation the
		// model was answering, and the exit line is the engine's own report that it ran.
		assertThat(REQUEST_BODIES.get(REQUEST_BODIES.size() - 1)).contains("reject and escalate")
			.contains("Script completed with exit code 0");
	}

	private static void publish(Path library) throws Exception {
		Skill skill = new Skill(NAME, "Receive chilled goods and reject a temperature breach.", SCRIPTED_BODY,
				List.of(new SkillScript("scripts/check-temperature.js", SCRIPT)));
		new FolderPublisher(PublishTarget.parse("folder:" + library)).publish(new SkillPublishable(skill, EVALS,
				new LifecycleStamper().stamp(SkillContentHash.of(skill, EVALS), 0.75)), false, false);
	}

}
