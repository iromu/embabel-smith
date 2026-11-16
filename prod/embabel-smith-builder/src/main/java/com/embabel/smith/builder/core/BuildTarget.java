/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * The artifact a build produces. {@link #AGENT_SPEC} compiles the description into an
 * executable pack, validated by deterministic eval tasks; {@link #SKILL} compiles it into
 * a portable {@code SKILL.md}, validated by the with/without-skill lift. The two share
 * the publish, security and lifecycle stages but not the generation or the gate, so the
 * build dispatches on this selector.
 *
 * @author Ivan Rodriguez
 */
public enum BuildTarget {

	/** An Embabel agent-spec pack (the default). */
	AGENT_SPEC("agent-spec"),

	/** An Agent Skill folder in the Agent Skills format. */
	SKILL("skill");

	private final String id;

	BuildTarget(String id) {
		this.id = id;
	}

	/**
	 * The user-facing name of the target, as accepted by {@code --target}.
	 * @return the target id.
	 */
	public String id() {
		return this.id;
	}

	/**
	 * Parses a target name strictly.
	 * @param value the name to parse, in either id ({@code agent-spec}) or constant
	 * ({@code AGENT_SPEC}) form, case-insensitively.
	 * @return the target named.
	 * @throws IllegalArgumentException if the value names no target.
	 */
	public static BuildTarget parse(String value) {
		for (BuildTarget target : values()) {
			if (target.id.equalsIgnoreCase(value) || target.name().equalsIgnoreCase(value)) {
				return target;
			}
		}
		throw new IllegalArgumentException(
				"Unknown build target '" + value + "': expected " + AGENT_SPEC.id + " or " + SKILL.id);
	}

}
