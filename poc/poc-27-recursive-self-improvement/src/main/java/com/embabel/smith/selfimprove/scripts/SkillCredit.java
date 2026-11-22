/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.scripts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

/**
 * Runs a generated skill's scripts through POC 26's engine and decides what each run is
 * worth to the skill: a script that completes credits the skill, and a script that ends
 * in an error withholds credit and records the failure against the skill that wrote it.
 *
 * <p>
 * This is the candidate's execution door and the only place in this package that opens a
 * script context — the same role {@code innerloop.PolicyRunner} plays for detection
 * policies, and it inherits the same rule from {@code evidence/MUTABILITY.md}: the engine
 * is POC 26's, with its hardening and its limits untouched, and a sandbox denial is an
 * outcome to record rather than a crash to survive. Design D9 keeps the referee on the
 * other side of a process boundary from this class.
 *
 * <p>
 * <b>An error is any non-successful end, of which there are three, not one.</b> POC 26's
 * engine reports a thrown script as a {@code Failure}, but a script that calls
 * {@code process.exit(2)} comes back as a {@code Success} carrying exit code 2, because
 * the engine treats the exit code as a CLI's would. A gate that only looked for
 * {@code Failure} would credit a skill whose script announced its own failure — so the
 * exit code is checked after the type, exactly as {@code PolicyRunner} checks it. A
 * {@code Denied} result is the engine refusing the script before it ran, which withholds
 * credit too: a script the interpreter would not load did not do the work either.
 *
 * @author Ivan Rodriguez
 */
public final class SkillCredit {

	private final SkillScriptExecutionEngine engine;

	/**
	 * Create a credit run over POC 26's engine.
	 * @param engine the script engine; in this POC
	 * {@code GraalJsSkillScriptExecutionEngine} with its default limits
	 */
	public SkillCredit(SkillScriptExecutionEngine engine) {
		this.engine = engine;
	}

	/**
	 * Run every JavaScript script the skill folder carries.
	 * @param skill the generated skill to run
	 * @param stdin the text handed to each script as its input
	 * @return the ledger: one outcome per script, and the skills that earned credit.
	 */
	public Ledger run(StepSkillGenerator.Generated skill, String stdin) {
		List<Outcome> outcomes = new ArrayList<>();
		for (String fileName : skill.scriptFiles()) {
			outcomes.add(runOne(skill, fileName, stdin));
		}
		return new Ledger(outcomes);
	}

	/**
	 * Run one script and decide what it earns.
	 * @param skill the generated skill
	 * @param fileName the script to run
	 * @param stdin the script's input
	 * @return the outcome, credited only when the script ran and ended at zero.
	 */
	private Outcome runOne(StepSkillGenerator.Generated skill, String fileName, String stdin) {
		ScriptExecutionResult result = this.engine.execute(skill.script(fileName), List.of(), stdin, List.of());
		if (result instanceof ScriptExecutionResult.Denied denied) {
			return withheld(skill, fileName, "engine refused the script: " + denied.getReason());
		}
		if (result instanceof ScriptExecutionResult.Failure failure) {
			return withheld(skill, fileName, "script failed: " + failure.getError());
		}
		ScriptExecutionResult.Success success = (ScriptExecutionResult.Success) result;
		if (success.getExitCode() != 0) {
			return withheld(skill, fileName,
					"script exited with code " + success.getExitCode() + ": " + success.getStderr());
		}
		return new Outcome(skill.skillName(), fileName, true, "");
	}

	private static Outcome withheld(StepSkillGenerator.Generated skill, String fileName, String diagnostic) {
		return new Outcome(skill.skillName(), fileName, false, diagnostic);
	}

	/**
	 * What one script's run was worth.
	 *
	 * @param skillName the skill the script belongs to, so a failure is attributed rather
	 * than merely recorded
	 * @param scriptFileName the script that ran
	 * @param credited whether the run earned the skill credit
	 * @param diagnostic why it did not, empty when it did
	 * @author Ivan Rodriguez
	 */
	public record Outcome(String skillName, String scriptFileName, boolean credited, String diagnostic) {

		/**
		 * The failure with its owner in front of it, which is the form a generation
		 * record carries: the skill that wrote the script is the thing that did not work.
		 * @return the skill name, the script, and why it did not earn credit.
		 */
		public String attribution() {
			return this.skillName + " scripts/" + this.scriptFileName + ": " + this.diagnostic;
		}
	}

	/**
	 * The result of running one candidate's scripts.
	 *
	 * @param outcomes one outcome per script that was run, in file-name order
	 * @author Ivan Rodriguez
	 */
	public record Ledger(List<Outcome> outcomes) {

		public Ledger {
			outcomes = List.copyOf(outcomes);
		}

		/**
		 * The skills that earned credit: those that ran at least one script and had every
		 * one of them succeed. A skill with no scripts runs nothing and earns nothing, so
		 * credit cannot be granted by the folder merely being present.
		 * @return the credited skill names, in the order their scripts ran.
		 */
		public List<String> creditedSkills() {
			Set<String> ran = new LinkedHashSet<>();
			Set<String> failed = new HashSet<>();
			for (Outcome outcome : this.outcomes) {
				ran.add(outcome.skillName());
				if (!outcome.credited()) {
					failed.add(outcome.skillName());
				}
			}
			ran.removeAll(failed);
			return List.copyOf(ran);
		}

		/**
		 * The runs that did not earn credit, each carrying the skill it is against.
		 * @return the withheld outcomes, in run order.
		 */
		public List<Outcome> withheld() {
			return this.outcomes.stream().filter((outcome) -> !outcome.credited()).toList();
		}

		/**
		 * Whether this ledger credits a given skill.
		 * @param skillName the skill to ask about
		 * @return whether this ledger credits that skill.
		 */
		public boolean credited(String skillName) {
			return creditedSkills().contains(skillName);
		}
	}

}
