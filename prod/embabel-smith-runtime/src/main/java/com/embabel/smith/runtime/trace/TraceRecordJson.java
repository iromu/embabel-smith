/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.runtime.trace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON (de)serialization of {@link TraceRecord} for the JSONL trace store.
 * {@link java.time.Instant} is serialized as an ISO-8601 string via the jsr310 module.
 *
 * @author Ivan Rodriguez
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
				JsonGenerator generator = this.mapper.getFactory().createGenerator(out, JsonEncoding.UTF8)) {
			this.mapper.writeValue(generator, record);
			generator.flush();
			return out.toString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("cannot serialize trace record: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Parses one JSONL line.
	 * @param line the JSONL line to parse
	 * @return the parsed trace record
	 * @throws IllegalArgumentException when the line is not a valid trace record
	 */
	public TraceRecord deserialize(String line) {
		try {
			return this.mapper.readValue(line, TraceRecord.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("not a valid trace record: " + ex.getMessage(), ex);
		}
	}

}
