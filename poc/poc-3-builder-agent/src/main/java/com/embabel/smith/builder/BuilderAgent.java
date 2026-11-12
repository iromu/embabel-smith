package com.embabel.smith.builder;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.CompilationError;
import com.embabel.agent.spec.compile.DefaultNaturalLanguageCompiler;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.agent.spec.model.StepSpecContext;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DraftQuality;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationIssue;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * POC 3 — the builder agent (design D2). It is a code-backed {@code @Agent} run by the default GOAP
 * planner. Its single goal is "a validated agent spec exists on disk" ({@link AgentSpecRef}),
 * reached by a deterministic pipeline of three code-backed actions:
 *
 * <pre>
 *   UserInput --compileFromDescription--> CompiledDraft
 *           --validateDraft-------------> ValidatedDraft
 *           --writeAgentSpec------------> AgentSpecRef   (goal)
 * </pre>
 *
 * The LLM is called only inside {@link #compileFromDescription(UserInput)} (endpoint-gated); the
 * validate and write steps are pure Java, so the pipeline order is deterministic and the offline
 * behaviour is testable. Design D5 is enforced here: an invalid draft is never written.
 */
@Agent(planner = PlannerType.GOAP, description = "Builds a validated agent spec from a natural-language description")
public class BuilderAgent {

    private final Ai ai;
    private final DataDictionary dataDictionary;
    private final Path outputDir;

    private final DraftToAgentSpec mapper = new DraftToAgentSpec();
    private final AgentSpecValidator validator = new AgentSpecValidator();
    private final AgentSpecWriter writer = new AgentSpecWriter();
    private final AgentSpecReader reader = new AgentSpecReader();

    public BuilderAgent(Ai ai, AgentPlatform platform,
                        @Value("${builder.output-dir:./built-specs}") String outputDir) {
        this.ai = ai;
        this.dataDictionary = platform;
        this.outputDir = Path.of(outputDir);
    }

    /**
     * Endpoint-gated: compiles a natural-language description into a draft spec via the NL
     * compiler. This is the only step that calls the LLM.
     */
    @Action(description = "Compile a natural-language description into a draft agent spec")
    public CompiledDraft compileFromDescription(UserInput description) {
        if (ai == null) {
            throw new IllegalStateException(
                    "compileFromDescription requires a live LLM (an Ai bean). The offline tests "
                            + "exercise validate/write directly.");
        }
        String name = agentNameFrom(description.getContent());
        DefaultNaturalLanguageCompiler compiler =
                new DefaultNaturalLanguageCompiler(ai, LlmOptions.withDefaults());
        StepSpecContext context = new StepSpecContext(
                name, dataDictionary, List.of(), List.of(), List.of());
        AgentCompilationResult result = compiler.compileAgent(name, description.getContent(), context);
        List<String> issues = result.getErrors().stream().map(CompilationError::getMessage).toList();
        AgentSpec pack = mapper.toAgentSpec(result, name);

        // Measure the draft's goal shape (doc 02 caveat #2) before and after the builder's
        // normalization, so the log shows whether the safety net repaired a degenerate draft.
        String rawGoal = result.getGoal() == null ? null : result.getGoal().getOutputTypeName();
        List<String> rawOutputs = result.getActions().stream()
                .map(PromptedActionSpec::getOutputTypeName).toList();
        DraftQuality.Assessment raw = DraftQuality.assess(rawGoal, rawOutputs);
        List<String> normalizedOutputs = pack.steps().stream()
                .map(ActionStepSpec::outputTypeName).toList();
        DraftQuality.Assessment normalized = DraftQuality.assess(pack.goalOutputType(), normalizedOutputs);
        System.out.println("[builder] draft quality: raw=" + raw.verdict()
                + (raw.degenerate() ? " (degenerate — " + raw.reason() + ")" : " (ok)")
                + " -> normalized=" + normalized.verdict());

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
        List<String> issues = toMessages(validator.validate(readBack).issues());
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

    private String agentNameFrom(String description) {
        String slug = description.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-$", "");
        }
        return slug.isEmpty() ? "built-spec" : slug;
    }
}
