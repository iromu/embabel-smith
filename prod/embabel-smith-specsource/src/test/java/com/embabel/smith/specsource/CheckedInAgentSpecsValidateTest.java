/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.specsource;

import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationIssue;
import com.embabel.smith.common.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the checked-in example packs under {@code agent-specs/} from rotting: every
 * definition there is validated offline with {@link AgentSpecValidator#validate(Path)},
 * so a hand-edited example fails a build rather than a future run.
 * <p>
 * This module is where the scan lives because it already depends on {@code smith-common}
 * and on nothing else, is offline by design (no Embabel agent code), and already owns
 * pack discovery — {@link FolderSpecSource} is the same "a directory whose children are
 * packs" reading this test applies to the repository's own examples.
 * <p>
 * Nothing else in the repository did this before: the validator's own tests run over
 * fixtures it writes itself, and the only repository path anywhere was a
 * {@code SpecSource.parse} string. That is how a shipped example came to promise a file
 * its own deploy never stages.
 * <p>
 * A finding arrives in two severities and this scan treats them apart. An error means the
 * pack is not deployable. An advisory means something the pack declares will be ignored,
 * or lost at the next rewrite — which is precisely the statement worth failing a build
 * over, so the scan does not accept advisories as a class; {@code isValid()} alone would.
 * One code is accepted, named below, and even that one is printed rather than passed over
 * in silence.
 * <p>
 * Runs with no model and no endpoint: the validator reads files.
 *
 * @author Ivan Rodriguez
 */
class CheckedInAgentSpecsValidateTest {

	/**
	 * The advisory codes a checked-in pack is allowed to carry. It is a list of one
	 * because that one is a property of the format rather than of the pack: the writer
	 * emits {@code manifest.yml}, {@code goal.yml}, {@code steps/} and {@code types/} and
	 * carries no representation of a references file, so every pack with one is noted for
	 * it whatever else it gets right. The format's ruling was to report that loss rather
	 * than give the writer a round-trip; this is where the report stops being a build
	 * failure. Every other advisory still fails here, which
	 * {@link #anAdvisoryTheScanHasNotAcceptedStillFails} is the guard for.
	 */
	private static final Set<String> ACCEPTED_ADVISORIES = Set.of("REFERENCES_FILE_NOT_PRESERVED_ON_REWRITE");

	private final AgentSpecValidator validator = new AgentSpecValidator();

	@Test
	void everyCheckedInDefinitionValidates() {
		List<String> complaints = new ArrayList<>();
		List<String> accepted = new ArrayList<>();
		for (Path pack : checkedInDefinitions()) {
			String name = specName(pack);
			ValidationResult result = this.validator.validate(pack);
			accepted.addAll(acceptedNotes(name, result));
			String complaint = complaintFor(name, result);
			if (complaint != null) {
				complaints.add(complaint);
			}
		}
		// The accepted notes are written out rather than dropped, so a reader of the run
		// log
		// sees that reference-heavy carries a references file the writer would lose, and
		// why,
		// whether or not anything else in the run happens to fail.
		accepted.forEach((note) -> System.out.println("checked-in agent spec note: " + note));

		assertThat(complaints)
			.as(() -> "checked-in agent specs that do not validate; advisories this scan accepts:" + " " + accepted)
			.isEmpty();
	}

	@Test
	void theScanActuallyFindsTheKnownExamples() {
		// The guard against a vacuous pass above: if the locator found nothing, or found
		// only
		// one directory, the loop would report success having checked nothing.
		List<String> names = checkedInDefinitions().stream().map(CheckedInAgentSpecsValidateTest::specName).toList();

		assertThat(names).contains("explainer", "reference-heavy", "arithmetic");
		assertThat(names).anyMatch((n) -> n.startsWith("coding-agent/"));
		assertThat(names).anyMatch((n) -> n.startsWith("skill-interpreter/"));
	}

	@Test
	void anAcceptedAdvisoryDoesNotFailTheScan(@TempDir Path dir) throws IOException {
		// The accepted set is a list of one, so which side of it a pack falls on has to
		// be
		// said rather than assumed. This is the fixture form of the green the scan
		// reports
		// for reference-heavy, which keeps the constant watched instead of trusted.
		Path pack = minimalPack(dir);
		Files.writeString(pack.resolve("references.yml"), "references:\n  - name: notes\n    kind: brief\n");

		ValidationResult result = this.validator.validate(pack);

		assertThat(result.advisories()).as(() -> result.issues().toString())
			.anyMatch((note) -> "REFERENCES_FILE_NOT_PRESERVED_ON_REWRITE".equals(note.code()));
		assertThat(complaintFor("fixture", result)).as(() -> "an accepted advisory must not fail the scan").isNull();
	}

	@Test
	void anAdvisoryTheScanHasNotAcceptedStillFails(@TempDir Path dir) throws IOException {
		// The other side, and the failure this scan would otherwise quietly swallow:
		// widen
		// the accepted set by mistake and every advisory in the format waves through
		// while
		// the scan still prints green. A key the upstream reader honors and the writer
		// never
		// emits is the cheapest such advisory to arrange, and the pack still deploys — so
		// what
		// fails here is the advisory, not an error dressed up as one.
		Path pack = minimalPack(dir);
		Path step = pack.resolve("steps/actOnIt.yml");
		Files.writeString(step, Files.readString(step) + "llm: cheap-model\n");

		ValidationResult result = this.validator.validate(pack);

		assertThat(result.isValid()).as(() -> result.issues().toString()).isTrue();
		assertThat(result.advisories()).anyMatch((note) -> "KEY_NOT_PRESERVED_ON_REWRITE".equals(note.code()));
		assertThat(complaintFor("fixture", result)).as(() -> "an unaccepted advisory must still fail the scan")
			.isNotNull();
	}

	/**
	 * Why this pack is a problem, or null when the scan accepts it — deployable, and
	 * carrying nothing beyond the advisory {@link #ACCEPTED_ADVISORIES} allows.
	 * @param name the pack's name as the report should read it
	 * @param result what the validator said
	 * @return the complaint text, or null when there is nothing to complain about
	 */
	private static String complaintFor(String name, ValidationResult result) {
		List<ValidationIssue> blocking = new ArrayList<>(result.errors());
		blocking
			.addAll(result.advisories().stream().filter((note) -> !ACCEPTED_ADVISORIES.contains(note.code())).toList());
		if (blocking.isEmpty()) {
			return null;
		}
		StringBuilder report = new StringBuilder(name)
			.append(result.isValid() ? " (valid, with notes)" : " (NOT DEPLOYABLE)")
			.append('\n');
		for (ValidationIssue issue : result.issues()) {
			report.append("    ")
				.append(issue.severity())
				.append(' ')
				.append(issue.code())
				.append(" @ ")
				.append(issue.location())
				.append(" :: ")
				.append(issue.message())
				.append('\n');
		}
		return report.toString();
	}

	/**
	 * The notes this pack carries that the scan has agreed to live with, phrased the same
	 * way in the log as in a failure message.
	 * @param name the pack's name
	 * @param result what the validator said
	 * @return one line per accepted advisory
	 */
	private static List<String> acceptedNotes(String name, ValidationResult result) {
		return result.advisories()
			.stream()
			.filter((note) -> ACCEPTED_ADVISORIES.contains(note.code()))
			.map((note) -> name + " :: " + note.code() + " @ " + note.location())
			.toList();
	}

	/**
	 * A deployable pack written out by hand, which is the shape this scan exists to
	 * check: four files and a types directory, no model classes and no writer involved.
	 * @param dir the directory to write it into
	 * @return the pack's directory
	 */
	private static Path minimalPack(Path dir) throws IOException {
		Files.createDirectories(dir.resolve("steps"));
		Files.createDirectories(dir.resolve("types"));
		Files.writeString(dir.resolve("manifest.yml"), "name: fixture\nversion: 1.0.0\n");
		Files.writeString(dir.resolve("goal.yml"), "stepType: goal\nname: workDone\ndescription: \"The work is done\"\n"
				+ "outputTypeName: com.embabel.agent.spec.model.BackgroundMessage\nexport: true\n");
		Files.writeString(dir.resolve("steps/actOnIt.yml"), "stepType: action\nname: actOnIt\ndescription: \"Do the"
				+ " thing\"\ninputTypeNames:\n  - com.embabel.agent.domain.io.UserInput\noutputTypeName:"
				+ " com.embabel.agent.spec.model.BackgroundMessage\nprompt: |\n  Work on: {{userInput.content}}\n");
		return dir;
	}

	/**
	 * Every directory under the repository's {@code agent-specs/} that is an agent
	 * definition — one carrying a {@code manifest.yml}. Both shapes are covered: a pack
	 * sitting directly in {@code agent-specs/}, and a definition nested one level deeper
	 * inside a folder of definitions, which is the shape the examples are authored in.
	 * @return the definitions found, in path order
	 */
	private static List<Path> checkedInDefinitions() {
		Path root = agentSpecsRoot();
		try (Stream<Path> walk = Files.walk(root, 3)) {
			return walk.filter(Files::isDirectory)
				.filter((d) -> Files.isRegularFile(d.resolve("manifest.yml")))
				.sorted(Comparator.comparing(Path::toString))
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not walk " + root, ex);
		}
	}

	/**
	 * Locates {@code agent-specs/} by walking up from the working directory, which is the
	 * module directory under Maven, so a fixed relative path would break as soon as the
	 * build is run from elsewhere.
	 * @return the directory holding the repository's example packs
	 */
	private static Path agentSpecsRoot() {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path candidate = dir.resolve("agent-specs");
			if (Files.isDirectory(candidate.resolve("explainer"))) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not locate the repository's agent-specs directory");
	}

	private static String specName(Path pack) {
		return agentSpecsRoot().relativize(pack).toString().replace('\\', '/');
	}

}
