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

package com.embabel.smith.common.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * The single {@link ObjectMapper} used to (de)serialize agent spec YAML.
 *
 * <p>
 * Configured to omit null fields on write and to tolerate unknown fields on read, so the
 * reader can also ingest richer Embabel-emitted step files (which carry fields such as
 * {@code llm} that the pure {@code smith-common} model does not need to retain).
 *
 * @author Ivan Rodriguez
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
