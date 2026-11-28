/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expose map refuses at start-up, and says what it refused.
 *
 * <p>
 * <b>Why these are start-up failures rather than run-time surprises.</b> A composed role
 * that names something the addon cannot build would otherwise register a group that
 * answers with no tools at all — the exact shape a pack's step reaches for and gets
 * nothing from, with the explanation only in a resolver log line nobody reads. Failing
 * the boot instead moves the news to the moment the operator can still act on it, which
 * is also why the message names the property, the entry, and the whole vocabulary.
 *
 * <p>
 * <b>The fully-qualified-class case is the one this map exists to answer.</b> Upstream's
 * own operator-authored roles are built from tool names, and the instinct to write a
 * class name into a property is a normal one. This addon does not instantiate a class a
 * property named, under any spelling, so a class name is refused like any other unknown
 * entry — with a sentence saying why rather than leaving the operator to conclude that
 * the class was mistyped.
 *
 * <p>
 * These run through an {@code ApplicationContextRunner} rather than a booted test class
 * because a context that fails to start cannot be {@code @Autowired} into one.
 *
 * @author Ivan Rodriguez
 */
class ExposeMapRefusalTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(CodingToolsAutoConfiguration.class));

	@Test
	@DisplayName("an entry outside the vocabulary refuses the boot, naming the property and the entry")
	void unknownEntryRefuses() {
		this.runner.withPropertyValues("smith.tools.expose.review=frobnicate")
			.run((context) -> expectRefusal("frobnicate", context));
	}

	@Test
	@DisplayName("and says what it will not do with a class name")
	void classNamedEntryRefusesAndSaysWhy() {
		this.runner.withPropertyValues("smith.tools.expose.review=com.example.WhateverToolGroup")
			.run((context) -> expectRefusal("com.example.WhateverToolGroup", context));
	}

	@Test
	@DisplayName("a role that names no capability refuses too, since it would register an empty group")
	void emptyCapabilityListRefuses() {
		this.runner.withPropertyValues("smith.tools.expose.review=").run((context) -> expectRefusal("", context));
	}

	@Test
	@DisplayName("nothing configured is not a failure — the default boot has no composed roles at all")
	void nothingConfiguredBoots() {
		this.runner.run((context) -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean("exposedToolGroups", List.class)).isEmpty();
		});
	}

	/**
	 * Assert that this boot failed, and that the failure names the entry and the
	 * vocabulary.
	 * @param entry the text the property carried
	 * @param context the context that should not have started
	 */
	private void expectRefusal(String entry, AssertableApplicationContext context) {
		assertThat(context).hasFailed();
		Throwable failure = rootCause(context.getStartupFailure());
		assertThat(failure).isInstanceOf(IllegalStateException.class);
		assertThat(failure.getMessage()).contains("smith.tools.expose.review")
			.contains(entry)
			.contains(ExposedToolGroups.FILE_READ)
			.contains(ExposedToolGroups.SEARCH);
		if (entry.contains(".")) {
			assertThat(failure.getMessage()).contains("never from a class a property names");
		}
	}

	/**
	 * The deepest cause available, since a bean-method failure arrives wrapped.
	 * @param failure the startup failure, possibly nested
	 * @return the innermost cause, or the failure itself when it has none
	 */
	private static Throwable rootCause(Throwable failure) {
		Throwable cause = failure;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause;
	}

}
