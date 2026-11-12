package com.embabel.smith.builder.core;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.CompilationError;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationIssue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The builder's build component (design D2): a code-backed {@code @Agent} whose single goal is
 * "a validated agent spec exists on disk" ({@link AgentSpecRef}), reached by a deterministic
 * pipeline of three code-backed actions:
 *
 * <pre>
 *   UserInput --compileFromDescription--> CompiledDraft
 *           --validateDraft-------------> ValidatedDraft
 *           --writeAgentSpec------------> AgentSpecRef   (goal)
 * </pre>
 *
 * The model is called only inside {@link #compileFromDescription(UserInput)}, and only through the
 * injected {@link AgentSpecCompiler} (endpoint-gated); the validate and write steps are pure Java, so
 * the pipeline order is deterministic and the offline behaviour is testable. Design D5 is enforced
 * here: an invalid draft is never written.
 *
 * <p>The {@link BuilderService} drives these actions directly (the same order the GOAP planner
 * would take), keeping the pipeline an Embabel agent while staying deterministic and offline-testable.
 */
@Agent(planner = PlannerType.GOAP, description = "Builds a validated agent spec from a natural-language description")
public class BuilderAgent {

    private final AgentSpecCompiler compiler;
    private final DataDictionary dataDictionary;
    private final Path outputDir;

    private final DraftToAgentSpec mapper = new DraftToAgentSpec();
    private final AgentSpecValidator validator = new AgentSpecValidator();
    private final AgentSpecWriter writer = new AgentSpecWriter();
    private final AgentSpecReader reader = new AgentSpecReader();

    public BuilderAgent(AgentSpecCompiler compiler, DataDictionary dataDictionary, String outputDir) {
        this.compiler = compiler;
        this.dataDictionary = dataDictionary;
        this.outputDir = Path.of(outputDir);
    }

    /**
     * Compiles a natural-language description into a draft spec via the injected
     * {@link AgentSpecCompiler}. Endpoint-gated for the production compiler.
     */
    @Action(description = "Compile a natural-language description into a draft agent spec")
    public CompiledDraft compileFromDescription(UserInput description) {
        String name = PackName.slug(description.getContent());
        AgentCompilationResult result = compiler.compile(name, description.getContent(), dataDictionary);
        List<String> issues = result.getErrors().stream().map(CompilationError::getMessage).toList();
        AgentSpec pack = mapper.toAgentSpec(result, name);
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
    @Action(description = "Write the validated agent spec to disk")
    @AchievesGoal(description = "A validated agent spec exists on disk")
    public AgentSpecRef writeAgentSpec(ValidatedDraft draft) {
        if (!draft.valid()) {
            throw new IllegalStateException("Refusing to write an invalid draft: " + draft.issues());
        }
        Path dir = outputDir.resolve(draft.pack().name());
        AgentSpec readBack;
        try {
            writer.write(draft.pack(), dir);
            readBack = reader.read(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write/re-read the agent spec at " + dir, e);
        }
        // Re-validate the on-disk layout (not just the in-memory read-back) so a layout defect in
        // the writer is caught (design D5).
        List<String> issues = toMessages(validator.validate(dir).issues());
        if (!issues.isEmpty()) {
            throw new IllegalStateException("Written pack failed re-validation: " + issues);
        }
        return new AgentSpecRef(dir, readBack.name(), readBack.version());
    }

    private List<String> toMessages(List<ValidationIssue> issues) {
        return issues.stream()
                .map(issue -> issue.code() + " — " + issue.message())
                .toList();
    }
}
