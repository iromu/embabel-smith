/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.util.List;

import com.embabel.agent.a2a.server.A2ARequestHandler;
import com.embabel.agent.a2a.server.AgentCardHandler;
import com.embabel.agent.core.AgentPlatform;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentProvider;
import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.NonStreamingJSONRPCRequest;
import io.a2a.spec.StreamingJSONRPCRequest;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The card handler registered at {@code /a2a}: the library's card document, with the
 * skill list built from the configured spec source by {@link SpecSourceSkills} instead of
 * from the goals the platform holds.
 *
 * <p>
 * <b>Only the skills change source.</b> Every other field is the one
 * {@code EmbabelServerGoalsAgentCardHandler} sets, copied from its bytecode: the identity
 * fields ({@code name}, {@code description}) still come from the injected
 * {@link AgentPlatform}, because those are identity and not run history, and the rest are
 * the library's constants. Re-stating them here is the price of replacing that one bean —
 * the registrar registers three mappings per {@code AgentCardHandler} and takes a
 * {@code List}, so a second handler would fail the boot with an ambiguous mapping. The
 * protocol types, the registrar, the streaming handler and the task-state manager all
 * stay the library's; this class assembles one wire object.
 *
 * <p>
 * <b>Requests still go to {@link RunBackedA2ARequestHandler}.</b> The interface extends
 * {@link A2ARequestHandler}, and both of its methods are delegated, which is what the
 * library's handler does with its own request-handler field. Streaming is delegated too:
 * the interface's default implementation throws, so omitting it would take
 * {@code message/stream} down with the reason the card changed.
 *
 * @author Ivan Rodriguez
 */
public class SourceDerivedAgentCardHandler implements AgentCardHandler {

	/** The library's card version — a constant there, so a constant here. */
	private static final String CARD_VERSION = "0.1.0-SNAPSHOT";

	/** The library's provider, verbatim. */
	private static final AgentProvider PROVIDER = new AgentProvider("Embabel", "https://embabel.com");

	/** The library's documentation link, verbatim. */
	private static final String DOCUMENTATION_URL = "https://embabel.com/docs";

	/** The library's default content modes, verbatim. */
	private static final List<String> DEFAULT_MODES = List.of("application/json", "text/plain");

	/** The protocol this endpoint speaks, and the field a client reads to learn it. */
	private static final String PROTOCOL_VERSION = "0.3.0";

	private final String path;

	private final AgentPlatform agentPlatform;

	private final A2ARequestHandler requestHandler;

	private final SpecSourceSkills skills;

	/**
	 * Creates the handler.
	 * @param path the path the endpoint is served under
	 * @param agentPlatform the platform whose identity the card carries
	 * @param requestHandler the handler every JSON-RPC request is delegated to
	 * @param skills the skills read from the configured spec source
	 */
	public SourceDerivedAgentCardHandler(String path, AgentPlatform agentPlatform, A2ARequestHandler requestHandler,
			SpecSourceSkills skills) {
		this.path = path;
		this.agentPlatform = agentPlatform;
		this.requestHandler = requestHandler;
		this.skills = skills;
	}

	@Override
	public String getPath() {
		return this.path;
	}

	/**
	 * The card document. Everything but {@code skills} is the library's; {@code skills}
	 * comes from the configured source, and reading it is the one part of a card request
	 * that can fail — an unreadable source raises here rather than serving an empty list.
	 * @param scheme the request scheme
	 * @param host the request host
	 * @param port the request port
	 * @return the card, whose {@code url} names the path this endpoint accepts POSTs on
	 */
	@Override
	public AgentCard agentCard(String scheme, String host, int port) {
		return new AgentCard.Builder().name(this.agentPlatform.getName())
			.description(this.agentPlatform.getDescription())
			.url(scheme + "://" + host + ":" + port + "/" + this.path)
			.provider(PROVIDER)
			.version(CARD_VERSION)
			.documentationUrl(DOCUMENTATION_URL)
			.capabilities(new AgentCapabilities.Builder().streaming(true)
				.pushNotifications(false)
				.stateTransitionHistory(false)
				.extensions(List.of())
				.build())
			.defaultInputModes(DEFAULT_MODES)
			.defaultOutputModes(DEFAULT_MODES)
			.skills(this.skills.skills())
			.supportsAuthenticatedExtendedCard(false)
			.protocolVersion(PROTOCOL_VERSION)
			.build();
	}

	@Override
	public JSONRPCResponse<?> handleJsonRpc(NonStreamingJSONRPCRequest<?> request) {
		return this.requestHandler.handleJsonRpc(request);
	}

	@Override
	public SseEmitter handleJsonRpcStream(StreamingJSONRPCRequest<?> request) {
		return this.requestHandler.handleJsonRpcStream(request);
	}

	/**
	 * Human-readable identity of this handler, for the platform's own reporting.
	 * @param verbose unused; the string is already complete
	 * @param indent the number of tabs to prefix the string with
	 * @return the one-line description
	 */
	@Override
	public String infoString(Boolean verbose, int indent) {
		return "\t".repeat(indent) + getClass().getSimpleName() + "[path=" + this.path + ", platform="
				+ this.agentPlatform.getName() + "]";
	}

}
