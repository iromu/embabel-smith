/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.smith.builder.core.AgentSpecGenerator;
import com.embabel.smith.builder.core.AgentSpecNormalizer;
import com.embabel.smith.builder.core.BuilderPackPaths;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.builder.security.Finding;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.builder.tools.AgentSpecValidationTool;
import com.embabel.smith.builder.tools.ToolSurface;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The agentic flow's generator: the builder's own {@code specs/builder-agentic-blueprint}
 * pack issues the drafting call, and the deterministic checks run <em>host-side</em>
 * between model calls rather than only inside one. The pack is a classpath resource of
 * this module, addressed through {@link BuilderPackPaths} rather than against the
 * directory a build happened to start in, so the same pack loads from a test, from a
 * launched application and from a jar. The pack's step can already call
 * {@code validatePack}; what this class adds is that a draft cannot come back from the
 * pack unchecked, because the driver itself runs the validator and the scanner over it
 * and, when either reports something, re-invokes the same pack with the findings appended
 * to the same request.
 *
 * <p>
 * <b>Why the forcing is in Java.</b> A step that is told to validate its own draft may
 * not do it: the measured {@code --flow pack} run against the TradingAgents input offered
 * {@code validatePack} and declined it, and the eval then found sixteen dangling type
 * references (the same measurement in {@code docs/08-builder.md}, §Who issues the
 * generation call). A prompt sentence is an invitation; a Java call between two model
 * calls is a guarantee. So the tool stays — it lets a step self-correct on the call it is
 * already making, which is cheaper than a whole re-invocation — and this class is the
 * backstop for the step that did not.
 *
 * <p>
 * <b>What is re-sent.</b> The findings, verbatim, under a heading that says where they
 * came from, appended to the request the step already received. Nothing is hidden from
 * the second call and nothing new is invented for it: the same pack, the same goal, the
 * same documents open. The tool memo in {@link ToolSurface#startRun()} is deliberately
 * not cleared between the two — a second call that re-asks for a document it already read
 * gets the answer it already got, which is the dedupe this surface is required to have.
 *
 * <p>
 * <b>Bounded, and then it stops.</b> {@link #MAX_FORCING_ROUNDS} re-invocations, after
 * which the last draft is returned with the findings it still carries recorded as the
 * draft's issues. The later gates then see exactly what the Java flow's gates see, which
 * is the parity the pack flow was measured on. There is no path back to the Java
 * generator (design D4): the flow that was asked for is the flow that ran.
 *
 * <p>
 * <b>An unresolvable declaration is refused before the model.</b>
 * {@link #refuse(AgentSpec)} checks each group the pack's steps declare against the
 * groups this platform can actually resolve, and refuses the deploy if one names nothing.
 * That check lives here and not in {@code AgentSpecInvokerFactory} because the factory is
 * also the eval and improvement paths, whose specs are machine-generated and declare
 * nothing; {@code eval/ToolGroupBindingTest} is the evidence that a misspelled group
 * otherwise deploys verbatim and resolves to nothing at prompt time. Refusing at the
 * deploy that exists to reach tools is the narrow place the check belongs.
 *
 * <p>
 * <b>The harness flow drives this same loop.</b> {@code HarnessAgentSpecGenerator} is
 * this class with a different pack directory and its own name in the refusals: the
 * forcing, the ceiling and the role check stay one implementation rather than two that
 * can drift apart. What differs between the two flows is the pack — and so who decides
 * what is read next — not the driver.
 *
 * @author Ivan Rodriguez
 */
public class AgenticAgentSpecGenerator implements AgentSpecGenerator {

	/**
	 * The shipped agentic pack, addressed as
	 * {@link PackDrivenAgentSpecGenerator#builderPack()} addresses its own: through the
	 * class loader, at the {@code specs/} resource directory of this module, so the pack
	 * is found whatever directory a build was launched from. Asked for when it is needed
	 * rather than bound at class load, so a pack that is not there fails the build with
	 * the refusal {@code SpecFolders} writes instead of failing the class load.
	 * @return the directory of the pack this flow drives
	 */
	public static Path builderPack() {
		return BuilderPackPaths.directory("builder-agentic-blueprint");
	}

	/**
	 * How many times a draft with findings sends the request again. Two is enough for the
	 * measured failure (a dangling type reference is one edit), and a loop that kept
	 * re-asking until a model volunteered an answer would be a cost with no ceiling.
	 */
	static final int MAX_FORCING_ROUNDS = 2;

	private final InvokerFactory invokers;

	private final AgentPlatform platform;

	private final Path packDir;

	private final AgentSpecReader reader = new AgentSpecReader();

	private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
			false);

	private final AgentSpecValidationTool validator = new AgentSpecValidationTool();

	private final AgentSpecSecurityScanner scanner = new AgentSpecSecurityScanner();

	/**
	 * The name this driver calls itself by in the refusals it raises. The harness flow
	 * shares this class rather than duplicating it: the pack it deploys and the name in
	 * its messages are the only difference, and a second copy of the forcing loop would
	 * be a second place for the ceiling to drift.
	 */
	private final String flowName;

	public AgenticAgentSpecGenerator(InvokerFactory invokers, AgentPlatform platform, Path packDir) {
		this(invokers, platform, packDir, "agentic");
	}

	/**
	 * The shared driver: deploy {@code packDir}, invoke its goal, run the deterministic
	 * checks in Java on what came back, re-invoke with the findings.
	 * @param invokers the factory that deploys the pack and returns its goal invoker
	 * @param platform the platform whose resolvable roles the pack's declarations are
	 * checked against
	 * @param packDir the pack directory to drive
	 * @param flowName what the refusals this driver raises call the flow
	 */
	AgenticAgentSpecGenerator(InvokerFactory invokers, AgentPlatform platform, Path packDir, String flowName) {
		this.invokers = invokers;
		this.platform = platform;
		this.packDir = packDir;
		this.flowName = flowName;
	}

	/**
	 * A refusal in this driver's own voice, so a message says which flow stopped the
	 * build.
	 * @param detail what the message says after the flow name
	 * @return the message prefix
	 */
	private String named(String detail) {
		return this.flowName + " flow: " + detail;
	}

	@Override
	public CompiledDraft generate(String description) {
		return generate(description, null);
	}

	@Override
	public CompiledDraft generate(String description, String model) {
		AgentSpec pack = readPack();
		refuse(pack);
		AgentSpec drafted = null;
		List<String> findings = List.of();
		String request = description;
		for (int round = 0; round <= MAX_FORCING_ROUNDS; round++) {
			String answer = this.invokers.invokerFor(pack, model).invoke(request);
			if (answer == null || answer.isBlank()) {
				throw new IllegalStateException(named("the goal '" + goalName(pack) + "' of the pack '" + pack.name()
						+ "' produced no output," + " so there is no draft to check (the Java flow is not attempted)"));
			}
			drafted = parse(answer);
			findings = check(drafted);
			if (findings.isEmpty()) {
				return new CompiledDraft(drafted.name(), drafted, List.of());
			}
			request = withFindings(description, findings);
		}
		return new CompiledDraft(drafted.name(), drafted, findings);
	}

	/**
	 * The two deterministic checks the build would run anyway, in the order the build
	 * runs them: the structural validator, then the security scan. Both are pure
	 * functions of the spec, both are the production services, and neither is told
	 * anything about the model's intentions — which is why running them here rather than
	 * asking is the point.
	 * @param drafted the spec the pack just returned
	 * @return what the pack has to be told about, in its own words; empty when the draft
	 * is clean
	 */
	List<String> check(AgentSpec drafted) {
		List<String> findings = new ArrayList<>();
		AgentSpecValidationTool.ValidationReport validation = this.validator.validatePack(specText(drafted));
		validation.issues().forEach((issue) -> findings.add("validator " + issue.code() + ": " + issue.message()));
		ScanReport scan = this.scanner.scan(drafted);
		for (Finding finding : scan.findings()) {
			findings.add("security scan " + finding.severity() + " " + finding.category() + ": " + finding.message());
		}
		return List.copyOf(findings);
	}

	/**
	 * The pack's declared groups against the groups this platform can resolve. A name
	 * that resolves to nothing deploys today and reaches no model —
	 * {@code TrustTierPolicy} reads it as a declaration and moves the tier as though it
	 * meant something, which is the confusion this repository refuses to keep. So the
	 * deploy is refused before any model call rather than running a build whose tool
	 * calls would silently no-op.
	 * @param pack the pack about to be deployed
	 * @throws IllegalStateException when a step declares a group the platform cannot
	 * resolve
	 */
	void refuse(AgentSpec pack) {
		List<String> available = this.platform.getToolGroupResolver()
			.availableToolGroups()
			.stream()
			.map(ToolGroupDescription::getRole)
			.toList();
		for (ActionStepSpec step : pack.steps()) {
			List<String> unreachable = step.toolGroups()
				.stream()
				.filter((declared) -> !available.contains(declared))
				.toList();
			if (!unreachable.isEmpty()) {
				throw new IllegalStateException(named("step '" + step.name() + "' of the pack '" + pack.name()
						+ "' declares the tool groups " + unreachable + ", which this platform cannot resolve. The"
						+ " resolvable roles are " + available
						+ "; a step that declares a name resolving to nothing would run and silently reach nothing,"
						+ " so the build is refused before any model call."));
			}
		}
	}

	/**
	 * The request for a re-invocation: what the step was asked for, plus the findings
	 * against what it answered. Appended rather than replacing, so the second call is the
	 * same task and not a new one.
	 * @param description the original build description
	 * @param findings the checks' output
	 * @return the request to send again
	 */
	static String withFindings(String description, List<String> findings) {
		StringBuilder request = new StringBuilder(description);
		request.append(System.lineSeparator())
			.append(System.lineSeparator())
			.append("Your draft was checked by the build, in Java, and it reported the following findings. Fix each"
					+ " one and return the whole spec again — the same spec document, corrected, not a patch and not"
					+ " an explanation:")
			.append(System.lineSeparator());
		findings.forEach((finding) -> request.append("- ").append(finding).append(System.lineSeparator()));
		return request.toString();
	}

	private AgentSpec readPack() {
		try {
			return this.reader.read(this.packDir);
		}
		catch (IOException | RuntimeException ex) {
			throw new IllegalStateException(
					named("failed to load the builder pack at " + this.packDir + ": " + ex.getMessage()), ex);
		}
	}

	private AgentSpec parse(String answer) {
		AgentSpec drafted;
		try {
			drafted = this.json.readValue(AgentSpecNormalizer.extractJson(answer), AgentSpec.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException(named("the pack's goal did not return valid agent spec JSON: "
					+ ex.getMessage() + "; it returned " + abbreviate(answer)), ex);
		}
		return AgentSpecNormalizer.normalize(drafted);
	}

	private String specText(AgentSpec drafted) {
		try {
			return this.json.writeValueAsString(drafted);
		}
		catch (Exception ex) {
			throw new IllegalStateException(named("the draft could not be handed to the validator: " + ex.getMessage()),
					ex);
		}
	}

	private static String goalName(AgentSpec pack) {
		return (pack.goal() != null) ? pack.goal().name() : "(no goal)";
	}

	private static String abbreviate(String answer) {
		return (answer.length() <= 200) ? answer : answer.substring(0, 200) + "...";
	}

}
