package com.embabel.smith.retrieval.remote;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteEmbedderTest {

    @Test
    void embedManyReturnsVectorsInInputOrder() {
        RemoteEmbedder embedder = StubEmbedding.embedder(text -> switch (text) {
            case "a" -> new double[]{1, 0};
            case "b" -> new double[]{0, 1};
            default -> new double[]{0, 0};
        });
        double[][] out = embedder.embedMany(List.of("a", "b"));
        assertThat(out.length).isEqualTo(2);
        assertThat(out[0]).containsExactly(1, 0);
        assertThat(out[1]).containsExactly(0, 1);
    }

    @Test
    void embedIsFirstElementOfSingleBatch() {
        RemoteEmbedder embedder = StubEmbedding.embedder(text -> new double[]{0.5, 0.5});
        assertThat(embedder.embed("x")).containsExactly(0.5, 0.5);
    }

    @Test
    void requestCountCountsEachBatchOnce() {
        RemoteEmbedder embedder = StubEmbedding.embedder(text -> new double[]{1});
        assertThat(embedder.requestCount()).isZero();
        embedder.embedMany(List.of("a", "b", "c")); // one request for a batch of three
        embedder.embed("d");
        assertThat(embedder.requestCount()).isEqualTo(2);
    }

    @Test
    void modelIsExposed() {
        assertThat(StubEmbedding.embedder(text -> new double[]{1}).model()).isEqualTo("stub-model");
    }

    @Test
    void httpTransportRoundTripsAgainstLocalServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> auth = new AtomicReference<>();
        server.createContext("/v1/embeddings", exchange -> {
            List<String> header = exchange.getRequestHeaders().get("Authorization");
            auth.set(header == null || header.isEmpty() ? null : header.get(0));
            String body = "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}";
            exchange.sendResponseHeaders(200, body.length());
            exchange.getResponseBody().write(body.getBytes());
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        try (RemoteEmbedder embedder = RemoteEmbedder.create("http://localhost:" + port + "/v1", "secret", "m")) {
            assertThat(embedder.embed("hi")).containsExactly(0.1, 0.2, 0.3);
            assertThat(auth.get()).isEqualTo("Bearer secret");
        } finally {
            server.stop(0);
        }
    }
}
