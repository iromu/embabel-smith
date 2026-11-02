package com.embabel.smith.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the remote-action sidecar (POC 16). These pin the three-endpoint contract that
 * Embabel's {@code embabel-agent-remote} client consumes — the catalog, the (empty) types, and the
 * deterministic execute — without booting a platform or calling a model.
 */
class RemoteActionSidecarTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private RemoteActionSidecar sidecar;
    private String base;

    @BeforeEach
    void start() throws Exception {
        sidecar = new RemoteActionSidecar();
        sidecar.start(0); // ephemeral port
        base = sidecar.baseUrl();
    }

    @AfterEach
    void stop() {
        sidecar.stop();
    }

    @Test
    void exposesTheActionCatalog() throws Exception {
        JsonNode actions = get("/api/v1/actions");
        assertThat(actions.isArray()).isTrue();
        assertThat(actions.size()).isEqualTo(1);

        JsonNode action = actions.get(0);
        assertThat(action.path("name").asText()).isEqualTo("remoteDigest");
        // The input variable is named "userInput" so it matches the runAgentFrom key, and the
        // output is named "it" so it matches the goal's output condition — that is how GOAP chains
        // UserInput -> remoteDigest -> BackgroundMessage. pre/post are empty: the preconditions
        // come from the input binding, not a separate condition.
        assertThat(action.path("inputs").get(0).path("name").asText()).isEqualTo("userInput");
        assertThat(action.path("inputs").get(0).path("type").asText())
                .isEqualTo(RemoteActionSidecar.USER_INPUT);
        assertThat(action.path("outputs").get(0).path("name").asText()).isEqualTo("it");
        assertThat(action.path("outputs").get(0).path("type").asText())
                .isEqualTo(RemoteActionSidecar.BACKGROUND_MESSAGE);
        assertThat(action.path("pre").size()).isZero();
        assertThat(action.path("post").size()).isZero();
    }

    @Test
    void typesAreEmptyBecauseTheActionUsesBuiltInTypes() throws Exception {
        JsonNode types = get("/api/v1/types");
        assertThat(types.isArray()).isTrue();
        assertThat(types.size()).isZero();
    }

    @Test
    void executeRunsTheDeterministicAction() throws Exception {
        String body = """
                {"action_name":"remoteDigest","parameters":{"userInput":{"content":"the quick brown fox jumps"}}}
                """;
        JsonNode out = post("/api/v1/actions/execute", body);

        String content = out.path("content").asText();
        assertThat(content).contains("5 words");
        assertThat(content).contains("25 chars");
        assertThat(content).contains("the");
        assertThat(out.path("timestamp").asText()).isNotBlank();
    }

    @Test
    void executeRejectsAnUnknownAction() throws Exception {
        String body = """
                {"action_name":"nope","parameters":{"userInput":{"content":"x"}}}
                """;
        HttpResponse<String> resp = postRaw("/api/v1/actions/execute", body);
        assertThat(resp.statusCode()).isEqualTo(500);
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        return mapper.readTree(resp.body());
    }

    private JsonNode post(String path, String body) throws Exception {
        HttpResponse<String> resp = postRaw(path, body);
        assertThat(resp.statusCode()).isEqualTo(200);
        return mapper.readTree(resp.body());
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
