package com.embabel.smith.selector.llm;

import static com.embabel.smith.selector.llm.DecisionPrompt.Format.ID;
import static com.embabel.smith.selector.llm.DecisionPrompt.Format.OOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.embabel.smith.retrieval.SkillEntry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class LlmSelectorTest {

    private static final List<SkillEntry> THREE = List.of(
            new SkillEntry("http-client", "http-client", "A reusable HTTP client wrapper for REST calls.",
                    List.of("UserInput"), "HttpResponse"),
            new SkillEntry("retry-policy", "retry-policy", "Adds retry-with-backoff behaviour to failing operations.",
                    List.of("Operation"), "Operation"),
            new SkillEntry("http-mock", "http-mock", "Stubs outgoing HTTP calls in tests.",
                    List.of("TestContext"), "MockServer"));

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile String modelReply = "B";

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                    + modelReply + "\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void idSelectorSelectsTheCandidateTheModelNames() {
        LlmSelector selector = new LlmSelector(baseUrl, "test-model", ID);
        assertThat(selector.select("Add retries to the HTTP client", THREE)).isEqualTo("retry-policy");
    }

    @Test
    void idSelectorSendsTheLetteredDecisionPrompt() {
        new LlmSelector(baseUrl, "test-model", ID)
                .select("Add retries to the HTTP client", THREE);
        assertThat(lastRequestBody.get())
                .contains("\"model\":\"test-model\"")
                .contains("A. http-client")
                .contains("Answer with the single letter of the correct option.");
    }

    @Test
    void oodSelectorSelectsTheCandidateTheModelNumbers() {
        modelReply = "2";
        LlmSelector selector = new LlmSelector(baseUrl, "test-model", OOD);
        assertThat(selector.select("Add retries to the HTTP client", THREE)).isEqualTo("retry-policy");
        assertThat(lastRequestBody.get()).contains("Reply with only the number of the best skill");
    }

    @Test
    void aReplyThatNamesNoCandidateIsAMiss() {
        modelReply = "I cannot decide between these";
        LlmSelector selector = new LlmSelector(baseUrl, "test-model", ID);
        assertThat(selector.select("Add retries to the HTTP client", THREE)).isNull();
    }

    @Test
    void anEmptyCandidateSetMakesNoModelCall() {
        LlmSelector selector = new LlmSelector(baseUrl, "test-model", ID);
        assertThat(selector.select("anything", List.of())).isNull();
        assertThat(requestCount.get()).isZero();
    }

    @Test
    void aDownEndpointFailsWithAClearErrorNamingIt() {
        LlmSelector selector = new LlmSelector("http://127.0.0.1:1/v1", "test-model", ID);
        assertThatThrownBy(() -> selector.select("Add retries to the HTTP client", THREE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http://127.0.0.1:1/v1");
    }
}
