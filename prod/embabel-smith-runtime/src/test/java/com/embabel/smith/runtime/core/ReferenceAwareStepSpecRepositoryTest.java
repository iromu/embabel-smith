/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.model.StepSpec;
import com.embabel.agent.spec.model.StepSpecContext;
import com.embabel.agent.spec.persistence.StepSpecRepository;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the wrapper that decides what a step's {@code references} declaration
 * actually attaches.
 *
 * <p>
 * <b>Why the context is the assertion.</b> A deployed {@code AgentScope} publishes no
 * tool list, so nothing downstream of {@code deploy()} can be asked what a step was
 * offered. What is decidable without a model is the {@link StepSpecContext} this class
 * puts in front of the step it wrapped — upstream resolves a declared name against that
 * list with a silent {@code map.get}, so a reference missing from it fails without a
 * trace, and a reference present in it but not named by the step fails the same way. Both
 * halves are pinned here.
 *
 * <p>
 * The delegate is the real {@link YmlStepSpecRepository} over a staged directory, so the
 * step under test is the class upstream actually emits — {@code PromptedActionSpec} — and
 * not a stand-in that could pass by being the wrong shape.
 */
class ReferenceAwareStepSpecRepositoryTest {

	@TempDir
	Path tmp;

	@Test
	void theContextHandedToTheStepCarriesTheReferenceThatHoldsItsDeclaration() throws IOException {
		PackSkills skills = PackSkills.at(pack("repo-map"), "survey");
		ReferenceAwareStepSpecRepository repository = repository("repo-map");

		assertThat(repository.findAll()).isNotEmpty();
		List<LlmReference> attached = skills.references();
		StepSpecContext context = context();

		StepSpecContext carried = ReferenceAwareStepSpecRepository.carrying(context, attached);

		assertThat(carried.getAvailableReferences()).extracting(LlmReference::getName).containsExactly("survey-skills");
		// Everything the scope builder put there survives the copy.
		assertThat(carried.getName()).isEqualTo(context.getName());
		assertThat(carried.getDataDictionary()).isSameAs(context.getDataDictionary());
		assertThat(carried.getToolGroups()).isEqualTo(context.getToolGroups());
		assertThat(carried.getTools()).isEqualTo(context.getTools());
	}

	@Test
	void aStepDeclaringNothingIsHandedTheContextItWasGivenUnchanged() throws IOException {
		StepSpecContext context = context();

		assertThat(ReferenceAwareStepSpecRepository.carrying(context, List.of())).isSameAs(context);
	}

	@Test
	void theDelegateIsAskedForEveryReadAndNothingIsDroppedOnTheWayOut() throws IOException {
		RecordingDelegate delegate = new RecordingDelegate(
				new YmlStepSpecRepository(steps("repo-map").toString(), List.of()));
		new ReferenceAwareStepSpecRepository(delegate, Map.of("locate", PackSkills.at(pack("repo-map"), "survey")))
			.findAll();

		assertThat(delegate.findAllCalls).isEqualTo(1);
	}

	@Test
	void aDeclarationThePackDoesNotCarryIsNamedRatherThanSkipped() throws IOException {
		ReferenceAwareStepSpecRepository repository = repository("typo-map");
		repository.findAll();

		assertThat(repository.unresolved()).containsExactly(
				"step 'locate' declares reference 'typo-map' that no skill folder its definition can reach carries");
		// Nothing attached, so the step is emitted as authored rather than rewritten to
		// name a container that holds nothing.
		assertThat(repository.attached()).isEmpty();
	}

	@Test
	void aDeclarationThatResolvesIsReportedUnderTheContainerItAttached() throws IOException {
		ReferenceAwareStepSpecRepository repository = repository("repo-map");
		repository.findAll();

		assertThat(repository.attached()).containsExactly("locate -> [survey-skills]");
		assertThat(repository.unresolved()).isEmpty();
	}

	@Test
	void aStepDeclaringNoReferenceIsNeitherRewrittenNorReported() throws IOException {
		ReferenceAwareStepSpecRepository repository = new ReferenceAwareStepSpecRepository(
				new YmlStepSpecRepository(steps(null).toString(), List.of()),
				Map.of("locate", PackSkills.at(pack("repo-map"), "survey")));
		repository.findAll();

		assertThat(repository.attached()).isEmpty();
		assertThat(repository.unresolved()).isEmpty();
	}

	/**
	 * The wrapper over a staged directory holding one action step.
	 * @param declared the name the step declares, or {@code null} for a step that
	 * declares no {@code references} key at all
	 * @return the wrapper, its skills standing for the definition named {@code survey}
	 */
	private ReferenceAwareStepSpecRepository repository(String declared) throws IOException {
		return new ReferenceAwareStepSpecRepository(new YmlStepSpecRepository(steps(declared).toString(), List.of()),
				Map.of("locate", PackSkills.at(pack("repo-map"), "survey")));
	}

	/**
	 * Write a staged step directory with the one step under test.
	 * @param declared the {@code references} entry to write, or {@code null} to omit the
	 * key
	 * @return the directory a {@code YmlStepSpecRepository} reads
	 */
	private Path steps(String declared) throws IOException {
		Path dir = Files.createDirectories(this.tmp.resolve("steps-" + declared));
		StringBuilder step = new StringBuilder("""
				stepType: action
				name: locate
				description: "Locate the files the request is about"
				inputTypeNames:
				  - com.embabel.agent.domain.io.UserInput
				outputTypeName: RepoMap
				toolGroups:
				  - file
				""");
		if (declared != null) {
			step.append("references:\n  - ").append(declared).append('\n');
		}
		step.append("prompt: |\n  Find the files. {{userInput.content}}\n");
		Files.writeString(dir.resolve("0-locate.yml"), step.toString());
		return dir;
	}

	/**
	 * A definition directory carrying one skill of the given name and nothing else.
	 * @param skillName the name the skill declares
	 * @return the pack directory
	 */
	private Path pack(String skillName) throws IOException {
		Path skill = this.tmp.resolve("survey/skills/" + skillName);
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"),
				"---\nname: " + skillName + "\ndescription: A skill for the test\n---\n\nRead these paths.\n");
		Files.createDirectories(this.tmp.resolve("survey/steps"));
		Files.writeString(this.tmp.resolve("survey/manifest.yml"), "name: survey\nversion: 0.1.0\n");
		return this.tmp.resolve("survey");
	}

	private static StepSpecContext context() {
		return new StepSpecContext("smith-spec-scope", DataDictionary.fromClasses("test", UserInput.class), List.of(),
				List.of(), List.of());
	}

	/**
	 * A delegate that counts the reads it was asked for, so a wrapper that quietly
	 * stopped delegating would fail rather than deploy nothing.
	 */
	private static final class RecordingDelegate implements StepSpecRepository {

		private final StepSpecRepository delegate;

		private int findAllCalls;

		RecordingDelegate(StepSpecRepository delegate) {
			this.delegate = delegate;
		}

		@Override
		public StepSpec<?> save(StepSpec<?> stepSpec) {
			return this.delegate.save(stepSpec);
		}

		@Override
		public Iterable<StepSpec<?>> findAll() {
			this.findAllCalls++;
			return this.delegate.findAll();
		}

	}

}
