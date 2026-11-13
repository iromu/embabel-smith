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

package com.embabel.smith.builder.core;

/**
 * The generation technique a build selects. Both techniques produce the shared
 * {@link com.embabel.smith.common.model.AgentSpec} format, so every downstream stage
 * (validate, eval, security, lifecycle, publish) is technique-independent.
 *
 * @author Ivan Rodriguez
 */
public enum GenerationTechnique {

	/** One model call emits the whole spec as JSON (the default). */
	SINGLE_SHOT("single-shot"),

	/** Embabel's NL compiler, which compiles the description into a draft. */
	EMBABEL("embabel");

	private final String id;

	GenerationTechnique(String id) {
		this.id = id;
	}

	/**
	 * The user-facing name of the technique, as accepted by {@code --technique} and the
	 * studio's generate request.
	 * @return the technique id.
	 */
	public String id() {
		return this.id;
	}

	/**
	 * Parses a technique name strictly.
	 * @param value the name to parse, in either id ({@code single-shot}) or constant
	 *        ({@code SINGLE_SHOT}) form, case-insensitively.
	 * @return the technique named.
	 * @throws IllegalArgumentException if the value names no technique.
	 */
	public static GenerationTechnique parse(String value) {
		for (GenerationTechnique technique : values()) {
			if (technique.id.equalsIgnoreCase(value) || technique.name().equalsIgnoreCase(value)) {
				return technique;
			}
		}
		throw new IllegalArgumentException("Unknown generation technique '" + value + "': expected "
				+ SINGLE_SHOT.id + " or " + EMBABEL.id);
	}

	/**
	 * Parses a technique name leniently: anything unrecognized means the default.
	 * @param value the name to parse, possibly null or blank.
	 * @return the technique named, or {@link #SINGLE_SHOT} when unrecognized.
	 */
	public static GenerationTechnique from(String value) {
		try {
			return parse(value);
		}
		catch (IllegalArgumentException ex) {
			return SINGLE_SHOT;
		}
	}

}
