package com.embabel.smith.runtime.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceRecordJsonTest {

    private final TraceRecordJson codec = new TraceRecordJson();

    private static TraceRecord sampleRecord() {
        return new TraceRecord(
                "run-1",
                Instant.parse("2026-11-05T12:00:00Z"),
                new TraceRecord.SourceRef("git:example.com/repo.git@main", "main", "abc123"),
                new TraceRecord.PackRef("explainer", "sha256:deadbeef"),
                "explainerDone",
                "Explain how a coffee machine works",
                "GOAP",
                "standard",
                List.of(new TraceRecord.StepRecord("extractTopic", "completed", 1234, "excerpt")),
                true,
                null,
                "output excerpt");
    }

    @Test
    void serializesPerTheDesignSchema() throws Exception {
        JsonNode node = new ObjectMapper().readTree(codec.serialize(sampleRecord()));

        assertThat(node.get("runId").asText()).isEqualTo("run-1");
        assertThat(node.get("timestamp").asText()).isEqualTo("2026-11-05T12:00:00Z");
        assertThat(node.get("source").get("uri").asText()).isEqualTo("git:example.com/repo.git@main");
        assertThat(node.get("source").get("ref").asText()).isEqualTo("main");
        assertThat(node.get("source").get("resolvedAt").asText()).isEqualTo("abc123");
        assertThat(node.get("pack").get("name").asText()).isEqualTo("explainer");
        assertThat(node.get("pack").get("contentHash").asText()).isEqualTo("sha256:deadbeef");
        assertThat(node.get("goal").asText()).isEqualTo("explainerDone");
        assertThat(node.get("input").asText()).isEqualTo("Explain how a coffee machine works");
        assertThat(node.get("planner").asText()).isEqualTo("GOAP");
        assertThat(node.get("trustTier").asText()).isEqualTo("standard");
        assertThat(node.get("steps")).hasSize(1);
        assertThat(node.get("steps").get(0).get("name").asText()).isEqualTo("extractTopic");
        assertThat(node.get("steps").get(0).get("status").asText()).isEqualTo("completed");
        assertThat(node.get("steps").get(0).get("durationMs").asLong()).isEqualTo(1234);
        assertThat(node.get("steps").get(0).get("outputExcerpt").asText()).isEqualTo("excerpt");
        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("error").isNull()).isTrue();
        assertThat(node.get("outputExcerpt").asText()).isEqualTo("output excerpt");
    }

    @Test
    void nullPackAndErrorSerializeAsJsonNull() throws Exception {
        TraceRecord record = new TraceRecord(
                "run-2", Instant.parse("2026-11-05T12:00:00Z"),
                new TraceRecord.SourceRef("folder:/tmp/packs", null, null),
                null, "nope", "input", null, "standard", List.of(), false, "the error", null);

        JsonNode node = new ObjectMapper().readTree(codec.serialize(record));

        assertThat(node.get("pack").isNull()).isTrue();
        assertThat(node.get("source").get("ref").isNull()).isTrue();
        assertThat(node.get("source").get("resolvedAt").isNull()).isTrue();
        assertThat(node.get("planner").isNull()).isTrue();
        assertThat(node.get("steps")).isEmpty();
        assertThat(node.get("success").asBoolean()).isFalse();
        assertThat(node.get("error").asText()).isEqualTo("the error");
        assertThat(node.get("outputExcerpt").isNull()).isTrue();
    }

    @Test
    void roundTrips() {
        assertThat(codec.deserialize(codec.serialize(sampleRecord()))).isEqualTo(sampleRecord());
    }

    @Test
    void rejectsCorruptLines() {
        assertThatThrownBy(() -> codec.deserialize("{not a trace record"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid trace record");
    }
}
