/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Writes the switches a selected preset stands for, so a preset is shorthand rather than
 * a second gate.
 *
 * <p>
 * <b>Why the switches are the route.</b> A deployment that names
 * {@code smith.tools.preset=coding} is asking for the groups that
 * {@link CodingToolsAutoConfiguration} gates on {@code smith.tools.<group>.enabled}. If
 * the preset were evaluated separately from those gates, precedence would live in a
 * second place, and a preset and a switch would be two hands on one wheel with nothing
 * recording which turned it. Writing the switches instead means the rule "an explicit
 * switch beats a preset" is Spring's own property-source precedence, which the bean
 * conditions already obey, and the boot report's claim about the deciding source is a
 * fact about the environment rather than a restatement of an if-statement.
 *
 * <p>
 * <b>What is contributed, and what decides.</b> Every switch the preset stands for, one
 * per group this addon contains, written the way the deployment would have written it —
 * and nothing is skipped here on the grounds that something else already set it. That
 * would be a second implementation of the precedence rule, in the one place the rule is
 * already carried by the ordering: the source goes in with {@code addLast}, below the
 * deployment's config files, its environment variables and a test's inlined properties,
 * so a switch the deployment wrote resolves ahead of the entry sitting under it and this
 * class never has to know which of its own entries it would have lost. The consequence is
 * the one {@link ToolGroupPresets} states rather than hides: a preset decides every group
 * this addon contains, so one naming only some of them switches the rest off.
 *
 * <p>
 * <b>A preset this addon does not define contributes nothing at all.</b> Not its matching
 * part, not its default part: a half-applied preset would register a set of groups nobody
 * asked for and leave the boot report describing a configuration that no single property
 * produced. The name is reported by {@link CodingToolsBootReport} instead, with the
 * presets this addon does define, which is the same refusal the expose map practises on
 * an entry it has no recipe for — the difference being that an expose entry can name a
 * class, so it refuses the boot, while a preset name can only name another addon's
 * bundle, so it says there was nothing here to apply. Leaving no source behind at all is
 * also what keeps the unset case and the unrecognised case the same story: both are boots
 * in which nothing here was written by a preset.
 *
 * <p>
 * <b>What a native image cannot be told.</b> This is a runtime contribution, and an AOT
 * build decides the {@code @ConditionalOnProperty} gates while it compiles, so a preset
 * that would have switched a group on cannot add back a bean {@code process-aot} froze
 * out. The caveat belongs to the gates themselves, not to this class — a preset inherits
 * it by being a way of setting them.
 *
 * @author Ivan Rodriguez
 */
// Ordered last on purpose, because the ordering is the precedence. Running after the
// deployment's own sources are in place puts this one underneath all of them, which is
// the
// whole of why an explicit switch beats a preset; ordered earlier, this source would sit
// above config data and quietly outrank the file the operator edited.
@Order(Ordered.LOWEST_PRECEDENCE)
public class CodingToolsPresetEnvironmentPostProcessor implements EnvironmentPostProcessor {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String asked = environment.getProperty(ToolGroupPresets.PRESET_PROPERTY, "").trim().toLowerCase();
		Optional<Set<String>> groups = ToolGroupPresets.named(asked);
		if (groups.isEmpty()) {
			// Nothing this addon defines answers to that name, and a preset that is not
			// set has the same effect: no name, nothing to apply. Contributing part of an
			// unrecognised one is the half-applied case this method exists to avoid.
			return;
		}
		// Object because that is the value type a property source holds. The switches are
		// written as the text a switch is written in, so the contributed source is
		// indistinguishable from one the deployment wrote by hand, which is the point of
		// taking the preset out of the decision altogether.
		Map<String, Object> contributed = new LinkedHashMap<>(ToolGroupPresets.switchesFor(groups.get()));
		environment.getPropertySources().addLast(new MapPropertySource(ToolGroupPresets.SOURCE_NAME, contributed));
	}

}
