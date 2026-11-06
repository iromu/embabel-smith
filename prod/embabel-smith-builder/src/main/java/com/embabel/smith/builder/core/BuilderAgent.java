package com.embabel.smith.builder.core;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.CompilationError;
import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.io.SkillPackWriter;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.validation.SkillPackValidator;
import com.embabel.smith.common.validation.ValidationIssue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The builder's build component (design D2): a code-backed {@code @Agent} whose single goal is
 * "a validated skill-pack exists on disk" ({@link SkillPackRef}), reached by a deterministic
 * pipeline of three code-backed actions:
 *
 * <pre>
 *   UserInput --compileFromDescription--> CompiledDraft
 *           --validateDraft-------------> ValidatedDraft
 *           --writeSkillPack------------> SkillPackRef   (goal)
 * </pre>
 *
 * The model is called only inside {@link #compileFromDescription(UserInput)}, and only through the
 * injected {@link SkillCompiler} (endpoint-gated); the validate and write steps are pure Java, so
 * the pipeline order is deterministic and the offline behaviour is testable. Design D5 is enforced
 * here: an invalid draft is never written.
 *
 * <p>The {@link BuilderService} drives these actions directly (the same order the GOAP planner
 * would take), keeping the pipeline an Embabel agent while staying deterministic and offline-testable.
 */
@Agent(planner = PlannerType.GOAP, description = "Builds a validated skill-pack from a natural-language description")
public class BuilderAgent {

    private final SkillCompiler compiler;
    private final DataDictionary dataDictionary;
    private final Path outputDir;

    private final DraftToSkillPack mapper = new DraftToSkillPack();
    private final SkillPackValidator validator = new SkillPackValidator();
    private final SkillPackWriter writer = new SkillPackWriter();
    private final SkillPackReader reader = new SkillPackReader();

    public BuilderAgent(SkillCompiler compiler, DataDictionary dataDictionary, String outputDir) {
        this.compiler = compiler;
        this.dataDictionary = dataDictionary;
        this.outputDir = Path.of(outputDir);
    }

    /**
     * Compiles a natural-language description into a draft skill via the injected
     * {@link SkillCompiler}. Endpoint-gated for the production compiler.
     */
    @Action(description = "Compile a natural-language description into a draft skill-pack")
    public CompiledDraft compileFromDescription(UserInput description) {
        String name = PackName.slug(description.getContent());
        AgentCompilationResult result = compiler.compile(name, description.getContent(), dataDictionary);
        List<String> issues = result.getErrors().stream().map(CompilationError::getMessage).toList();
        SkillPack pack = mapper.toSkillPack(result, name);
        return new CompiledDraft(name, pack, issues);
    }

    /**
     * Pure: validates the draft's bindings and schemas (group-1 validation). An invalid draft is
     * reported, not silently accepted.
     */
    @Action(description = "Validate the draft's type bindings and prompt references")
    public ValidatedDraft validateDraft(CompiledDraft draft) {
        List<String> issues = toMessages(validator.validate(draft.pack()).issues());
        return new ValidatedDraft(draft.pack(), issues.isEmpty(), issues);
    }

    /**
     * Pure: writes a valid draft to disk and re-validates the written pack (group-1 validation).
     * Refuses to write an invalid draft (design D5).
     */
    @Action(description = "Write the validated skill-pack to disk")
    @AchievesGoal(description = "A validated skill-pack exists on disk")
    public SkillPackRef writeSkillPack(ValidatedDraft draft) {
        if (!draft.valid()) {
            throw new IllegalStateException("Refusing to write an invalid draft: " + draft.issues());
        }
        Path dir = outputDir.resolve(draft.pack().name());
        SkillPack readBack;
        try {
            writer.write(draft.pack(), dir);
            readBack = reader.read(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write/re-read the skill-pack at " + dir, e);
        }
        // Re-validate the on-disk layout (not just the in-memory read-back) so a layout defect in
        // the writer is caught (design D5).
        List<String> issues = toMessages(validator.validate(dir).issues());
        if (!issues.isEmpty()) {
            throw new IllegalStateException("Written pack failed re-validation: " + issues);
        }
        return new SkillPackRef(dir, readBack.name(), readBack.version());
    }

    private List<String> toMessages(List<ValidationIssue> issues) {
        return issues.stream()
                .map(issue -> issue.code() + " — " + issue.message())
                .toList();
    }
}
