/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.process;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentsPack;

/**
 * Hand-authored spec sets for the offline checks, so every expectation about the
 * product's validator and repair is pinned to a fixture this module can read rather than
 * to what a model happened to emit.
 *
 * <p>
 * The three unsound shapes are the ones {@code docs/08-builder.md} and the product's own
 * tests distinguish: an input no agent produces, a data flow no reorder can settle, and a
 * declared order that merely contradicts the data flow. The last is the control case — it
 * proves "not scored" is caused by unsoundness and not by this module refusing to score.
 *
 * <p>
 * Names carry the fixture's meaning and nothing else: descriptions are the literal
 * {@code "desc"} and prompts the literal {@code "prompt"}, because the stage-coverage
 * check searches declared text for stage words, and a chatty fixture would cover stages
 * by accident.
 *
 * @author Ivan Rodriguez
 */
final class ProcessFixtures {

	/**
	 * The builder page task 5.3 checks stage names against. Resolved from the module
	 * working directory the way POC 24 resolves its example blueprints, overridable for a
	 * run started elsewhere.
	 */
	static final Path BUILDER_DOCS = Path.of(System.getProperty("smith.docs.builder", "../../docs/08-builder.md"));

	/**
	 * A canned multi-agent answer in the shape {@code process_to_flow_multi} asks for:
	 * two agents that hand a compiled draft to a publisher, which is the pipeline being
	 * described rather than a generic trading example.
	 */
	static final String TWO_AGENT_ANSWER = """
			{
			  "process": "self-build",
			  "agents": [
			    {
			      "manifest": { "name": "compile-description", "version": "1.0.0" },
			      "goal": { "stepType": "goal", "name": "draft_ready", "description": "desc",
			                "outputTypeName": "CompiledDraft", "export": true },
			      "steps": [
			        { "stepType": "action", "name": "compile", "description": "desc",
			          "inputTypeNames": ["UserInput"], "outputTypeName": "CompiledDraft",
			          "prompt": "Draft from {{ userInput }}" }
			      ],
			      "types": []
			    },
			    {
			      "manifest": { "name": "publish-pack", "version": "1.0.0" },
			      "goal": { "stepType": "goal", "name": "pack_published", "description": "desc",
			                "outputTypeName": "PublishedPack", "export": true },
			      "steps": [
			        { "stepType": "action", "name": "publish", "description": "desc",
			          "inputTypeNames": ["CompiledDraft"], "outputTypeName": "PublishedPack",
			          "prompt": "Publish {{ compiledDraft }}" }
			      ],
			      "types": []
			    }
			  ]
			}
			""";

	private ProcessFixtures() {
	}

	/** A two-agent process whose hand-offs are sound. */
	static AgentsPack soundProcess() {
		return process("self-build",
				agent("compile-description", "CompiledDraft", step("compile", "UserInput", "CompiledDraft")),
				agent("publish-pack", "PublishedPack", step("publish", "CompiledDraft", "PublishedPack")));
	}

	/** A process whose second step needs a type nothing in the process produces. */
	static AgentsPack uncoveredInputProcess() {
		return process("self-build",
				agent("compile-description", "CompiledDraft", step("compile", "UserInput", "CompiledDraft")),
				agent("publish-pack", "PublishedPack", step("publish", "ReviewedDraft", "PublishedPack")));
	}

	/**
	 * A process whose two agents each consume the other's output — no reorder settles it.
	 */
	static AgentsPack mutualCycleProcess() {
		return process("self-build",
				agent("draft", "DraftedSpec", step("draft", "UserInput", "DraftedSpec"),
						step("mend", "ScoredSpec", "DraftedSpec")),
				agent("score", "ScoredSpec", step("score", "UserInput", "ScoredSpec"),
						step("rescore", "DraftedSpec", "ScoredSpec")));
	}

	/**
	 * A process with sound data flow that the model narrated in the wrong order: the
	 * publisher is listed before the compiler that feeds it.
	 */
	static AgentsPack wrongOrderProcess() {
		return process("self-build",
				agent("publish-pack", "PublishedPack", step("publish", "CompiledDraft", "PublishedPack")),
				agent("compile-description", "CompiledDraft", step("compile", "UserInput", "CompiledDraft")));
	}

	/**
	 * A process with one agent per name given, which is how the stage-coverage fixtures
	 * are authored: the names are the only stage words in the whole spec set.
	 * @param names the agent names, in run order
	 * @return a process declaring one agent per name
	 */
	static AgentsPack stagesProcess(String... names) {
		List<AgentSpec> agents = new ArrayList<>();
		for (int index = 0; index < names.length; index++) {
			String output = "StageOutput" + index;
			agents.add(agent(names[index], output, step("do" + index, "UserInput", output)));
		}
		return process("pipeline", agents.toArray(AgentSpec[]::new));
	}

	/**
	 * The builder documentation page, read from the repository.
	 * @return the text of {@code docs/08-builder.md}
	 * @throws IllegalStateException naming the path it looked for and the property that
	 * moves it
	 */
	static String builderDocs() {
		if (!Files.isRegularFile(BUILDER_DOCS)) {
			throw new IllegalStateException("No builder documentation at " + BUILDER_DOCS.toAbsolutePath()
					+ ". Task 5.3 checks stage names against that page, so run from the module directory or pass"
					+ " -Dsmith.docs.builder=<path to docs/08-builder.md>.");
		}
		try {
			return Files.readString(BUILDER_DOCS);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to read " + BUILDER_DOCS.toAbsolutePath(), ex);
		}
	}

	private static AgentsPack process(String name, AgentSpec... agents) {
		return new AgentsPack(name, List.of(agents));
	}

	private static AgentSpec agent(String name, String goalOutput, ActionStepSpec... steps) {
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name + "_goal", "desc", goalOutput), List.of(steps), List.of());
	}

	private static ActionStepSpec step(String name, String input, String output) {
		return ActionStepSpec.action(name, "desc", List.of(input), output, "prompt");
	}

}
