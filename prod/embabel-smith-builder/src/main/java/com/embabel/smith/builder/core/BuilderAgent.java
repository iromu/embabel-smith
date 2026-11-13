/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.builder.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationIssue;

/**
 * The builder's build component (design D2): a code-backed {@code @Agent} whose single
 * goal is "a validated agent spec exists on disk" ({@link AgentSpecRef}), reached by a
 * deterministic pipeline of three code-backed actions:
 *
 * <pre>
 *   UserInput --compileFromDescription--> CompiledDraft
 *           --validateDraft-------------> ValidatedDraft
 *           --writeAgentSpec------------> AgentSpecRef   (goal)
 * </pre>
 *
 * The model is called only inside the compile step, and only through the injected
 * {@link AgentSpecGenerator} for the build's {@link GenerationTechnique}
 * (endpoint-gated); the validate and write steps are pure Java, so the pipeline order is
 * deterministic and the offline behaviour is testable. Design D5 is enforced here: an
 * invalid draft is never written.
 *
 * <p>
 * The {@code BuilderService} drives these actions directly (the same order the GOAP
 * planner would take), keeping the pipeline an Embabel agent while staying deterministic
 * and offline-testable.
 *
 * @author Ivan Rodriguez
 */
@Agent(planner = PlannerType.GOAP, description = "Builds a validated agent spec from a natural-language description")
public class BuilderAgent {

	private final Map<GenerationTechnique, AgentSpecGenerator> generators;

	private final Path outputDir;

	private final AgentSpecValidator validator = new AgentSpecValidator();

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private final AgentSpecReader reader = new AgentSpecReader();

	public BuilderAgent(Map<GenerationTechnique, AgentSpecGenerator> generators, String outputDir) {
		this.generators = Map.copyOf(generators);
		this.outputDir = Path.of(outputDir);
	}

	/**
	 * Convenience for callers with a single generator — an offline test, or a build that
	 * does not vary the technique — which then serves every technique.
	 * @param generator the generator to use for all techniques.
	 * @param outputDir the directory the validated spec is written to.
	 */
	public BuilderAgent(AgentSpecGenerator generator, String outputDir) {
		this(Map.of(GenerationTechnique.SINGLE_SHOT, generator, GenerationTechnique.EMBABEL, generator), outputDir);
	}

	/**
	 * Compiles a natural-language description into a draft spec with the default
	 * {@link GenerationTechnique}.
	 * @param description the natural-language description to compile.
	 * @return the compiled draft spec.
	 */
	@Action(description = "Compile a natural-language description into a draft agent spec")
	public CompiledDraft compileFromDescription(UserInput description) {
		return compileFromDescription(description, GenerationTechnique.SINGLE_SHOT);
	}

	/**
	 * Compiles a natural-language description into a draft spec with the named technique.
	 * Not an {@code @Action}: the technique is a build option, not a domain type the
	 * planner could produce, so it stays out of the action's inputs.
	 * @param description the natural-language description to compile.
	 * @param technique the generation technique to use.
	 * @return the compiled draft spec.
	 */
	public CompiledDraft compileFromDescription(UserInput description, GenerationTechnique technique) {
		AgentSpecGenerator generator = this.generators.get(technique);
		if (generator == null) {
			throw new IllegalStateException("No generator wired for technique " + technique);
		}
		return generator.generate(description.getContent());
	}

	/**
	 * Pure: validates the draft's bindings and schemas (group-1 validation). An invalid
	 * draft is reported, not silently accepted.
	 * @param draft the draft spec to validate.
	 * @return the draft plus any validation issues.
	 */
	@Action(description = "Validate the draft's type bindings and prompt references")
	public ValidatedDraft validateDraft(CompiledDraft draft) {
		List<String> issues = toMessages(this.validator.validate(draft.pack()).issues());
		return new ValidatedDraft(draft.pack(), issues.isEmpty(), issues);
	}

	/**
	 * Pure: writes a valid draft to disk and re-validates the written pack (group-1
	 * validation). Refuses to write an invalid draft (design D5).
	 * @param draft the validated draft to write.
	 * @return the reference to the agent spec written on disk.
	 */
	@Action(description = "Write the validated agent spec to disk")
	@AchievesGoal(description = "A validated agent spec exists on disk")
	public AgentSpecRef writeAgentSpec(ValidatedDraft draft) {
		if (!draft.valid()) {
			throw new IllegalStateException("Refusing to write an invalid draft: " + draft.issues());
		}
		Path dir = this.outputDir.resolve(draft.pack().name());
		AgentSpec readBack;
		try {
			this.writer.write(draft.pack(), dir);
			readBack = this.reader.read(dir);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write/re-read the agent spec at " + dir, ex);
		}
		// Re-validate the on-disk layout (not just the in-memory read-back) so a layout
		// defect in
		// the writer is caught (design D5).
		List<String> issues = toMessages(this.validator.validate(dir).issues());
		if (!issues.isEmpty()) {
			throw new IllegalStateException("Written pack failed re-validation: " + issues);
		}
		return new AgentSpecRef(dir, readBack.name(), readBack.version());
	}

	private List<String> toMessages(List<ValidationIssue> issues) {
		return issues.stream().map((issue) -> issue.code() + " — " + issue.message()).toList();
	}

}
