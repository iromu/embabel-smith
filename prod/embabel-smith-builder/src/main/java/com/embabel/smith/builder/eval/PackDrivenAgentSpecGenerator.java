/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.embabel.smith.builder.core.AgentSpecGenerator;
import com.embabel.smith.builder.core.AgentSpecNormalizer;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The pack flow's generator (design D1): the builder's own
 * {@code builder-agent-specs/builder-spec-one-shot} pack deployed through
 * {@link AgentSpecInvokerFactory#invokerFor(AgentSpec, String)}, its exported goal run
 * over the description, and the JSON that comes back parsed into the draft the Java flow
 * would have produced. One model call either way — what changes is that the call now
 * belongs to a pack, and that the step behind it can call the validator.
 *
 * <p>
 * <b>The description goes in as it stands.</b> The pack's step prompt does the framing
 * (its last line is the labelled description, which is what the transcription recorded in
 * task 2.1 kept), so the generator passes the build description straight through as
 * {@code UserInput} rather than wrapping it in the single-shot generator's own framing
 * sentence. Wrapping it would put two frames on one input and make the parity numbers
 * compare two different prompts.
 *
 * <p>
 * <b>Failure is a failure.</b> Every exit from this class that is not a draft is an
 * {@link IllegalStateException} naming the pack flow and the reason — the pack that would
 * not load, the goal that produced nothing, the answer that was not spec JSON. There is
 * no path back to the Java generator (design D4): the flow that was asked for is the flow
 * that runs, so a build cannot report success while a pack flow failed.
 *
 * <p>
 * The parse is {@link AgentSpecNormalizer}'s, shared with the single-shot generator on
 * purpose: two flows that normalised differently would differ in their drafts before any
 * gate saw them, and task 3.3's comparison would be comparing the wrong thing.
 *
 * @author Ivan Rodriguez
 */
public class PackDrivenAgentSpecGenerator implements AgentSpecGenerator {

	/**
	 * The shipped pack, addressed from the module directory — the convention
	 * {@code core/BuilderAgentSpecPackTest} and
	 * {@code eval/CollaborationStructureIsFixedByConstructionTest} use for the same
	 * directory. Nothing resolves it against the classpath: the packs are the mutable
	 * surface, so they live outside {@code src/main/resources}.
	 */
	public static final Path BUILDER_PACK = Path.of("builder-agent-specs", "builder-spec-one-shot");

	private final InvokerFactory invokers;

	private final Path packDir;

	private final AgentSpecReader reader = new AgentSpecReader();

	private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
			false);

	public PackDrivenAgentSpecGenerator(InvokerFactory invokers, Path packDir) {
		this.invokers = invokers;
		this.packDir = packDir;
	}

	@Override
	public CompiledDraft generate(String description) {
		return generate(description, null);
	}

	@Override
	public CompiledDraft generate(String description, String model) {
		AgentSpec pack = readPack();
		String answer = this.invokers.invokerFor(pack, model).invoke(description);
		if (answer == null || answer.isBlank()) {
			throw new IllegalStateException("pack flow: the goal '" + goalName(pack) + "' of the pack '" + pack.name()
					+ "' produced no output, so there is no draft to parse (the Java flow is not attempted)");
		}
		AgentSpec drafted = parse(answer);
		return new CompiledDraft(drafted.name(), drafted, List.of());
	}

	/**
	 * The pack directory as disk. A directory that is missing or unreadable is named here
	 * rather than surfacing as a bare IO error, because the stage that failed is the
	 * answer the operator needs.
	 * @return the pack to deploy
	 * @throws IllegalStateException when the pack cannot be read
	 */
	private AgentSpec readPack() {
		try {
			return this.reader.read(this.packDir);
		}
		catch (IOException | RuntimeException ex) {
			throw new IllegalStateException(
					"pack flow: failed to load the builder pack at " + this.packDir + ": " + ex.getMessage(), ex);
		}
	}

	private AgentSpec parse(String answer) {
		AgentSpec drafted;
		try {
			drafted = this.json.readValue(AgentSpecNormalizer.extractJson(answer), AgentSpec.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException("pack flow: the pack's goal did not return valid agent spec JSON: "
					+ ex.getMessage() + "; it returned " + abbreviate(answer), ex);
		}
		return AgentSpecNormalizer.normalize(drafted);
	}

	private static String goalName(AgentSpec pack) {
		return (pack.goal() != null) ? pack.goal().name() : "(no goal)";
	}

	private static String abbreviate(String answer) {
		return (answer.length() <= 200) ? answer : answer.substring(0, 200) + "...";
	}

}
