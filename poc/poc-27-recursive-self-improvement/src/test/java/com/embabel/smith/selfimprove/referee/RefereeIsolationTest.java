/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.improve.GateChain;
import com.embabel.smith.skillscripts.GraalJsSkillScriptExecutionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 3.2: the referee carries no script engine and no script context, and refuses a
 * skill folder.
 *
 * <p>
 * <b>What this does not claim.</b> The actor loop and the referee live in this one Maven
 * module, so POC 26's engine classes are on the shared classpath and no test here can
 * assert that they are absent — jar-level isolation would need a second module, which the
 * plan did not ask for. Isolation is by process boundary ({@link RefereeProcess} is a
 * separate {@code main}, started by hand and never by actor code) and by construction
 * (nothing reachable from the object {@code Referee.start} returns is a script engine or
 * a script context). The first test proves the walk can see such an engine when one
 * <em>is</em> in the graph, so the clean result of the second is a fact about the object
 * graph and not a walk that was looking at nothing.
 *
 * @author Ivan Rodriguez
 */
class RefereeIsolationTest {

	@Test
	void theWalkSeesAScriptEngineThroughAField() {
		// Control case, so the clean result below is a fact about the referee's graph and
		// not a walk
		// looking at nothing. Both detection paths are proven separately, because the
		// second is the
		// one that survives a refactor: an engine reached through the SPI interface, from
		// a class
		// whose own name carries no script marker, is caught by what it is rather than by
		// what it
		// is called.
		assertThat(ObjectGraphWalk.from(new EngineHolder(new GraalJsSkillScriptExecutionEngine())).scriptClasses())
			.anyMatch((name) -> name.contains("skillscripts"));
		assertThat(ObjectGraphWalk.from(new EngineHolder(new Watchmaker())).scriptClasses())
			.containsExactly(Watchmaker.class.getName());
	}

	@Test
	void nothingReachableFromTheRefereeIsAScriptEngineOrAScriptContext() {
		Referee referee = Referee.start(RefereeFixtures.resources(), null,
				new RecordingInvokerFactory(RefereeFixtures.frozenTasks(), RefereeFixtures.CORRECT));

		ObjectGraphWalk walk = ObjectGraphWalk.from(referee);

		assertThat(walk.scriptClasses()).as("script engines or contexts reachable from the referee").isEmpty();
		// The walk covered what it claims to have covered: no field was skipped because
		// reflection
		// could not read it, and it did reach the product gate the referee holds.
		assertThat(walk.blockedByReflection()).isEmpty();
		assertThat(walk.classes()).contains(GateChain.class.getName());
	}

	@Test
	void theRefereeRefusesToStartWithASkillFolder() {
		assertThatThrownBy(() -> Referee.start(RefereeFixtures.resources(), Path.of("/tmp/some-skill-folder"),
				new RecordingInvokerFactory(RefereeFixtures.frozenTasks(), RefereeFixtures.CORRECT)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("refuses to start with a skill folder")
			.hasMessageContaining("/tmp/some-skill-folder")
			.hasMessageContaining("MUTABILITY.md");
	}

	@Test
	void theRefereeNeverNamesTheScorerItIsNotAllowedToReach() {
		// EvalScorer is fixed by evidence/MUTABILITY.md and is reached only from inside
		// GateChain. A
		// source check is the only way to see a static call, so this is what keeps the
		// referee from
		// scoring candidates with the scorer the improver must not be able to reach.
		List<Path> sources = refereeSources();

		assertThat(sources).as("referee sources found from " + Path.of("src/main/java").toAbsolutePath()).isNotEmpty();
		sources.forEach((source) -> {
			try {
				assertThat(Files.readString(source)).as(source.toString()).doesNotContain("EvalScorer");
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		});
	}

	private static List<Path> refereeSources() {
		Path dir = Path.of("src/main/java/com/embabel/smith/selfimprove/referee");
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (var paths = Files.list(dir)) {
			return paths.filter((path) -> path.getFileName().toString().endsWith(".java")).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private record EngineHolder(SkillScriptExecutionEngine engine) {
	}

	/**
	 * A script engine that a name-matching walk would walk straight past: it implements
	 * Embabel's script SPI, and nothing in its own name mentions scripts. It is never
	 * executed, which is why its one meaningful method throws rather than returns
	 * something fake.
	 */
	private static final class Watchmaker implements SkillScriptExecutionEngine {

		@Override
		public Set<ScriptLanguage> supportedLanguages() {
			return Set.of();
		}

		@Override
		public ScriptExecutionResult.Denied validate(SkillScript script) {
			return null;
		}

		@Override
		public ScriptExecutionResult execute(SkillScript script, List<String> args, String stdin,
				List<? extends Path> inputFiles) {
			throw new UnsupportedOperationException("a control fixture is never executed");
		}

	}

}
