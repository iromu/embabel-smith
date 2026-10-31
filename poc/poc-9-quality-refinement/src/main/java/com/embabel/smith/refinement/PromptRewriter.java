package com.embabel.smith.refinement;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.validation.SkillPackValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * The eval → rewrite step (POC 9, the SkillAxe pattern from doc 03 §5). Two halves:
 * {@link #rewrite} is endpoint-gated (asks the model to revise the prompt so it satisfies the
 * failing criteria); {@link #applyRevisedPrompt} is pure Java (splices the revised prompt into the
 * named step and re-runs validation), so the re-validation half is verifiable offline.
 */
public class PromptRewriter {

    private final SkillPackValidator validator = new SkillPackValidator();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /** The result of applying a rewrite: the revised prompt, the revised pack, and whether it re-validates. */
    public record RewriteResult(String revisedPrompt, SkillPack revisedPack, boolean valid, List<String> issues) {
        public RewriteResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    /**
     * Endpoint-gated: ask the model to revise {@code currentPrompt} so it satisfies the failing
     * criteria. Uses the OpenAI-compatible chat-completions endpoint directly (the endpoint is
     * already env-driven for the rest of the POC).
     */
    public String rewrite(String baseUrl, String apiKey, String model,
                          String currentPrompt, List<SkillEvalTask> failingTasks) {
        StringBuilder criteria = new StringBuilder();
        for (SkillEvalTask task : failingTasks) {
            criteria.append("- input: ").append(task.input())
                    .append("; must satisfy: ").append(task.acceptanceCriteria()).append('\n');
        }
        String instruction = """
                You revise the prompt of a single skill action so it reliably produces output that
                satisfies the acceptance criteria below. Keep it a single action prompt; preserve the
                {{...}} template variables; return ONLY the revised prompt text, no commentary.

                Acceptance criteria the current prompt fails:
                %s

                Current prompt:
                %s
                """.formatted(criteria, currentPrompt);

        try {
            ObjectNode body = json.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", "You are an expert at writing precise LLM action prompts.");
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", instruction);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Rewrite LLM call failed: HTTP " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = json.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return content == null || content.isBlank() ? currentPrompt : content.trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Rewrite LLM call failed: " + e.getMessage(), e);
        }
    }

    /** Pure: splice a revised prompt into the named step and re-validate the revised draft. */
    public RewriteResult applyRevisedPrompt(SkillPack pack, String stepName, String revisedPrompt) {
        List<ActionStepSpec> newSteps = pack.steps().stream()
                .map(s -> s.name().equals(stepName) ? withPrompt(s, revisedPrompt) : s)
                .toList();
        SkillPack revised = new SkillPack(pack.manifest(), pack.goal(), newSteps, pack.types());
        List<String> issues = validator.validate(revised).issues().stream()
                .map(issue -> issue.code() + " — " + issue.message())
                .toList();
        return new RewriteResult(revisedPrompt, revised, issues.isEmpty(), issues);
    }

    private ActionStepSpec withPrompt(ActionStepSpec s, String prompt) {
        return new ActionStepSpec(
                s.stepType(), s.name(), s.description(), s.inputTypeNames(),
                s.outputTypeName(), prompt, s.pre(), s.post(),
                s.cost(), s.value(), s.canRerun(), s.nullable(), s.toolGroups());
    }
}
