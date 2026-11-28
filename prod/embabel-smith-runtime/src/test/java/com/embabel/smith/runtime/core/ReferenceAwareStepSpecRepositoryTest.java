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
				new YmlStepSpecRepository(steps(names("repo-map")).toString(), List.of()));
		new ReferenceAwareStepSpecRepository(delegate, Map.of("locate", PackReferences.at(pack("repo-map"), "survey")))
			.findAll();

		assertThat(delegate.findAllCalls).isEqualTo(1);
	}

	@Test
	void aDeclarationThePackDoesNotCarryIsNamedRatherThanSkipped() throws IOException {
		ReferenceAwareStepSpecRepository repository = repository("typo-map");
		repository.findAll();

		assertThat(repository.unresolved())
			.containsExactly("step 'locate' declares reference 'typo-map' that neither its definition's skills nor"
					+ " its references file carries");
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
				new YmlStepSpecRepository(steps(List.of()).toString(), List.of()),
				Map.of("locate", PackReferences.at(pack("repo-map"), "survey")));
		repository.findAll();

		assertThat(repository.attached()).isEmpty();
		assertThat(repository.unresolved()).isEmpty();
	}

	@Test
	void aStepDeclaringASkillAndADocumentIsAttachedToBothHolders() throws IOException {
		ReferenceAwareStepSpecRepository repository = twoKindRepository(List.of("repo-map", "domain-guide"), true);
		repository.findAll();

		// One step, two kinds, one line: the container its skill folder belongs to, and
		// the document its references file declares.
		assertThat(repository.attached()).containsExactly("locate -> [survey-skills, domain-guide]");
		assertThat(repository.unresolved()).isEmpty();
	}

	@Test
	void theSameStepOnAPackWithoutTheReferencesFileAttachesTheSkillAndNamesTheOther() throws IOException {
		ReferenceAwareStepSpecRepository repository = twoKindRepository(List.of("repo-map", "domain-guide"), false);
		repository.findAll();

		// The pair to the test above, and the shape of the mistake it rules out: a run
		// that read only skill folders would present this pack's step exactly as it
		// presents the other one. Here the skill still attaches, so the line is not
		// vacuous, and the name that went missing is stated rather than left to the
		// upstream skip.
		assertThat(repository.attached()).containsExactly("locate -> [survey-skills]");
		assertThat(repository.unresolved()).containsExactly("step 'locate' declares reference 'domain-guide' that"
				+ " neither its definition's skills nor its references file carries");
	}

	@Test
	void bothHoldersReachTheCallUnderTheirOwnPrefixesWithTheirOwnDescriptions() throws IOException {
		PackReferences refs = PackReferences.at(twoKindPack(true), "survey");
		StepSpecContext context = context();

		StepSpecContext carried = ReferenceAwareStepSpecRepository.carrying(context,
				List.of(refs.carried().get("repo-map"), refs.carried().get("domain-guide")));

		List<LlmReference> holders = carried.getAvailableReferences();
		assertThat(holders).extracting(LlmReference::getName).containsExactly("survey-skills", "domain-guide");
		// Each under its own prefix: the two prefixes differ, and every tool the skill
		// container lists sits behind the one that belongs to it.
		assertThat(holders.get(0).toolPrefix()).isNotEqualTo(holders.get(1).toolPrefix());
		assertThat(listed(holders.get(0))).isNotEmpty().allMatch((name) -> name.startsWith("survey_skills_"));
		// Both descriptions visible: each reference says what it is, in its own
		// contribution, and the two say different things.
		assertThat(holders.get(0).getDescription()).contains("Skills carried by");
		assertThat(holders.get(1).getDescription()).contains("Document domain.md");
		assertThat(holders.get(0).contribution()).contains("survey-skills").isNotEqualTo(holders.get(1).contribution());
	}

	/**
	 * The tool names a reference is listed under, as the naming strategy it carries would
	 * rename them.
	 * @param reference the value to list
	 * @return the names, in listing order
	 */
	private static List<String> listed(LlmReference reference) {
		return LlmReference.prefixedTools(reference).stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	/**
	 * The wrapper over a staged directory holding one action step.
	 * @param declared the name the step declares, or {@code null} for a step that
	 * declares no {@code references} key at all
	 * @return the wrapper, its references standing for the definition named
	 * {@code survey}
	 */
	private ReferenceAwareStepSpecRepository repository(String declared) throws IOException {
		return new ReferenceAwareStepSpecRepository(
				new YmlStepSpecRepository(steps(names(declared)).toString(), List.of()),
				Map.of("locate", PackReferences.at(pack("repo-map"), "survey")));
	}

	/**
	 * The wrapper over the same step declaring two names, standing for a definition that
	 * carries one of them as a skill and the other as a document.
	 * @param declared the names the step declares, in order
	 * @param referencesFile whether the pack writes {@code references.yml} at all — the
	 * variant without it is the pack that can only ever attach the skill
	 * @return the wrapper
	 */
	private ReferenceAwareStepSpecRepository twoKindRepository(List<String> declared, boolean referencesFile)
			throws IOException {
		return new ReferenceAwareStepSpecRepository(new YmlStepSpecRepository(steps(declared).toString(), List.of()),
				Map.of("locate", PackReferences.at(twoKindPack(referencesFile), "survey")));
	}

	private static List<String> names(String declared) {
		return (declared == null) ? List.of() : List.of(declared);
	}

	/**
	 * Write a staged step directory with the one step under test.
	 * @param declared the {@code references} entries to write; an empty list omits the
	 * key
	 * @return the directory a {@code YmlStepSpecRepository} reads
	 */
	private Path steps(List<String> declared) throws IOException {
		Path dir = Files.createDirectories(this.tmp.resolve("steps-" + String.join("+", declared)));
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
		if (!declared.isEmpty()) {
			step.append("references:\n");
			for (String name : declared) {
				step.append("  - ").append(name).append('\n');
			}
		}
		step.append("prompt: |\n  Find the files. {{userInput.content}}\n");
		Files.writeString(dir.resolve("0-locate.yml"), step.toString());
		return dir;
	}

	/**
	 * The pack of {@link #pack(String)}, additionally carrying the folder and the file a
	 * document reference resolves against.
	 * @param referencesFile whether to write {@code references.yml} declaring that file
	 * @return the pack directory
	 */
	private Path twoKindPack(boolean referencesFile) throws IOException {
		Path packDir = pack("repo-map");
		Files.createDirectories(packDir.resolve("docs"));
		Files.writeString(packDir.resolve("docs/domain.md"), "The domain of the survey pack.\n");
		if (referencesFile) {
			Files.writeString(packDir.resolve(PackReferences.REFERENCES_FILE), """
					references:
					  - name: domain-guide
					    kind: document
					    path: docs/domain.md
					""");
		}
		return packDir;
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
