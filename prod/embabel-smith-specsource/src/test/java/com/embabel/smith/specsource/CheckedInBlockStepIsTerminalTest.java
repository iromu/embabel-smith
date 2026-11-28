/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.specsource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.embabel.smith.common.io.Yaml;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The interpreter pack's escalate step ends a run rather than pausing it, and the pack
 * itself is what makes that true.
 *
 * <p>
 * <b>Why a pack claim is checked by reading files.</b> "Nothing beyond the block was
 * executed" is not something a run can be asked after the fact — a finished process
 * reports what it did, and if the planner had scheduled a step past the block it would
 * have run it and reported that too. What makes the outcome a block is structural: the
 * decision the escalate step names comes out as {@code OpenQuestion}, and if no step in
 * the pack declares that type as an input then no step can be planned after it, in any
 * strategy, on any input. That is a property of the shipped definitions and it is
 * checkable offline, which is the only kind of guarantee the format can offer in the
 * first place — there is no loop construct to test and no ordering to assert.
 *
 * <p>
 * <b>What the type has to carry.</b> A blocked-for-decision outcome is worth the name
 * only if it says what is being asked, what is already known, and how to run it again.
 * Those three are the fields below, and the third is the reason the runtime needs no
 * resume: the continuation is a new run with the answer as input, which the docs state as
 * a boundary rather than an omission.
 *
 * <p>
 * <b>The control that makes the absence mean something.</b> A reader that found no inputs
 * anywhere would pass the second case for the wrong reason, so the third case asks the
 * same reader to find an input that does exist in the same pack. Absence of a consumer is
 * then a fact about the type, not about the reading.
 *
 * <p>
 * Runs with no model and no endpoint: every case reads files under {@code agent-specs/}.
 *
 * @author Ivan Rodriguez
 */
class CheckedInBlockStepIsTerminalTest {

	/**
	 * The pack whose block is the subject: the generic interpreter pack, whose steps name
	 * no subject matter.
	 */
	private static final String INTERPRETER_PACK = "skill-interpreter";

	/**
	 * The type the block produces. Its spelling is the claim, so it is a literal here
	 * rather than a constant read from the definition it is asserted about.
	 */
	private static final String BLOCK_OUTPUT = "OpenQuestion";

	/**
	 * What a decision a run cannot make has to travel with: the question, what is already
	 * known about it, and how to run again once it is answered.
	 */
	private static final Set<String> THE_TRIPLE = Set.of("question", "evidence", "continuation");

	@Test
	void theBlockNamesItsQuestionItsEvidenceAndItsContinuation() {
		var fields = ownPropertiesOf("escalate", BLOCK_OUTPUT);

		assertThat(fields).as("the outcome the escalate step produces carries all three, so a reader can act on it")
			.containsExactlyInAnyOrderElementsOf(THE_TRIPLE);
	}

	@Test
	void noStepInThatPackConsumesWhatTheBlockProduces() {
		List<String> consumers = stepsConsuming(BLOCK_OUTPUT);

		assertThat(consumers)
			.as("nothing takes the decision as an input, so no planner — GOAP or otherwise — can schedule a"
					+ " step past the block")
			.isEmpty();
		assertThat(goalOutputOf("escalate"))
			.as("and the block is a finished run's output rather than a half-run: it is what the definition"
					+ " exports")
			.isEqualTo(BLOCK_OUTPUT);
	}

	@Test
	void theSameReaderFindsAnInputWhereOneIsDeclared() {
		List<String> consumers = stepsConsuming("WorkLog");

		assertThat(consumers)
			.as("the case above denies a consumer by name, so the reading has to be shown able to find one"
					+ " where the pack declares it")
			.isNotEmpty();
	}

	/**
	 * The declared own properties of one of a definition's types.
	 * @param definition the definition holding the type
	 * @param typeName the type's name, which is also its file name
	 * @return the property names, sorted, or empty when the type declares none
	 */
	private static Set<String> ownPropertiesOf(String definition, String typeName) {
		var node = read(interpreterPack().resolve(definition).resolve("types").resolve(typeName + ".yml"));
		Set<String> names = new TreeSet<>();
		var ownProperties = node.path("ownProperties");
		for (var property : ownProperties) {
			names.add(property.path("name").asText());
		}
		return names;
	}

	/**
	 * The output type the definition's exported goal asks for.
	 * @param definition the definition to read the goal of
	 * @return the declared output type name
	 */
	private static String goalOutputOf(String definition) {
		return read(interpreterPack().resolve(definition).resolve("goal.yml")).path("outputTypeName").asText();
	}

	/**
	 * The steps of the interpreter pack that declare a given type as an input.
	 * @param typeName the type to look for
	 * @return the step file names that declare it, in no particular order
	 */
	private static List<String> stepsConsuming(String typeName) {
		List<String> found = new ArrayList<>();
		for (Path step : stepsOf(interpreterPack())) {
			var inputs = read(step).path("inputTypeNames");
			for (var input : inputs) {
				if (typeName.equals(input.asText())) {
					found.add(step.getFileName().toString());
				}
			}
		}
		return found;
	}

	/**
	 * Every step file of a pack, definitions nested one level below it.
	 * @param pack the pack directory
	 * @return the step files found under it
	 */
	private static List<Path> stepsOf(Path pack) {
		try (Stream<Path> files = Files.walk(pack)) {
			return files.filter((file) -> file.getFileName().toString().endsWith(".yml")).filter((file) -> {
				Path parent = file.getParent();
				return parent != null && "steps".equals(String.valueOf(parent.getFileName()));
			}).toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * One YAML file as a tree.
	 * @param file the file to read
	 * @return its root node
	 */
	private static JsonNode read(Path file) {
		try {
			return Yaml.mapper().readTree(file.toFile());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read " + file, ex);
		}
	}

	/**
	 * The repository's {@code agent-specs/skill-interpreter/}, found by walking up from
	 * the working directory, which is how the other scan over these packs locates them.
	 * @return the interpreter pack's directory
	 */
	private static Path interpreterPack() {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path candidate = dir.resolve("agent-specs").resolve(INTERPRETER_PACK);
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not locate the repository's agent-specs/" + INTERPRETER_PACK);
	}

}
