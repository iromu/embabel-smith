package com.embabel.smith.common.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * The single {@link ObjectMapper} used to (de)serialize skill-pack YAML.
 *
 * <p>Configured to omit null fields on write and to tolerate unknown fields on read, so the
 * reader can also ingest richer Embabel-emitted step files (which carry fields such as {@code llm}
 * that the pure {@code smith-common} model does not need to retain).
 */
public final class Yaml {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Yaml() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
