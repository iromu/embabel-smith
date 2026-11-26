/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.SkillPublishable;
import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.builder.security.Finding;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.builder.skill.script.GraalJsSkillScriptExecutionEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reader's half of the skill's executable surface: a folder that {@code publish}
 * wrote, hosted by {@link PublishedSkillHost} and read as the tool list a request is
 * answered with.
 *
 * <p>
 * <b>Why this is not the publisher's seam test again.</b>
 * {@code publish/PublishedSkillScriptToolSurfaceTest} asks what a published folder
 * <em>contains</em> when somebody else's loader reads it. This one asks what a host makes
 * of it: that the host reads the directory rather than re-deriving the skill from a spec,
 * that it works with no engine at all, that a script in it actually executes, and that a
 * folder the loader refuses is reported instead of vanishing. The first of those is why
 * every case here starts by publishing through {@link FolderPublisher} rather than by
 * writing files: a host over a folder the builder wrote itself would still pass if the
 * publisher changed its layout and the host quietly started hosting nothing.
 *
 * <p>
 * <b>The two surfaces that speak about a script's reach.</b> The scanner and the engine,
 * and this host is not a third — so one case here asserts the absence directly: nothing
 * in the host's own signature takes a trust tier, with the scan report as the control
 * that the check would have found one had it been wired (a tier is a set of tool-group
 * roles and a script declares none, so a tier check here could only ever read an empty
 * list). Another case shows the host adds no allowance either: a script the scanner
 * refuses to publish is offered by the host as-is, because "it should never have been
 * published" is the branch's position, not a runtime rule this class gets to invent.
 *
 * <p>
 * Offline: no model and no endpoint. The model call a host feeds is recorded elsewhere,
 * at {@code cli/SkillRunCommandTest}.
 *
 * @author Ivan Rodriguez
 */
class PublishedSkillHostTest {

	private static final String NAME = "cold-store-goods-receipt";

	/**
	 * Embabel's rule: skill name, underscore, the script's file base name, unsanitized.
	 */
	private static final String SCRIPT_TOOL = "cold-store-goods-receipt_check-temperature";

	private static final String SCRIPTED_BODY = "## Workflow\n1. Read the carrier temperature record.\n"
			+ "2. Run scripts/check-temperature.js and quote what it printed.\n";

	private static final String SCRIPT = "const limit = Number(args[0] ?? '4');\n"
			+ "const reading = Number(stdin.trim());\n" + "if (!Number.isFinite(reading)) {\n"
			+ "  console.error('no temperature reading on stdin');\n" + "  process.exit(2);\n" + "}\n"
			+ "console.log(reading > limit ? 'reject and escalate' : 'accept and file the record');\n";

	private static final EvalSuite EVALS = new EvalSuite(NAME, List.of(new EvalSuite.Eval(1, "receive a chilled pallet",
			"accepted after a check", List.of(), List.of("must apply FEFO"))));

	@Test
	void hostsAPublishedFolderAsTheToolsARequestIsOffered(@TempDir Path library) throws Exception {
		publish(library, skill(SCRIPTED_BODY, "scripts/check-temperature.js", SCRIPT));

		Skills hosted = new PublishedSkillHost(library, engine()).skills();

		// The three tools every skill brings plus the one the engine turns the script
		// into.
		// The name is unsanitized because Embabel joins the two names without touching
		// them,
		// and the hyphen is the evidence that this list came from the published folder
		// rather
		// than from something that rebuilt the skill and sanitized it on the way.
		assertThat(toolNames(hosted)).contains("activate", "listResources", "readResource");
		assertThat(toolNames(hosted)).contains(SCRIPT_TOOL);
		assertThat(activate(hosted)).contains("Read the carrier temperature record");
	}

	@Test
	void withNoEngineItIsStillAWorkingHostAndOnlyTheScriptToolIsMissing(@TempDir Path library) throws Exception {
		publish(library, skill(SCRIPTED_BODY, "scripts/check-temperature.js", SCRIPT));

		// The one-argument constructor is the ordinary run: nothing offered an engine, so
		// the host asks for none and Embabel's default claims no language.
		Skills hosted = new PublishedSkillHost(library).skills();

		// Not a quieter host — a host that still activates and still reads the body back,
		// so
		// the missing tool below is the switch's decision and not a folder that failed to
		// load. That distinction is the whole containment claim.
		assertThat(activate(hosted)).contains("Read the carrier temperature record");
		assertThat(toolNames(hosted)).contains("activate", "listResources", "readResource").doesNotContain(SCRIPT_TOOL);
	}

	@Test
	void aScriptInThePublishedFolderExecutes(@TempDir Path library) throws Exception {
		publish(library, skill(SCRIPTED_BODY, "scripts/check-temperature.js", SCRIPT));

		Skills hosted = new PublishedSkillHost(library, engine()).skills();

		// The first time a published script runs in prod/: the bytes publish wrote,
		// through
		// the framework's loader, through the engine's sandbox, printing what the script
		// prints. 9 against the default limit of 4 is a breach.
		assertThat(call(hosted, SCRIPT_TOOL, "{\"args\": [], \"stdin\": \"9\", \"inputFiles\": []}"))
			.contains("reject and escalate");
	}

	@Test
	void theHostConsultsNoTrustTierAndTheScanReportIsTheControlThatSaysSo(@TempDir Path library) throws Exception {
		// An absence asserted as a type check, not as a name match: a field or argument
		// of
		// tier type anywhere in the host would be a gate, and the same scan over
		// ScanReport
		// finds a tier, which is what makes the host's silence a decision rather than a
		// check
		// that could never have fired.
		Set<String> hostTypes = typesOf(PublishedSkillHost.class);
		assertThat(hostTypes).as("the host takes no trust tier, so it cannot gate a script on one")
			.noneMatch((type) -> type.contains("TrustTier"));
		assertThat(typesOf(ScanReport.class)).as("control: the same scan finds a tier where one is reported")
			.anyMatch((type) -> type.contains("TrustTier"));
	}

	@Test
	void aScriptTheScannerRefusesToPublishIsNotSomethingTheHostInventsAnAllowanceFor(@TempDir Path library)
			throws Exception {
		String reaching = "const r = await fetch('https://example.invalid');\nconsole.log(await r.text());\n";
		// A body that names the script it does ship: a body naming some other file would
		// make
		// the loader drop the skill, and then the missing tool below would read as the
		// host
		// having refused it.
		String body = "## Workflow\n1. Fetch the record with scripts/lookup.js.\n2. Report it.\n";
		publish(library, skill(body, "scripts/lookup.js", reaching));

		Skills hosted = new PublishedSkillHost(library, engine()).skills();

		// The host runs the folder it was given: no extra denial of its own. The branch's
		// position is that this script should never have been published, and the surface
		// that
		// says so is the scanner, naming the reason — not a reader that quietly drops the
		// tool
		// and leaves the build report green.
		assertThat(toolNames(hosted)).contains("cold-store-goods-receipt_lookup");
		ScanReport scan = new AgentSpecSecurityScanner().scanSkill(NAME, List.of(body),
				Map.of("scripts/lookup.js", reaching));
		assertThat(scan.findingsIn(Finding.CAT_SCRIPT_NETWORK)).as("the scanner is the surface that objects")
			.isNotEmpty();
		assertThat(scan.hasCritical()).isTrue();
	}

	@Test
	void aFolderTheLoaderRefusesIsNamedRatherThanSilentlyAbsent(@TempDir Path library) throws Exception {
		publish(library, skill(SCRIPTED_BODY, "scripts/check-temperature.js", SCRIPT));
		// A body that points at a file the folder does not carry: Embabel's loader drops
		// the
		// whole skill over it and logs the reason where this process's caller cannot see
		// it.
		publish(library, new Skill("dangling-references", "A skill whose body names a file it lacks.",
				"## Workflow\n1. Read references/indicators.md and apply it.\n"));

		PublishedSkillHost.Loading loading = new PublishedSkillHost(library).loading();

		assertThat(loading.loaded()).containsExactly(NAME);
		assertThat(loading.dropped()).as("the refused folder is reported, not swallowed by the loader's log")
			.singleElement()
			.satisfies((dropped) -> {
				assertThat(dropped.folder().getFileName().toString()).isEqualTo("dangling-references");
				assertThat(dropped.reason()).as("with the loader's own reason").isNotBlank();
			});
	}

	private static Skill skill(String body, String scriptPath, String scriptSource) {
		return new Skill(NAME, "Receive chilled goods and reject a temperature breach.", body,
				List.of(new SkillScript(scriptPath, scriptSource)));
	}

	private static void publish(Path library, Skill skill) throws Exception {
		new FolderPublisher(PublishTarget.parse("folder:" + library)).publish(new SkillPublishable(skill, EVALS,
				new LifecycleStamper().stamp(SkillContentHash.of(skill, EVALS), 0.75)), false, false);
	}

	private static SkillScriptExecutionEngine engine() {
		return new GraalJsSkillScriptExecutionEngine();
	}

	private static List<String> toolNames(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	private static String activate(Skills skills) {
		return call(skills, "activate", "{\"name\": \"" + NAME + "\"}");
	}

	private static String call(Skills skills, String toolName, String inputJson) {
		Tool tool = skills.tools()
			.stream()
			.filter((candidate) -> candidate.getDefinition().getName().equals(toolName))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("the host offers no " + toolName + " tool"));
		Tool.Result result = tool.call(inputJson);
		if (result instanceof Tool.Result.Error error) {
			return "[tool error] " + error.getMessage();
		}
		if (result instanceof Tool.Result.Text text) {
			return text.getContent();
		}
		return String.valueOf(result);
	}

	/**
	 * Every type the class names in a field, a constructor or a method — the way to
	 * assert that something is not wired in without trusting a name to have been spelled
	 * the way a grep would find it.
	 * @param type the class to read.
	 * @return the names of every type it mentions.
	 */
	private static Set<String> typesOf(Class<?> type) {
		Stream<String> fields = Arrays.stream(type.getDeclaredFields()).map((field) -> field.getType().getName());
		Stream<String> constructors = Arrays.stream(type.getDeclaredConstructors())
			.flatMap((ctor) -> Arrays.stream(ctor.getParameterTypes()).map(Class::getName));
		Stream<String> methods = Arrays.stream(type.getDeclaredMethods())
			.flatMap((method) -> Stream.concat(Arrays.stream(method.getParameterTypes()).map(Class::getName),
					Stream.of(method.getReturnType().getName())));
		return Stream.of(fields, constructors, methods).flatMap((names) -> names).collect(Collectors.toSet());
	}

}
