/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.nio.file.Path;

import com.embabel.smith.builder.eval.InvokerFactory;

/**
 * The referee as a process of its own. It is started by hand, and nothing in this module
 * starts it: the actor side of the POC must not hold the scorer, which is why the
 * studio's rule that remote bridges are <em>reported and never started</em>
 * ({@code docs/09-studio.md}) is the precedent this follows rather than an in-process
 * second gate.
 *
 * <p>
 * Started by hand from this module's classpath, as
 * {@code com.embabel.smith.selfimprove.referee.RefereeProcess}. It is not wired into a
 * launcher: {@code SelfImproveApp} is the actor side and does not start this class.
 *
 * <p>
 * It reports what it would score and whether the outer loop may start, and exits non-zero
 * when it refuses. It never constructs a script engine and never loads a skill folder —
 * see the citation of {@code evidence/MUTABILITY.md} in {@link Referee} for the fixed
 * boundary that says why.
 *
 * @author Ivan Rodriguez
 */
public final class RefereeProcess {

	private RefereeProcess() {
	}

	/**
	 * Reports the referee's state, or refuses.
	 * @param args {@code --skill-folder <path>} is accepted only so that supplying it can
	 * be refused; the resources directory is taken from {@code -Dsmith.self-improve.dir},
	 * defaulting to {@code src/main/resources}
	 */
	public static void main(String[] args) {
		Path resources = Path.of(System.getProperty("smith.self-improve.dir", "src/main/resources"));
		System.out.println("referee process — scoring against " + resources);
		System.out.println("This process is started by hand. No actor code starts it, and it holds no script engine.");
		try {
			Referee referee = Referee.start(resources, skillFolder(args), notYetDeployable());
			FrozenTaskSet frozen = referee.frozen();
			System.out.println("frozen tasks   : " + frozen.ids());
			System.out.println("gate sees      : " + frozen.toEvalTasks().size() + " task(s), first required phrase"
					+ " of each as the expected substring");
			System.out.println("narrowed       : " + frozen.narrowedTaskIds() + " — the gate's single-substring scorer"
					+ " cannot carry their anyOf/forbid conditions, so the report keeps both scores");
			referee.assertOuterLoopMayStart();
			System.out.println("evidence       : baseline and noise floor recorded; the outer loop may start");
		}
		catch (IllegalStateException ex) {
			System.out.println("REFUSED — " + ex.getMessage());
			System.exit(1);
		}
	}

	/**
	 * The skill folder the process was asked to load, if any.
	 * @param args the command-line arguments
	 * @return the path after {@code --skill-folder}, or null when the flag was not given
	 */
	private static Path skillFolder(String[] args) {
		for (int i = 0; i < args.length - 1; i++) {
			if ("--skill-folder".equals(args[i])) {
				return Path.of(args[i + 1]);
			}
		}
		return null;
	}

	/**
	 * The invoker factory the referee is constructed with before section 7 exists. It
	 * fails when used rather than answering for a pack it cannot run: the gate's re-eval
	 * is the only thing that needs one, and a fake answer here would look like a score.
	 * @return a factory whose invoker refuses to answer
	 */
	private static InvokerFactory notYetDeployable() {
		return (pack) -> (input) -> {
			throw new IllegalStateException("the referee cannot deploy a candidate yet: running a pack is section 7 of"
					+ " this change, and nothing before it should need an invoker");
		};
	}

}
