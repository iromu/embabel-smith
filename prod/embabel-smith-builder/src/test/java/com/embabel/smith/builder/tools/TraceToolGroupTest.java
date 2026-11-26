/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.smith.common.trace.TraceRecord;
import com.embabel.smith.common.trace.TraceStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code traces} group: the window and the regression check, plus the answer the
 * builder CLI gets when nothing wired a store.
 *
 * <p>
 * <b>Why the fake store lives here.</b> The builder has no trace-store implementation of
 * its own — {@code JsonlTraceStore} is in {@code embabel-smith-runtime}, which this
 * module does not depend on — so the driver's store is the only one a tool can read, and
 * a test has to supply one. The store-less case is asserted too, because that is what an
 * ordinary {@code smith build} does today: the honest answer is "no store is wired", and
 * a tool that invented a path to {@code ~/.smith/traces} would be the model-named-path
 * failure this surface exists to avoid.
 *
 * @author Ivan Rodriguez
 */
class TraceToolGroupTest {

	private static ToolGroup group(ToolSurface surface) {
		return new TraceToolGroup().traceTools(surface);
	}

	private static ToolSurface openedTo(TraceStore store) {
		ToolSurface surface = new ToolSurface();
		surface.openedTraces(store);
		return surface;
	}

	private static TraceRecord run(String spec, boolean success, String error) {
		return new TraceRecord("run-" + spec + '-' + System.nanoTime(), Instant.now(),
				new TraceRecord.SourceRef("folder:/lib", null, null), new TraceRecord.PackRef(spec, "hash"), spec, "in",
				"GOAP", "trusted", List.of(), success, error, success ? "ok" : null);
	}

	@Test
	void theGroupIsRegisteredUnderTheRoleATraceStepDeclares() {
		assertThat(group(new ToolSurface()).getMetadata().getRole())
			.as("the role a step's toolGroups entry resolves against, and the constant the bean states")
			.isEqualTo(TraceToolGroup.ROLE)
			.isEqualTo("traces");
	}

	@Test
	void theGroupCarriesTheTwoTraceToolsAndNamesTheirArguments() {
		List<Tool> tools = group(new ToolSurface()).getTools();

		assertThat(tools).extracting((tool) -> tool.getDefinition().getName())
			.containsExactlyInAnyOrder(TraceToolGroup.TRACE_WINDOW, TraceToolGroup.DETECT_REGRESSIONS);
		assertThat(tools).allSatisfy((tool) -> assertThat(tool.getDefinition().getInputSchema().getParameters())
			.extracting(Tool.Parameter::getName)
			.containsExactly(TraceToolGroup.SPEC));
		assertThat(tools).allSatisfy(
				(tool) -> assertThat(tool.getDefinition().getInputSchema().toJsonSchema()).doesNotContain("arg0"));
	}

	@Test
	void theWindowReportsTheRunsAndTheirFailures() {
		TraceStore store = store(run("billing", true, null), run("billing", false, "action exploded"));

		String window = TraceToolGroup.window(openedTo(store), "billing");

		assertThat(window).contains("2 recorded runs of 'billing'").contains("1 failed").contains("action exploded");
	}

	@Test
	void theRegressionCheckCountsAFailedRunAndExcludesAPolicyBlock() {
		TraceStore store = store(run("billing", false, "Run blocked by trust tier: untrusted"),
				run("billing", false, "action exploded"));

		String report = TraceToolGroup.regressions(openedTo(store), "billing");

		assertThat(report).as("one real failure, one policy block the check excludes")
			.contains("1 failed runs of 'billing'");
		assertThat(report).contains("lifecycle sidecar");
	}

	@Test
	void aSpecWithNoRunsIsReportedAsHavingNoneRatherThanAsClean() {
		assertThat(TraceToolGroup.regressions(openedTo(store(run("other", true, null))), "billing"))
			.contains("No recorded runs")
			.contains("other");
	}

	@Test
	void noStoreIsAnsweredHonestlyRatherThanWithAnEmptyWindow() {
		assertThat(TraceToolGroup.window(new ToolSurface(), "billing")).isEqualTo(TraceToolGroup.NO_STORE)
			.contains("No trace store is wired");
	}

	private static TraceStore store(TraceRecord... records) {
		return new TraceStore() {

			private final List<TraceRecord> stored = new ArrayList<>(List.of(records));

			@Override
			public void append(TraceRecord record) {
				this.stored.add(record);
			}

			@Override
			public ReadResult read() {
				return new ReadResult(List.copyOf(this.stored), List.of());
			}
		};
	}

	@Nested
	@SpringBootTest
	class OnABootedPlatform {

		@Autowired
		AgentPlatform platform;

		@Test
		void theBootedPlatformPublishesTheTraceGroupAStepCanDeclare() {
			List<String> roles = this.platform.getToolGroupResolver()
				.availableToolGroups()
				.stream()
				.map(ToolGroupDescription::getRole)
				.toList();

			assertThat(roles).as("roles the platform can resolve a toolGroups entry to").contains(TraceToolGroup.ROLE);
		}

	}

}
