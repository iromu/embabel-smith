/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.ToDoubleFunction;

import com.embabel.smith.builder.core.AgentsPackRepair;
import com.embabel.smith.builder.core.Candidate;
import com.embabel.smith.common.io.AgentsPackReader;
import com.embabel.smith.common.io.AgentsPackWriter;
import com.embabel.smith.common.model.AgentsPack;
import com.embabel.smith.common.validation.AgentsPackValidationResult;
import com.embabel.smith.common.validation.AgentsPackValidator;

/**
 * The hand-off gate for a generated process, run without a build: the process is written
 * to a pack folder with the shared writer, read back, and validated by the product's own
 * {@link AgentsPackValidator} — the same validator {@code BuilderService} runs on its
 * second candidate, with the same bounded reorder {@link AgentsPackRepair} performs.
 *
 * <p>
 * <b>The pack folder is written directly, because {@code process} is not a build
 * target.</b> {@code docs/08-builder.md} records that {@code --target} takes exactly
 * {@code agent-spec} and {@code skill} and that {@code --target process} is a usage
 * error, so the only way to put a generated process on disk from here is the shared
 * {@link AgentsPackWriter}. The round trip is not decoration: the hand-offs the validator
 * judges are the ones on disk, and a run order that survives only in memory is not
 * evidence about the artifact a build would carry.
 *
 * <p>
 * The verdict is the product's own {@link Candidate}, which is how "named and not scored"
 * is recorded rather than asserted: an unsound process comes back as the multi-agent
 * candidate with its name, {@code valid} false and no score, and the scorer is never
 * called — the rule {@code BuilderService.scoreAgentsPack} applies, where a process that
 * did not validate is not worth a model call. Nothing here scores anything itself; the
 * scorer is passed in, so an offline test can see the call not happen.
 *
 * <p>
 * {@code evidence/MUTABILITY.md} fixes what this may not touch: the validator and the
 * repair are product code, and the process shape they judge is mutable. This class
 * configures both and changes neither.
 *
 * @author Ivan Rodriguez
 */
public final class ProcessHandoffs {

	private ProcessHandoffs() {
	}

	/**
	 * Writes the draft as a pack folder, validates what reads back, reorders a cycle once
	 * the product's repair will, and scores only what survives.
	 * @param draft the generated process to check
	 * @param packDir the folder to write it to
	 * @param evalScore the stand-in for the eval the product would run; only called for a
	 * process that validated
	 * @return the verdict: the process as read back and reordered, its validation, the
	 * reorders performed, and the candidate the build would carry
	 */
	public static Verdict verify(AgentsPack draft, Path packDir, ToDoubleFunction<AgentsPack> evalScore) {
		AgentsPack process = roundTrip(draft, packDir);
		AgentsPackValidationResult validation = new AgentsPackValidator().validate(process);
		int reorders = 0;
		if (validation.hasCycle()) {
			AgentsPackRepair.Result repaired = AgentsPackRepair.repair(process, validation);
			process = repaired.agentsPack();
			validation = repaired.validation();
			reorders = repaired.attempts();
		}
		String name = process.process();
		if (!validation.isValid()) {
			return new Verdict(process, validation, reorders, Candidate.multiAgent(name, false, null), packDir);
		}
		return new Verdict(process, validation, reorders,
				Candidate.multiAgent(name, true, evalScore.applyAsDouble(process)), packDir);
	}

	/**
	 * Writes the process and reads the folder back, so the validation that follows judges
	 * the artifact rather than the in-memory draft.
	 * @param draft the process to write
	 * @param packDir the folder to write it to
	 * @return the process as the folder declares it, in the run order its manifest
	 * records
	 */
	private static AgentsPack roundTrip(AgentsPack draft, Path packDir) {
		try {
			new AgentsPackWriter().write(draft, packDir);
			return new AgentsPackReader().read(packDir);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write and re-read the process folder at " + packDir, ex);
		}
	}

	/**
	 * The outcome of checking one process's hand-offs.
	 *
	 * @param process the process as read back from disk, after any reorder repair
	 * @param validation the validation of that process, whose issues name what is unsound
	 * @param reorders the number of reorders {@link AgentsPackRepair} performed
	 * @param candidate the candidate a build would carry for this process, with no score
	 * when it did not validate
	 * @param packDir the folder the process was written to
	 */
	public record Verdict(AgentsPack process, AgentsPackValidationResult validation, int reorders, Candidate candidate,
			Path packDir) {

		/**
		 * Whether the process reached scoring at all.
		 * @return whether a score was recorded for this candidate
		 */
		public boolean scored() {
			return this.candidate.score() != null;
		}

		/**
		 * The hand-off findings, empty when the process is sound.
		 * @return the validation issue messages, in validation order
		 */
		public List<String> issues() {
			return this.validation.issues();
		}
	}

}
