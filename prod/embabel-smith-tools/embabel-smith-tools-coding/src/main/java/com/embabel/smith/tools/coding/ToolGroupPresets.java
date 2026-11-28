/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The named presets this addon defines, and the two names its preset mechanism is known
 * by: the property an operator sets, and the property source the resulting switches
 * arrive in.
 *
 * <p>
 * <b>A preset is shorthand for the switches, not a second gate.</b> Selecting one does
 * not stand beside {@code smith.tools.<group>.enabled}, it writes those switches — which
 * is what keeps the precedence claim honest. An explicit switch still wins, and it wins
 * because Spring's property-source ordering says so, not because a second implementation
 * of precedence happened to remember to check the switch first. The consequence is stated
 * rather than hidden: a preset decides <em>every</em> group this addon contains, so
 * selecting one that names only some of them switches the rest off.
 *
 * <p>
 * <b>The vocabulary is this addon's own.</b> A preset name is a key in a curated map, and
 * a name that is not a key is not a preset this addon can honour. It is reported by
 * {@link CodingToolsBootReport} and contributes nothing at all — the half-applied
 * alternative, taking the groups that happen to match and leaving the rest at their
 * defaults, would register a set no operator asked for and no report could name. A preset
 * naming a group that lives in an addon this deployment does not carry is the same case
 * from the other side: nothing here can apply it, so nothing here applies any of it.
 *
 * <p>
 * <b>Where a second family would add itself.</b> Its submodule contributes its own
 * {@link #PRESETS} entries and its own groups; the coding submodule is unchanged, which
 * is what the tool-addon-modules rule "a second family is a new submodule" asks for in
 * practice.
 *
 * @author Ivan Rodriguez
 */
public final class ToolGroupPresets {

	/**
	 * The property that selects a preset. Read as a gate, like the per-group switches, so
	 * it is deliberately absent from {@link CodingToolsProperties}.
	 */
	public static final String PRESET_PROPERTY = "smith.tools.preset";

	/**
	 * The name of the property source a selected preset contributes. The boot report
	 * reads it to say which source decided a group, so this constant is the whole record
	 * of authorship — a value found in a source of this name was written by a preset, and
	 * a value found anywhere else was written by the deployment.
	 */
	public static final String SOURCE_NAME = "smithToolsPresets";

	/** The one preset this addon defines: everything the coding family can offer. */
	static final String CODING = "coding";

	/**
	 * The curated presets. The value is the set of groups the preset asks for; every
	 * group this addon contains but a preset does not list is switched off by that
	 * preset, which is what "sets the group switches together" means.
	 */
	private static final Map<String, Set<String>> PRESETS = Map.of(CODING, CodingToolGroups.ROLES);

	private ToolGroupPresets() {
	}

	/**
	 * The groups a named preset asks for.
	 * @param preset the preset name as the property carries it, lower-cased
	 * @return the groups it asks for, or empty when this addon defines no such preset —
	 * empty is the case the boot report exists to say out loud
	 */
	public static Optional<Set<String>> named(String preset) {
		return Optional.ofNullable(PRESETS.get(preset));
	}

	/**
	 * The switches a named preset writes, one per group this addon contains.
	 * @param groups the groups the preset asks for, from {@link #named(String)}
	 * @return {@code smith.tools.<group>.enabled} to {@code "true"} or {@code "false"},
	 * in group order. Every group this addon contains is written, including the ones the
	 * preset leaves out — a group a preset does not name is switched off by it rather
	 * than left at its default, which is what makes a preset a decision rather than a
	 * suggestion. Nothing here is skipped because a switch already exists: the source the
	 * values arrive in is placed below the deployment's, so the switch that wins is the
	 * one the deployment wrote without this method having to know it did
	 */
	public static Map<String, String> switchesFor(Set<String> groups) {
		Map<String, String> switches = new LinkedHashMap<>();
		for (String group : CodingToolGroups.ROLES) {
			switches.put(CodingToolsBootReport.PREFIX + group + CodingToolsBootReport.ENABLED_SUFFIX,
					groups.contains(group) ? "true" : "false");
		}
		return switches;
	}

	/**
	 * The preset names this addon defines, for the messages that have to list what it
	 * does know rather than only what it does not.
	 * @return the names, sorted
	 */
	public static Set<String> names() {
		return new TreeSet<>(PRESETS.keySet());
	}

}
