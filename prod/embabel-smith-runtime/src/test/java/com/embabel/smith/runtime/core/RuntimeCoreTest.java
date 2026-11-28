/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import com.embabel.agent.core.Action;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.IoBinding;
import com.embabel.agent.core.ToolGroupRequirement;
import com.embabel.plan.common.condition.ConditionDetermination;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.TestPacks;
import com.embabel.smith.specsource.ResolvedSpecSource;
import com.embabel.smith.specsource.SpecSource;
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
import java.util.Map;
import java.util.Set;

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

	@Test
	void aStepDeclaringACarriedSkillIsReportedAttachedAtIngest(@TempDir Path tmp) throws Exception {
		IngestedSource ingested = core().ingest(skillPack(tmp, "notes", "notes", null));

		// The report is the whole offline claim: a deployed AgentScope publishes no tool
		// list, so the alternative to saying what was attached is saying nothing at all.
		assertThat(ingested.report().lines()).anyMatch((line) -> line.contains("read -> [ask-skills]"));
		assertThat(ingested.report().lines()).noneMatch((line) -> line.contains("no skill folder"));
	}

	@Test
	void aStepDeclaringASharedSkillIsReportedAttachedUnderSharedSkills(@TempDir Path tmp) throws Exception {
		// The skill sits in the source root's skills/, not in the pack, which is the
		// shared route rather than the pack's own — and the container it attaches to is
		// named for that route, which is also the prefix the callable names carry.
		IngestedSource ingested = core().ingest(skillPack(tmp, null, "shared-notes", "shared-notes"));

		assertThat(ingested.report().lines()).anyMatch((line) -> line.contains("read -> [shared-skills]"));
	}

	@Test
	void aStepDeclaringASkillNoFolderCarriesIsRefusedBeforeItRuns(@TempDir Path tmp) throws Exception {
		ResolvedSpecSource source = skillPack(tmp, "notes", "no-such-skill", null);

		// The validator is the front door and it names the mistake, so a dangling
		// declaration does not reach a run at all. The wrapper's own dangling report is
		// therefore the backstop for a path that bypasses validation, which
		// ReferenceAwareStepSpecRepositoryTest pins directly.
		assertThat(source.packs()).isEmpty();
		assertThat(source.warnings()).anyMatch((warning) -> warning.contains("declares reference 'no-such-skill'"));
		assertThatThrownBy(() -> core().ingest(source)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("contains no agent specs");
	}

	@Test
	void referencesDeclaredOrRemovedLeaveThePlannersStepSequenceUntouched(@TempDir Path declaredDir,
			@TempDir Path bareDir) throws Exception {
		// Declaring a reference changes what a step is offered, and nothing else. The
		// planner reasons over the step graph: a step's name, what it takes and leaves,
		// the conditions it needs and gives, the roles it asks for. A reference belongs
		// to none of those columns, so one pack ingested twice — once with its
		// declarations and once with them struck out — has to present the same sequence
		// of steps. The pair above is what keeps that equality from passing by default:
		// it says one of these two ingests really attached something and the other really
		// attached nothing, which is the difference between references that resolved and
		// declarations that were dropped without a trace.
		IngestedSource declared = core().ingest(skillPack(declaredDir, "notes", "notes", null));
		IngestedSource bare = core().ingest(skillPack(bareDir, "notes", null, null));

		assertThat(declared.report().lines()).anyMatch((line) -> line.contains("read -> [ask-skills]"));
		assertThat(bare.report().lines()).noneMatch((line) -> line.contains("read ->"));

		assertThat(graph(declared)).as("the graph the planner reasons over").isEqualTo(graph(bare));
		assertThat(names(declared)).as("the same steps, in the same order").isEqualTo(names(bare));
	}

	@Test
	void aStepDeclaringNoReferenceIsReportedAsAttachedToNothing(@TempDir Path tmp) throws Exception {
		IngestedSource ingested = core().ingest(skillPack(tmp, "notes", null, null));

		// The pack carries the skill on disk and the container has tools to offer, so the
		// silence here is the pack's own declaration rather than an absent folder.
		assertThat(ingested.report().lines()).noneMatch((line) -> line.contains("read ->"));
		assertThat(ingested.report().lines()).noneMatch((line) -> line.contains("no skill folder"));
	}

	/**
	 * A two-step pack in a folder source, with the skill placed in the pack, in the
	 * source's shared folder, or both, and a step that declares a name or nothing. The
	 * second step declares nothing in any variant: it is there so an ingest presents a
	 * sequence of steps rather than a single name.
	 * @param tmp the directory that becomes the folder source
	 * @param ownSkill the skill carried by the pack itself, or {@code null} for none
	 * @param declared what the step declares, or {@code null} for no {@code references}
	 * key
	 * @param sharedSkill the skill carried by the source root's shared {@code skills/},
	 * or {@code null} for none
	 * @return the resolved source, ready to ingest
	 */
	private ResolvedSpecSource skillPack(Path tmp, String ownSkill, String declared, String sharedSkill)
			throws IOException {
		Path pack = Files.createDirectories(tmp.resolve("ask"));
		Files.writeString(pack.resolve("manifest.yml"), "name: ask\nversion: 0.1.0\n");
		// A pack is only well-formed with its types directory present, even empty —
		// validation refuses one without it.
		Files.createDirectories(pack.resolve("types"));
		Files.writeString(pack.resolve("goal.yml"), """
				stepType: goal
				name: answered
				description: "Answered from the working directory"
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				export: true
				""");
		Files.createDirectories(pack.resolve("steps"));
		Files.writeString(pack.resolve("steps/read.yml"), """
				stepType: action
				name: read
				description: "Read one file and answer"
				inputTypeNames:
				  - com.embabel.agent.domain.io.UserInput
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				toolGroups:
				  - file
				%sprompt: |
				  Name one file. {{userInput.content}}
				""".formatted(declared == null ? "" : "references:\n  - " + declared + "\n"));
		// A second step, so the two ingests are compared on a sequence rather than on one
		// name. It declares nothing in either variant — the reference declaration is the
		// only difference between the two packs this feeds.
		Files.writeString(pack.resolve("steps/answer.yml"), """
				stepType: action
				name: answer
				description: "Answer from the request"
				inputTypeNames:
				  - com.embabel.agent.domain.io.UserInput
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				prompt: |
				  Summarise the reading. {{userInput.content}}
				""");
		if (ownSkill != null) {
			writeSkill(Files.createDirectories(pack.resolve("skills/" + ownSkill)), ownSkill, "carried by the pack");
		}
		if (sharedSkill != null) {
			writeSkill(Files.createDirectories(tmp.resolve("skills/" + sharedSkill)), sharedSkill,
					"carried by the source root");
		}
		return SpecSource.parse("folder:" + tmp.toAbsolutePath()).resolve();
	}

	/**
	 * The step graph as a planner sees it: each deployed action stated in the columns a
	 * plan is built from, in the order the deployed agent holds them.
	 * @param ingested the ingest to read
	 * @return one entry per action, in deployment order
	 */
	private static List<String> graph(IngestedSource ingested) {
		return ingested.agent().getActions().stream().map(RuntimeCoreTest::describe).toList();
	}

	/**
	 * The step names, in order — the sequence itself, without the columns.
	 * @param ingested the ingest to read
	 * @return the deployed action names
	 */
	private static List<String> names(IngestedSource ingested) {
		return ingested.agent().getActions().stream().map(Action::getName).toList();
	}

	/**
	 * One action, stated only in terms a planner can reason with. A reference is
	 * deliberately absent because there is no column for it: an action asks for tool
	 * groups, and what a step was offered as a reference is settled when it runs. That
	 * absence is the claim the equality above rests on.
	 * @param action the deployed action
	 * @return its description
	 */
	private static String describe(Action action) {
		return action.getName() + " takes=" + bindings(action.getInputs()) + " leaves=" + bindings(action.getOutputs())
				+ " needs=" + conditions(action.getPreconditions()) + " gives=" + conditions(action.getEffects())
				+ " roles=" + action.getToolGroups().stream().map(ToolGroupRequirement::getRole).sorted().toList();
	}

	private static List<String> bindings(Set<IoBinding> bindings) {
		return bindings.stream().map(String::valueOf).sorted().toList();
	}

	private static List<String> conditions(Map<String, ConditionDetermination> conditions) {
		return conditions.entrySet().stream().map((entry) -> entry.getKey() + "=" + entry.getValue()).sorted().toList();
	}

	/**
	 * Write a minimal skill folder.
	 * @param folder the directory to write into
	 * @param name the name it declares
	 * @param says what it says about itself
	 */
	private static void writeSkill(Path folder, String name, String says) throws IOException {
		Files.writeString(folder.resolve("SKILL.md"),
				"---\nname: " + name + "\ndescription: \"A skill " + says + "\"\n---\n\nList the directory.\n");
	}

}
