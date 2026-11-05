package com.embabel.smith.runtime.core;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.DomainType;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.runtime.TestPacks;
import com.embabel.smith.skillsource.ResolvedSkillSource;
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
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests of the runtime core against a real (model-less) platform. The model endpoint is
 * a local HTTP server that answers 404: the OpenAI SDK fails fast on 4xx (no retry backoff), and
 * none of these scenarios executes an action body successfully.
 */
@SpringBootTest(properties = {
        "OPENAI_CUSTOM_API_KEY=test",
        "OPENAI_CUSTOM_MODELS=test-model",
        "embabel.models.default-llm=test-model",
        // Action execution retries failures (ActionQos default: 5 attempts, 10s backoff x5);
        // the fake 404 endpoint must fail fast instead.
        "embabel.agent.platform.action-qos.default.max-attempts=1"
})
class RuntimeCoreTest {

    /** Serves 404 on a random port so model calls fail fast without SDK retry backoff. */
    private static volatile HttpServer fakeEndpoint;

    /** Lazily started on first access — the dynamic property source resolves before @BeforeAll. */
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
        registry.add("OPENAI_CUSTOM_BASE_URL",
                () -> {
                    try {
                        return "http://127.0.0.1:" + fakeEndpoint().getAddress().getPort() + "/v1";
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    @Autowired
    private AgentPlatform platform;

    private RuntimeCore core() {
        return new RuntimeCore(platform);
    }

    @Test
    void multiPackIngestDeploysAllGoalsAndResolvesTheGoalPack(@TempDir Path tmp) throws Exception {
        ResolvedSkillSource resolved = TestPacks.resolve(TestPacks.library(tmp, "extract", "explain"));

        IngestedSource ingested = core().ingest(resolved);

        assertThat(ingested.goalNames()).containsExactlyInAnyOrder("briefDone", "explainDone");
        assertThat(ingested.packForGoal("explainDone").name()).isEqualTo("explain");
        assertThat(ingested.packForGoal("briefDone").name()).isEqualTo("extract");
    }

    @Test
    void combinedTypesDeduplicateTheSharedIntermediate() throws Exception {
        List<DomainType> types = core().combinedTypes(
                List.of(TestPacks.read("extract"), TestPacks.read("explain")));

        assertThat(types).extracting(DomainType::getName)
                .containsExactlyInAnyOrder(
                        "com.embabel.agent.domain.io.UserInput",
                        "com.embabel.agent.spec.model.BackgroundMessage",
                        "TopicBrief");
    }

    @Test
    void aDuplicateGoalNameFailsAtIngestNamingBothPacks(@TempDir Path tmp) throws Exception {
        ResolvedSkillSource resolved = TestPacks.resolve(TestPacks.library(tmp, "dup-a", "dup-b"));

        assertThatThrownBy(() -> core().ingest(resolved))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate goal name 'sameGoal'")
                .hasMessageContaining("dup-a")
                .hasMessageContaining("dup-b");
    }

    @Test
    void aMissingGoalFailsListingTheAvailableGoals(@TempDir Path tmp) throws Exception {
        IngestedSource ingested = core().ingest(
                TestPacks.resolve(TestPacks.library(tmp, "extract", "explain")));

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
        assertThat(outcome.error())
                .contains("Goal 'ghostDone' not achieved")
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
