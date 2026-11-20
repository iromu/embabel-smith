/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import java.nio.file.Path;
import java.util.Locale;

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.embabel.agent.a2a.server.config.A2AConfiguration;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Wiring of the runtime's A2A surface: the trace store the runs append to, the run
 * service the requests drive, and the card handler that puts them on {@code /a2a}.
 *
 * <p>
 * The card handler is declared here rather than left to the library's
 * {@code A2AConfiguration}, because that one delegates to
 * {@code AutonomyA2ARequestHandler}, which runs goals through Embabel Autonomy — skipping
 * the trust-tier gate, the planner selection and the trace record that make a run a Smith
 * run. Its card is replaced too: it derives the skill list from
 * {@code agentPlatform.getGoals()}, which is a deployment history rather than a
 * description of this endpoint, so the skills come from {@code smith.a2a.source} instead.
 *
 * <p>
 * <b>Why this class repeats the library's component scan.</b> The library's card handler
 * is an unconditional {@code @Bean}, so both handlers cannot coexist: its registrar
 * registers one mapping per {@code AgentCardHandler} bean and a second handler fails the
 * boot with an ambiguous mapping for {@code /a2a}. {@code AgentA2AAutoConfiguration} —
 * which is nothing but a scan of {@code com.embabel.agent.a2a} — is therefore excluded at
 * the launcher and the same scan runs here with {@code A2AConfiguration} filtered out.
 * That keeps the library's own registrar, streaming handler and task state manager rather
 * than re-declaring them, and it drops only the handler this module replaces. The
 * auto-configuration's servlet and classpath conditions are not re-stated: this module
 * forces servlet mode and declares the starter, so both are already true here.
 *
 * @author Ivan Rodriguez
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.embabel.agent.a2a",
		excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = A2AConfiguration.class))
public class A2aConfiguration {

	/** The path the endpoint is served under: {@code /a2a}. */
	public static final String ENDPOINT_PATH = "a2a";

	/**
	 * The JSONL trace store, at {@code smith.a2a.trace-dir} or at the CLI default when
	 * unset, so an A2A run lands in the same file a {@code smith run} writes.
	 * @param traceDir the trace directory, or empty for {@code ~/.smith/traces}
	 * @return the trace store
	 */
	@Bean
	public TraceStore traceStore(@Value("${smith.a2a.trace-dir:}") String traceDir) {
		return new JsonlTraceStore(traceFile(traceDir));
	}

	/**
	 * The runtime's own run service, writing to {@link #traceStore(String)}.
	 * @param platform the booted agent platform
	 * @param traceStore the store each run appends its record to
	 * @return the run service
	 */
	@Bean
	public AgentSpecRunService agentSpecRunService(AgentPlatform platform, TraceStore traceStore) {
		return new AgentSpecRunService(platform, traceStore);
	}

	/**
	 * The handler that turns a request into one run of {@code smith.a2a.source} at tier
	 * {@code smith.a2a.trust-tier}.
	 * @param runService the run service to drive
	 * @param traceStore the store {@code tasks/get} reads
	 * @param sourceUri the spec source every run executes against
	 * @param trustTier the tier every run executes under
	 * @return the request handler
	 */
	@Bean
	public RunBackedA2ARequestHandler runBackedA2ARequestHandler(AgentSpecRunService runService, TraceStore traceStore,
			@Value("${smith.a2a.source:}") String sourceUri,
			@Value("${smith.a2a.trust-tier:STANDARD}") String trustTier) {
		return new RunBackedA2ARequestHandler(runService, traceStore, sourceUri, tierOf(trustTier));
	}

	/**
	 * The card handler registered at {@code /a2a}: the library's card document with the
	 * skill list read from {@code smith.a2a.source}, serving requests through
	 * {@link RunBackedA2ARequestHandler} instead of the Autonomy one. The skills are the
	 * goals of the packs the endpoint's configured tier can actually run — the same tier
	 * every run uses, and the same per-pack check the run-time gate makes, so the card
	 * and the gate cannot disagree.
	 * @param platform the platform whose identity the card carries
	 * @param requestHandler the handler that runs the goals
	 * @param sourceUri the spec source the skills are derived from
	 * @param trustTier the tier that decides which packs are advertised, and that every
	 * run executes under
	 * @return the card handler
	 */
	@Bean
	public AgentCardHandler sourceDerivedAgentCardHandler(AgentPlatform platform,
			RunBackedA2ARequestHandler requestHandler, @Value("${smith.a2a.source:}") String sourceUri,
			@Value("${smith.a2a.trust-tier:STANDARD}") String trustTier) {
		return new SourceDerivedAgentCardHandler(ENDPOINT_PATH, platform, requestHandler,
				new SpecSourceSkills(sourceUri, tierOf(trustTier)));
	}

	private static TrustTier tierOf(String trustTier) {
		return TrustTier.valueOf(trustTier.toUpperCase(Locale.ROOT));
	}

	private static Path traceFile(String traceDir) {
		if (traceDir == null || traceDir.isBlank()) {
			return JsonlTraceStore.defaultPath();
		}
		return Path.of(traceDir).resolve("runs.jsonl");
	}

}
