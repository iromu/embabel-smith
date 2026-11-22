/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.DomainType;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.TestPacks;
import com.embabel.smith.specsource.ResolvedSpecSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests of the runtime core against a real (model-less) platform. The model
 * endpoint is a local HTTP server that answers 404: the OpenAI SDK fails fast on 4xx (no
 * retry backoff), and none of these scenarios executes an action body successfully.
 */
@SpringBootTest(properties = { "OPENAI_CUSTOM_API_KEY=test", "OPENAI_CUSTOM_MODELS=test-model",
		"embabel.models.default-llm=test-model",
		// Action execution retries failures (ActionQos default: 5 attempts, 10s backoff
		// x5);
		// the fake 404 endpoint must fail fast instead.
		"embabel.agent.platform.action-qos.default.max-attempts=1" })
class RuntimeCoreTest {

	/** Serves 404 on a random port so model calls fail fast without SDK retry backoff. */
	private static volatile HttpServer fakeEndpoint;

	/**
	 * Lazily started on first access — the dynamic property source resolves
	 * before @BeforeAll.
	 */
	private static synchronized HttpServer fakeEndpoint() throws IOException {
		if (fakeEndpoint == null) {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				byte[] body = "not found".getBytes();
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(404, body.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(body);
				}
			});
			server.start();
			fakeEndpoint = server;
		}
		return fakeEndpoint;
	}

	@AfterAll
	static void stopFakeEndpoint() throws IOException {
		if (fakeEndpoint != null) {
			fakeEndpoint.stop(0);
		}
	}

	/** Points the model endpoint at the fake server, overriding any ambient endpoint. */
	@DynamicPropertySource
	static void fakeEndpointUrl(DynamicPropertyRegistry registry) {
		registry.add("OPENAI_CUSTOM_BASE_URL", () -> {
			try {
				return "http://127.0.0.1:" + fakeEndpoint().getAddress().getPort() + "/v1";
			}
			catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@Autowired
	private AgentPlatform platform;

	private RuntimeCore core() {
		return new RuntimeCore(platform);
	}

	/** Stamps a pack's lifecycle state into the sidecar the runtime reads at ingest. */
	private static void stamp(Path packDir, String state) throws IOException {
		Files.writeString(packDir.resolve(LifecycleSidecar.FILE_NAME),
				"contentHash: 0be2d86f\nbaselinePassRate: 1.0\nstate: " + state + "\nupdatedAt: now\n");
	}

	@Test
	void multiPackIngestDeploysAllGoalsAndResolvesTheGoalPack(@TempDir Path tmp) throws Exception {
		ResolvedSpecSource resolved = TestPacks.resolve(TestPacks.library(tmp, "extract", "explain"));

		IngestedSource ingested = core().ingest(resolved);

		assertThat(ingested.goalNames()).containsExactlyInAnyOrder("briefDone", "explainDone");
		assertThat(ingested.packForGoal("explainDone").name()).isEqualTo("explain");
		assertThat(ingested.packForGoal("briefDone").name()).isEqualTo("extract");
	}

	@Test
	void combinedTypesDeduplicateTheSharedIntermediate() throws Exception {
		List<DomainType> types = core().combinedTypes(List.of(TestPacks.read("extract"), TestPacks.read("explain")));

		assertThat(types).extracting(DomainType::getName)
			.containsExactlyInAnyOrder("com.embabel.agent.domain.io.UserInput",
					"com.embabel.agent.spec.model.BackgroundMessage", "TopicBrief");
	}

	@Test
	void aDuplicateGoalNameFailsAtIngestNamingBothPacks(@TempDir Path tmp) throws Exception {
		ResolvedSpecSource resolved = TestPacks.resolve(TestPacks.library(tmp, "dup-a", "dup-b"));

		assertThatThrownBy(() -> core().ingest(resolved)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("duplicate goal name 'sameGoal'")
			.hasMessageContaining("dup-a")
			.hasMessageContaining("dup-b");
	}

	@Test
	void aRetiredPackIsNotDeployedAndIsNamedAsExcluded(@TempDir Path tmp) throws Exception {
		Path library = TestPacks.library(tmp, "extract", "explain");
		stamp(library.resolve("explain"), "RETIRED");

		IngestedSource ingested = core().ingest(TestPacks.resolve(library));

		assertThat(ingested.goalNames()).containsExactly("briefDone");
		assertThat(ingested.packs()).extracting(AgentSpec::name).containsExactly("extract");
		assertThat(ingested.excluded()).extracting(IngestedSource.ExcludedPack::name).containsExactly("explain");
	}

	@Test
	void aSourceWhoseOnlyPackIsRetiredFailsAsAnEmptySourceNamingIt(@TempDir Path tmp) throws Exception {
		Path library = TestPacks.library(tmp, "explain");
		stamp(library.resolve("explain"), "RETIRED");

		assertThatThrownBy(() -> core().ingest(TestPacks.resolve(library))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("contains no agent specs")
			.hasMessageContaining("explain");
	}

	@Test
	void aRetiredTwinIsNotReportedAsADuplicateGoalCollision(@TempDir Path tmp) throws Exception {
		Path library = TestPacks.library(tmp, "dup-a", "dup-b");
		stamp(library.resolve("dup-b"), "RETIRED");

		IngestedSource ingested = core().ingest(TestPacks.resolve(library));

		assertThat(ingested.goalNames()).containsExactly("sameGoal");
		assertThat(ingested.packs()).extracting(AgentSpec::name).containsExactly("dup-a");
		assertThat(ingested.excluded()).extracting(IngestedSource.ExcludedPack::name).containsExactly("dup-b");
	}

	@Test
	void aGoalOnlyARetiredPackExportsIsALifecycleBlockNotAGoalNotFound(@TempDir Path tmp) throws Exception {
		Path library = TestPacks.library(tmp, "extract", "explain");
		stamp(library.resolve("explain"), "RETIRED");
		IngestedSource ingested = core().ingest(TestPacks.resolve(library));

		assertThatThrownBy(() -> core().run(ingested, "explainDone", "input", null, TrustTier.STANDARD))
			.isInstanceOf(LifecycleBlockedException.class)
			.hasMessageContaining("Run blocked by lifecycle state")
			.hasMessageContaining("'explain'")
			.hasMessageNotContaining("No goal named");
	}

	@Test
	void theLifecycleBlockMessagePrefixIsTheStringTheBuilderMatches() {
		// The improvement loop now lives in the builder and matches this message by
		// prefix
		// rather than catching the type, so the literal is a contract across a module
		// edge
		// with no dependency between the two. Detector.LIFECYCLE_BLOCK_PREFIX is the
		// builder's half of the pin; naming the literal here is the runtime's half.
		assertThat(new LifecycleBlockedException("explain", "explainDone").getMessage())
			.startsWith(LifecycleBlockedException.PREFIX);
		assertThat(LifecycleBlockedException.PREFIX).isEqualTo("Run blocked by lifecycle state");
	}

	@Test
	void aLiveTwinStillWinsTheGoalItsRetiredTwinAlsoExported(@TempDir Path tmp) throws Exception {
		Path library = TestPacks.library(tmp, "dup-a", "dup-b");
		stamp(library.resolve("dup-b"), "RETIRED");
		IngestedSource ingested = core().ingest(TestPacks.resolve(library));

		// The retired twin's exclusion is reported, but it must not block the goal the
		// live
		// pack still exports: the run is attempted rather than refused as retired.
		assertThat(ingested.packForGoal("sameGoal").name()).isEqualTo("dup-a");
		assertThat(ingested.excluded()).extracting(IngestedSource.ExcludedPack::name).containsExactly("dup-b");
		assertThatCode(() -> core().run(ingested, "sameGoal", "input", null, TrustTier.STANDARD))
			.doesNotThrowAnyException();
	}

	@Test
	void aDegradedPackIsStillDeployed(@TempDir Path tmp) throws Exception {
		Path library = TestPacks.library(tmp, "extract", "explain");
		stamp(library.resolve("explain"), "DEGRADED");

		IngestedSource ingested = core().ingest(TestPacks.resolve(library));

		assertThat(ingested.goalNames()).containsExactlyInAnyOrder("briefDone", "explainDone");
		assertThat(ingested.excluded()).isEmpty();
	}

	@Test
	void aMissingGoalFailsListingTheAvailableGoals(@TempDir Path tmp) throws Exception {
		IngestedSource ingested = core().ingest(TestPacks.resolve(TestPacks.library(tmp, "extract", "explain")));

		assertThatThrownBy(() -> core().run(ingested, "nope", "input", null, TrustTier.STANDARD))
			.isInstanceOf(GoalNotFoundException.class)
			.hasMessageContaining("No goal named 'nope'")
			.hasMessageContaining("briefDone")
			.hasMessageContaining("explainDone");
	}

	@Test
	void unmetPreconditionsFailNamingTheUnmetPrecondition(@TempDir Path tmp) throws Exception {
		IngestedSource ingested = core().ingest(TestPacks.resolve(TestPacks.library(tmp, "unreachable")));

		RunOutcome outcome = core().run(ingested, "ghostDone", "any input", null, TrustTier.STANDARD);

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.output()).isNull();
		assertThat(outcome.error()).contains("Goal 'ghostDone' not achieved")
			.contains("unmet precondition")
			.contains("GhostThing");
	}

	@Test
	void aHighTrustStepIsBlockedUnderTheStandardTier(@TempDir Path tmp) throws Exception {
		IngestedSource ingested = core().ingest(TestPacks.resolve(TestPacks.library(tmp, "shell")));

		assertThatThrownBy(() -> core().run(ingested, "cliDone", "ls", null, TrustTier.STANDARD))
			.isInstanceOf(TrustTierBlockedException.class)
			.hasMessageContaining("pack 'shell'")
			.hasMessageContaining("shell")
			.hasMessageContaining("standard");
	}

	@Test
	void theGrantedHighTierPassesTheGate(@TempDir Path tmp) throws Exception {
		IngestedSource ingested = core().ingest(TestPacks.resolve(TestPacks.library(tmp, "shell")));

		// Under HIGH the tier gate passes; the run then fails at the offline model call,
		// never at the tier.
		RunOutcome outcome = core().run(ingested, "cliDone", "ls", null, TrustTier.HIGH);

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.error()).doesNotContain("blocked by trust tier");
	}

}
