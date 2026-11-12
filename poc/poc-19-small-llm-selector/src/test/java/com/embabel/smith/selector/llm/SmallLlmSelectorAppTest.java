package com.embabel.smith.selector.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.gliner.ShadowingRunner;
import com.embabel.smith.retrieval.gliner.SpecSelector;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class SmallLlmSelectorAppTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startStub() throws IOException {
        // The stubbed model always picks the first option — the canonical spec is listed first.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"A\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
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
    void theFiveMethodsAreRegisteredInTableOrder() {
        SpecSelector stubGliner2 = (query, candidates) -> candidates.get(0).id();
        Map<String, SpecSelector> methods =
                SmallLlmSelectorApp.benchmarkMethods(baseUrl, "test-model", stubGliner2);
        assertThat(methods.keySet()).containsExactly(
                "transformer (GLiNER2)",
                "lexical (word TF)",
                "dense (feature hash)",
                "llm-id (Qwen3-0.6B)",
                "llm-ood (Qwen3-0.6B)");
    }

    @Test
    void theComparisonTableHasOneRowPerMethod() throws IOException {
        SpecSelector stubGliner2 = (query, candidates) -> candidates.get(0).id();
        Map<String, SpecSelector> methods =
                SmallLlmSelectorApp.benchmarkMethods(baseUrl, "test-model", stubGliner2);
        ShadowingRunner runner = new ShadowingRunner();

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            runner.printComparisonTable(methods);
        } finally {
            System.setOut(original);
        }

        String table = captured.toString(StandardCharsets.UTF_8);
        assertThat(table)
                .contains("method")
                .contains("transformer (GLiNER2)")
                .contains("lexical (word TF)")
                .contains("dense (feature hash)")
                .contains("llm-id (Qwen3-0.6B)")
                .contains("llm-ood (Qwen3-0.6B)")
                .contains("100%"); // the stub always selects the canonical spec
    }

    @Test
    void theRunnerScoresAllFiveOnIdenticalCandidates() {
        SpecSelector stubGliner2 = (query, candidates) -> candidates.get(0).id();
        Map<String, SpecSelector> methods =
                SmallLlmSelectorApp.benchmarkMethods(baseUrl, "test-model", stubGliner2);
        ShadowingRunner runner = new ShadowingRunner();
        // The stubbed model answers "A" (ID) and the canonical spec is option A, so both LLM
        // formats score 1.0; a selector that never answers scores 0.0.
        assertThat(methods.get("llm-id (Qwen3-0.6B)")
                .select("any query", List.of(new SpecEntry("a", "a", "d", List.of(), "t"))))
                .isEqualTo("a");
        assertThat(runner.precisionAt1((query, candidates) -> null, ShadowingRunner.Variant.MATCHING))
                .isZero();
    }
}
