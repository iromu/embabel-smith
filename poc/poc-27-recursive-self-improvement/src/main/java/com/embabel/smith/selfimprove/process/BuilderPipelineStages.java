/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.ProcessSpec;

/**
 * The pipeline a generated process is supposed to describe: the eight stages of
 * embabel-smith's own builder, in the order {@code core/BuilderService.build()} holds
 * them, each carrying the words {@code docs/08-builder.md} uses for it.
 *
 * <p>
 * The stage <em>names</em> are the self-description's ({@link SelfDescription}), the
 * stage <em>order</em> is the builder's, and every stage carries a
 * {@link Stage#docsAnchor() docs anchor} — a phrase quoted from the builder page. A test
 * asserts each anchor is present in that page, so this list cannot quietly disagree with
 * the documentation it claims to reflect; a stage invented here would have no anchor to
 * quote.
 *
 * <p>
 * {@link #coverage(ProcessSpec)} answers task 5.3's question — which stages does the
 * generated process actually name — by searching the process's own declared text for the
 * stage's terms. It is a coarse, deterministic instrument on purpose: a stage counts as
 * named when the word appears anywhere in an agent, step, prompt or type the model wrote,
 * so a paraphrase that avoids the word is recorded as a dropped stage. That is the honest
 * direction for this check to err, because the record it produces is "the generation
 * dropped a stage", and a dropped stage is a finding the POC reports rather than a
 * failure it hides.
 *
 * @author Ivan Rodriguez
 */
public final class BuilderPipelineStages {

	/**
	 * The stages, in build order. Each anchor was quoted from {@code docs/08-builder.md}
	 * and each term is the stem the builder page uses for that stage, so the coverage
	 * rule is the docs' vocabulary rather than a keyword list invented per stage.
	 */
	private static final List<Stage> STAGES = List.of(
			new Stage("generate", "generates a draft", List.of("generat", "compile")),
			new Stage("validate", "Validate before write is mandatory", List.of("validat")),
			new Stage("write", "validate → write → evaluate", List.of("write")),
			new Stage("evaluate", "The eval invoker runs the pack's own goal", List.of("eval")),
			new Stage("inspect", "gate runs the skill writing guidelines before any eval call is paid for",
					List.of("inspect")),
			new Stage("security scan", "is the install-time static scan proven in POC 21", List.of("security", "scan")),
			new Stage("lifecycle stamp", "content hash, baseline pass-rate, state", List.of("lifecycle")),
			new Stage("publish", "publishes only the recommended one", List.of("publish")));

	private BuilderPipelineStages() {
	}

	/**
	 * The documented stages, in build order.
	 * @return the eight stages
	 */
	public static List<Stage> stages() {
		return STAGES;
	}

	/**
	 * The stage names, in build order.
	 * @return the names of {@link #stages()}
	 */
	public static List<String> ids() {
		return STAGES.stream().map(Stage::id).toList();
	}

	/**
	 * Which stages a process names, and which it dropped.
	 * @param process the generated process to read
	 * @return the named and dropped stage names, in build order, together always the
	 * whole set
	 */
	public static Coverage coverage(ProcessSpec process) {
		String declared = declaredText(process);
		List<String> named = new ArrayList<>();
		List<String> dropped = new ArrayList<>();
		for (Stage stage : STAGES) {
			if (stage.terms().stream().anyMatch(declared::contains)) {
				named.add(stage.id());
			}
			else {
				dropped.add(stage.id());
			}
		}
		return new Coverage(named, dropped);
	}

	/**
	 * The stage anchors this list cannot find in the builder documentation.
	 * @param docsPage the text of {@code docs/08-builder.md}, whitespace-normalised here
	 * @return the anchors that page does not contain, empty when every stage is
	 * documented
	 */
	public static List<String> anchorsMissingFrom(String docsPage) {
		String page = docsPage.replaceAll("\\s+", " ");
		List<String> missing = new ArrayList<>();
		for (Stage stage : STAGES) {
			if (!page.contains(stage.docsAnchor())) {
				missing.add(stage.id() + " — \"" + stage.docsAnchor() + "\"");
			}
		}
		return missing;
	}

	/**
	 * Everything a process declares about itself, folded to lower case for the term
	 * search: its name, and each agent's name, goal, steps and types.
	 * @param process the process to read
	 * @return the process's own declared text
	 */
	private static String declaredText(ProcessSpec process) {
		StringBuilder text = new StringBuilder(String.valueOf(process.process())).append('\n');
		for (AgentSpec agent : process.agents()) {
			text.append(agent.name()).append('\n');
			if (agent.goal() != null) {
				text.append(agent.goal().name())
					.append(' ')
					.append(agent.goal().description())
					.append(' ')
					.append(agent.goal().outputTypeName())
					.append('\n');
			}
			for (ActionStepSpec step : agent.steps()) {
				text.append(step.name())
					.append(' ')
					.append(step.description())
					.append(' ')
					.append(step.outputTypeName())
					.append(' ')
					.append(step.inputTypeNames())
					.append(' ')
					.append(step.prompt())
					.append('\n');
			}
			agent.types()
				.forEach((type) -> text.append(type.name()).append(' ').append(type.description()).append('\n'));
		}
		return text.toString().toLowerCase(Locale.ROOT);
	}

	/**
	 * One pipeline stage of the builder's own process.
	 *
	 * @param id the stage as the self-description names it
	 * @param docsAnchor a phrase quoted from {@code docs/08-builder.md} that documents
	 * this stage
	 * @param terms the lower-case stems that count as the process naming this stage
	 */
	public record Stage(String id, String docsAnchor, List<String> terms) {

		public Stage {
			terms = List.copyOf(terms);
		}
	}

	/**
	 * What a generated process covers of the builder's pipeline.
	 *
	 * @param named the stages the process names, in build order
	 * @param dropped the stages it does not, in build order — the record task 5.3 asks
	 * for
	 */
	public record Coverage(List<String> named, List<String> dropped) {

		public Coverage {
			named = List.copyOf(named);
			dropped = List.copyOf(dropped);
		}
	}

}
