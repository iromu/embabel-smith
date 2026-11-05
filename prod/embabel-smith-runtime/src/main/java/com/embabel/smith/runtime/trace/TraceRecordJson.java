package com.embabel.smith.runtime.trace;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JSON (de)serialization of {@link TraceRecord} for the JSONL trace store.
 * {@link java.time.Instant} is serialized as an ISO-8601 string via the jsr310 module.
 */
public class TraceRecordJson {

    private final ObjectMapper mapper;

    public TraceRecordJson() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public String serialize(TraceRecord record) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             JsonGenerator generator = mapper.getFactory().createGenerator(out, JsonEncoding.UTF8)) {
            mapper.writeValue(generator, record);
            generator.flush();
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot serialize trace record: " + e.getMessage(), e);
        }
    }

    /**
     * Parses one JSONL line.
     *
     * @throws IllegalArgumentException when the line is not a valid trace record
     */
    public TraceRecord deserialize(String line) {
        try {
            return mapper.readValue(line, TraceRecord.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("not a valid trace record: " + e.getMessage(), e);
        }
    }
}
