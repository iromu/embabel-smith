/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.scripts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.skills.script.ScriptExecutionResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 6.3, offline: the context a candidate's script runs in is still POC 26's hardened
 * one. A script that probes for a host class, a file, a process or a thread is refused,
 * the refusal withholds its credit, and the words of the refusal are the engine's.
 *
 * <p>
 * The probes run through {@link SkillCredit} — the same door a generated skill script
 * uses — rather than straight at the engine, because the claim is about the candidate's
 * context and not about the engine in the abstract. One test then compares the two so the
 * ledger cannot be read as a place a denial gets softened on the way past.
 *
 * <p>
 * <b>The wordings are POC 26's, quoted from its own tests</b> ({@code
 * GraalJsSkillScriptExecutionEngineTest}: {@code "not allowed"} for the host class
 * lookup, {@code "was not staged"} for a file that was not handed to the run). They are
 * asserted here rather than inferred: {@code evidence/MUTABILITY.md} fixes the engine's
 * hardening, and if that hardening were widened, narrowed or removed, a test keyed on
 * this POC's own label for the event would keep passing while the sandbox was gone.
 *
 * <p>
 * <b>What this does not claim.</b> These are probes of the four surfaces the task names,
 * run from JavaScript. They cannot show that no other route exists — the engine's own
 * test suite is where the whole hardening is pinned, and this one checks that the
 * hardening is still in force on the path a candidate's script actually travels.
 *
 * @author Ivan Rodriguez
 */
class CandidateSandboxHardeningTest {

	private static final String STDIN = "draft: a compiled spec that names every stage";

	/**
	 * How POC 26's engine refuses a host class lookup, worded as its own test words it.
	 */
	private static final String HOST_CLASS_DENIED = "not allowed";

	/** How POC 26's bridge refuses a read of a file this run was not given. */
	private static final String UNSTAGED_READ_DENIED = "was not staged";

	@TempDir
	Path workDir;

	private final SkillCredit credit = new SkillCredit(ScriptFixtures.ENGINE);

	private SkillCredit.Ledger runProbing(String probeSource) {
		return this.credit.run(ScriptFixtures.generated(this.workDir, probeSource), STDIN);
	}

	@Test
	void aScriptThatLooksUpAHostClassIsRefusedAndLosesCredit() {
		Path marker = this.workDir.resolve("pwned");
		SkillCredit.Ledger ledger = runProbing("""
				const rt = Java.type('java.lang.Runtime');
				rt.getRuntime().exec('touch %s');
				console.log('should never print');
				""".formatted(marker));

		assertThat(ledger.withheld()).hasSize(1);
		assertThat(ledger.withheld().get(0).diagnostic()).contains(HOST_CLASS_DENIED);
		assertThat(ledger.credited(ledger.withheld().get(0).skillName())).isFalse();
		// The refusal stopped the attempt, it did not merely describe it.
		assertThat(marker).doesNotExist();
	}

	@Test
	void aScriptCannotSpawnAProcessOrAThread() {
		Path marker = this.workDir.resolve("spawned");
		SkillCredit.Ledger process = runProbing("""
				const builder = Java.type('java.lang.ProcessBuilder');
				new builder(['touch', '%s']).start();
				""".formatted(marker));
		SkillCredit.Ledger thread = runProbing("""
				const t = Java.type('java.lang.Thread');
				t.startVirtualThread(() => console.log('spawned'));
				""");

		assertThat(process.withheld().get(0).diagnostic()).contains(HOST_CLASS_DENIED);
		assertThat(thread.withheld().get(0).diagnostic()).contains(HOST_CLASS_DENIED);
		assertThat(thread.creditedSkills()).isEmpty();
		assertThat(marker).doesNotExist();
	}

	@Test
	void aScriptCannotReachTheFilesystemOrTheEnvironment() {
		Path outside = this.workDir.resolve("outside.txt");
		SkillCredit.Ledger hostFile = runProbing("""
				const file = Java.type('java.io.File');
				new file('%s').text = 'written';
				""".formatted(outside));
		SkillCredit.Ledger stagedRead = runProbing("console.log(readFile('outside.txt'))");
		SkillCredit.Ledger environment = runProbing("console.log(JSON.stringify(process.env))");

		assertThat(hostFile.withheld().get(0).diagnostic()).contains(HOST_CLASS_DENIED);
		assertThat(stagedRead.withheld().get(0).diagnostic()).contains(UNSTAGED_READ_DENIED);
		assertThat(outside).doesNotExist();
		// The environment is not refused, it is simply empty, so a script cannot read a
		// token out of the process that ran it. This is the one probe that succeeds,
		// which
		// is also why the control below is needed: a ledger that withheld everything
		// would
		// look identical to a working sandbox.
		assertThat(environment.creditedSkills()).hasSize(1);
	}

	@Test
	void theLedgerCarriesTheEnginesOwnRefusalUnchanged() {
		StepSkillGenerator.Generated skill = ScriptFixtures.generated(this.workDir,
				"console.log(Java.type('java.lang.System').getenv('HOME'))");

		ScriptExecutionResult direct = ScriptFixtures.ENGINE.execute(skill.script(skill.scriptFileName()), List.of(),
				STDIN, List.of());
		SkillCredit.Ledger ledger = this.credit.run(skill, STDIN);

		assertThat(direct).isInstanceOf(ScriptExecutionResult.Failure.class);
		String engineWords = ((ScriptExecutionResult.Failure) direct).getError();
		assertThat(engineWords).contains(HOST_CLASS_DENIED);
		assertThat(ledger.withheld().get(0).diagnostic()).contains(engineWords);
	}

	@Test
	void aScriptThatStaysInsideTheSandboxStillGetsCredit() throws IOException {
		// Control for every denial above: the same door, the same engine, a script that
		// asks for nothing. Without it the four tests would be satisfied by a context
		// that
		// refuses everything, including the work it was generated to do.
		Path artifactDir = this.workDir.resolve("artifacts");
		Files.createDirectories(artifactDir);
		StepSkillGenerator.Generated skill = ScriptFixtures.generated(this.workDir, """
				const draft = stdin.trim();
				writeFile('score.txt', 'scored ' + draft.length);
				console.log('scored ' + draft.length);
				""");

		SkillCredit.Ledger ledger = this.credit.run(skill, STDIN);

		assertThat(ledger.creditedSkills()).containsExactly(skill.skillName());
		assertThat(ledger.withheld()).isEmpty();
		assertThat(readScript(skill)).contains("writeFile");
	}

	private String readScript(StepSkillGenerator.Generated skill) {
		try {
			return Files.readString(skill.skillDir().resolve("scripts").resolve(skill.scriptFileName()),
					StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("could not read the generated script", ex);
		}
	}

}
