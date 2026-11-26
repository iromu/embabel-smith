/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.nio.file.Path;
import java.util.Locale;

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.trace.TraceStore;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 * <b>Nothing here is reachable on its own.</b> This class is imported by
 * {@link SmithA2aAutoConfiguration} and carries no component scan of its own, so the
 * library's registrar, streaming handler and task state manager come in through that
 * auto-configuration's scan — with the library's {@code A2AConfiguration} filtered out,
 * because its unconditional card handler alongside this module's would mean two mappings
 * on {@code /a2a} and a failed boot. A host therefore reaches these beans only through
 * the {@code smith.a2a.enabled} gate, not by scanning this package.
 *
 * @author Ivan Rodriguez
 */
@Configuration(proxyBeanMethods = false)
public class A2aConfiguration {

	/** The path the endpoint is served under: {@code /a2a}. */
	public static final String ENDPOINT_PATH = "a2a";

	/**
	 * The JSONL trace store, at {@code smith.a2a.trace-dir} or at the CLI default when
	 * unset, so an A2A run lands in the same file a {@code smith run} writes — unless the
	 * host already has a trace store.
	 *
	 * <p>
	 * <b>A host's own store wins, and it has to.</b> {@code embabel-smith-studio}
	 * declares a {@code JsonlTraceStore} of its own, so a second one here would leave
	 * every {@code TraceStore}-typed injection point in the process ambiguous and the
	 * boot would fail. The host's store is also the right answer on the merits: a run
	 * this endpoint starts should land in the place the host keeps its runs, and the
	 * host's store is the one that knows about its own storage.
	 * {@code smith.a2a.trace-dir} is the default for a host that has no store of its own,
	 * not an override of one that does.
	 * @param traceDir the trace directory, or empty for {@code ~/.smith/traces}
	 * @return the trace store
	 */
	@Bean
	@ConditionalOnMissingBean
	public TraceStore traceStore(@Value("${smith.a2a.trace-dir:}") String traceDir) {
		return new JsonlTraceStore(traceFile(traceDir));
	}

	/**
	 * The runtime's own run service, writing to {@link #traceStore(String)} — unless the
	 * host already has one. A host that wires its own {@code AgentSpecRunService} (the
	 * studio does, storage-backed and hot-swappable) keeps it: two bean methods of the
	 * same name in one context is a definition-override failure, not a choice Boot gets
	 * to make silently, and the host's service is the one that knows about its own
	 * storage.
	 * @param platform the booted agent platform
	 * @param traceStore the store each run appends its record to
	 * @return the run service
	 */
	@Bean
	@ConditionalOnMissingBean
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
