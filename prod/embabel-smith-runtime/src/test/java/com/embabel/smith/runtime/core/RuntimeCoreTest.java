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
import com.embabel.agent.core.ToolGroupResolution;
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
		// The unresolved wording, whatever it says, is what the control below looks for:
		// a declaration that failed to resolve has to leave a line saying so.
		assertThat(ingested.report().lines()).noneMatch((line) -> line.contains("declares reference"));
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
		// And the line that ends the run carries it too: an ingest that resolves to
		// nothing never reaches the report that would otherwise name the declaration, so
		// the reader of a four-second exit would otherwise see only an empty source.
		assertThatThrownBy(() -> core().ingest(source)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("contains no agent specs")
			.hasMessageContaining("no-such-skill");
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
		assertThat(ingested.report().lines()).noneMatch((line) -> line.contains("declares reference"));
	}

	@Test
	void aStepGivenASkillAndADocumentIsAttachedToBoth(@TempDir Path tmp) throws Exception {
		IngestedSource ingested = core().ingest(twoKindPack(tmp, "notes", "domain-guide"));

		assertThat(ingested.goalNames()).as("the pack deployed").contains("answered");
		// Two kinds, one step, one line: the container the skill folder holds and the
		// document the pack's references file declares. Every case above declares a
		// single name, and a single-name step resolves whichever kind the run happens to
		// read — a run that kept only skills would pass all of them. This is the case
		// where the two doors have to be open at once.
		assertThat(ingested.report().lines()).anyMatch((line) -> line.contains("read -> [ask-skills, domain-guide]"));
		assertThat(ingested.report().lines()).as("nothing declared failed to resolve")
			.noneMatch((line) -> line.contains("declares reference"));
	}

	@Test
	void eachKindResolvesOnItsOwnThroughTheSameSeam(@TempDir Path skillDir, @TempDir Path documentDir)
			throws Exception {
		IngestedSource skill = core().ingest(twoKindPack(skillDir, "notes"));
		IngestedSource document = core().ingest(twoKindPack(documentDir, "domain-guide"));

		// The pair that keeps the test above from passing by accident: the pack is the
		// same pack either way, so each name really is carried and each resolves alone.
		assertThat(skill.report().lines()).anyMatch((line) -> line.contains("read -> [ask-skills]"));
		assertThat(document.report().lines()).anyMatch((line) -> line.contains("read -> [domain-guide]"));
	}

	@Test
	void aRoleSpelledWronglyIsNamedByTheResolverRatherThanByTheReport(@TempDir Path tmp) throws Exception {
		IngestedSource ingested = core().ingest(rolePack(tmp, MISSPELLED_ROLE));

		// The declaration is a pass-through. Nothing reads a tool-group name before the
		// prompt asks the resolver for it, so the pack deploys carrying the misspelling
		// verbatim rather than being refused for it.
		assertThat(ingested.goalNames()).as("the pack deploys").contains("roleDone");
		assertThat(roles(ingested)).as("with the misspelling intact on the deployed action").contains(MISSPELLED_ROLE);

		// The naming the design notes attribute to the run lives one level down, in the
		// answer the platform gives when the step asks. It is a real answer and it names
		// the role, which is why a misspelling is findable — from the resolver, not from
		// anything the ingest prints.
		ToolGroupResolution resolution = resolve(MISSPELLED_ROLE);
		assertThat(resolution.getResolvedToolGroup()).as("nothing answers the misspelling").isNull();
		assertThat(resolution.getFailureMessage()).as("and the answer says so, by name").contains(MISSPELLED_ROLE);

		// The ingest has lines to print for this pack — the skill it carries is there to
		// guarantee that — so what the report does not say is about roles rather than
		// about a report that never said anything at all.
		assertThat(ingested.report().lines()).as("the report is not empty").isNotEmpty();
		assertThat(ingested.report().lines()).as("and no line of it names the role that resolved to nothing")
			.noneMatch((line) -> line.contains(MISSPELLED_ROLE));
	}

	@Test
	void aResolvedRoleIsReportedAsNoFailureAndTheTwoSpellingsReadAlike(@TempDir Path resolvesDir,
			@TempDir Path missesDir) throws Exception {
		IngestedSource resolved = core().ingest(rolePack(resolvesDir, "file"));
		IngestedSource spelledWrong = core().ingest(rolePack(missesDir, MISSPELLED_ROLE));

		// The resolver is alive here and the two declarations genuinely differ at it, so
		// the equality below is about the report rather than about a dead resolver.
		assertThat(resolve("math").getResolvedToolGroup()).as("upstream's own role resolves here").isNotNull();
		assertThat(resolve("file").getResolvedToolGroup()).as("and so does the addon's").isNotNull();
		assertThat(resolve(MISSPELLED_ROLE).getResolvedToolGroup()).isNull();

		// What a step was offered, and whether a model called any of it, is settled when
		// the step runs. A role that resolved reports no failure at ingest, which is the
		// delta's second case: an ignored group must not read as a broken one.
		assertThat(resolved.report().lines()).as("a resolved role is not named as a failure anywhere")
			.noneMatch((line) -> line.contains("file"));
		assertThat(resolved.report().lines())
			.as("and the report reads the same whichever way the declaration was spelled, which is the gap the "
					+ "delta's first case is about: a role that resolved to nothing is not named here either")
			.isEqualTo(spelledWrong.report().lines());
	}

	/**
	 * A two-step pack whose first step declares exactly one tool-group role and whose
	 * second declares none, carrying one skill so the ingest has something to report
	 * whatever the role says. The skill is the control: without it, a report that named
	 * no role would be indistinguishable from a report that said nothing.
	 * @param tmp the directory that becomes the folder source
	 * @param role the single role the first step declares
	 * @return the resolved source, ready to ingest
	 */
	private ResolvedSpecSource rolePack(Path tmp, String role) throws IOException {
		Path pack = Files.createDirectories(tmp.resolve("roles"));
		Files.writeString(pack.resolve("manifest.yml"), "name: roles\nversion: 0.1.0\n");
		Files.createDirectories(pack.resolve("types"));
		Files.writeString(pack.resolve("goal.yml"), """
				stepType: goal
				name: roleDone
				description: "Reached from a step that declares one tool group"
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				export: true
				""");
		Files.createDirectories(pack.resolve("steps"));
		Files.writeString(pack.resolve("steps/declared.yml"), """
				stepType: action
				name: declared
				description: "Declares one tool group and answers"
				inputTypeNames:
				  - com.embabel.agent.domain.io.UserInput
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				toolGroups:
				  - %s
				prompt: |
				  Use what you were given. {{userInput.content}}
				""".formatted(role));
		Files.writeString(pack.resolve("steps/undeclared.yml"), """
				stepType: action
				name: undeclared
				description: "Declares no tool group"
				inputTypeNames:
				  - com.embabel.agent.domain.io.UserInput
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				prompt: |
				  Summarise the answer. {{userInput.content}}
				""");
		writeSkill(Files.createDirectories(pack.resolve("skills/notes")), "notes",
				"carried so the ingest has a line to print");
		return SpecSource.parse("folder:" + pack.toAbsolutePath().getParent()).resolve();
	}

	/**
	 * The roles the deployed actions ask for, across every step.
	 * @param ingested the ingest to read
	 * @return the declared role names
	 */
	private static List<String> roles(IngestedSource ingested) {
		return ingested.agent()
			.getActions()
			.stream()
			.flatMap((action) -> action.getToolGroups().stream())
			.map(ToolGroupRequirement::getRole)
			.toList();
	}

	/**
	 * What the platform's own resolver answers for a role, which is the answer a step
	 * gets when it runs.
	 * @param role the role to ask about
	 * @return the resolution, group or failure message
	 */
	private ToolGroupResolution resolve(String role) {
		return this.platform.getToolGroupResolver().resolveToolGroup(new ToolGroupRequirement(role, Set.of(), null));
	}

	/**
	 * A tool-group role that resolves to nothing by construction: the addon's {@code
	 * shell} role with one letter doubled, so it cannot match a registered group and
	 * cannot be mistaken for a name this repo chose on purpose.
	 */
	private static final String MISSPELLED_ROLE = "shelll";

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
	 * A pack that carries two kinds of reference and a step that declares some of them: a
	 * skill in its own {@code skills/} folder, and a document its {@code references.yml}
	 * declares. The pack is one pack in both cases — only the declaration varies — so a
	 * run that could answer one kind and not the other shows up as a difference in the
	 * report rather than as two packs that differ in more than the thing under test.
	 * @param tmp the directory that becomes the folder source
	 * @param declared the names the step declares, in the order it declares them
	 * @return the resolved source, ready to ingest
	 */
	private ResolvedSpecSource twoKindPack(Path tmp, String... declared) throws IOException {
		Path pack = Files.createDirectories(tmp.resolve("ask"));
		Files.writeString(pack.resolve("manifest.yml"), "name: ask\nversion: 0.1.0\n");
		Files.createDirectories(pack.resolve("types"));
		Files.writeString(pack.resolve("goal.yml"), """
				stepType: goal
				name: answered
				description: "Answered with the pack's own document"
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				export: true
				""");
		Files.createDirectories(pack.resolve("steps"));
		StringBuilder references = new StringBuilder();
		for (String name : declared) {
			references.append("  - ").append(name).append('\n');
		}
		Files.writeString(pack.resolve("steps/read.yml"), """
				stepType: action
				name: read
				description: "Read the document and answer"
				inputTypeNames:
				  - com.embabel.agent.domain.io.UserInput
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				references:
				%sprompt: |
				  Name one file. {{userInput.content}}
				""".formatted(references));
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
		writeSkill(Files.createDirectories(pack.resolve("skills/notes")), "notes", "carried by the pack");
		Files.createDirectories(pack.resolve("docs"));
		Files.writeString(pack.resolve("docs/domain.md"), "The domain of the ask pack, carried by the pack.\n");
		Files.writeString(pack.resolve(PackReferences.REFERENCES_FILE), """
				references:
				  - name: domain-guide
				    kind: document
				    path: docs/domain.md
				""");
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
