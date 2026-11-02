package com.embabel.smith.remote;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Action;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.Goal;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.remote.action.RestServer;
import com.embabel.agent.remote.action.RestServerRegistration;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The remote-action runtime-adapter POC (POC 16).
 *
 * <p>Boots an Embabel {@link AgentPlatform}, starts a {@link RemoteActionSidecar}, pulls the
 * sidecar's action in as a {@code RestAction} (via {@link RestServer#agentScope}), and runs a GOAP
 * plan that <em>plans over and executes</em> the remote action exactly like a local one. The sidecar
 * runs deterministic code (no LLM), so the whole plan is offline-capable — the model endpoint is only
 * present for platform boot, never invoked.
 *
 * <p>Run:
 * <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://...   (any OpenAI-compatible endpoint; unused by the plan)
 *   export OPENAI_CUSTOM_API_KEY=...
 *   export OPENAI_CUSTOM_MODELS=...
 *   export EMBABEL_MODELS_DEFAULT_LLM=...
 *   mvn -pl poc/poc-16-remote-action spring-boot:run -Dbuilder.input="..."
 * </pre>
 */
@SpringBootApplication
public class RemoteActionApp implements CommandLineRunner {

    private final AgentPlatform platform;
    private final ModelEndpointConfig endpoint;
    private final int sidecarPort;
    private final String input;

    public RemoteActionApp(AgentPlatform platform,
                           @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
                           @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
                           @Value("${OPENAI_CUSTOM_MODELS:}") String models,
                           @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
                           @Value("${builder.sidecar-port:18090}") int sidecarPort,
                           @Value("${builder.input:Explain how a remote worker pool runs a task}") String input) {
        this.platform = platform;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
        this.sidecarPort = sidecarPort;
        this.input = input;
    }

    public static void main(String[] args) {
        SpringApplication.run(RemoteActionApp.class, args);
    }

    @Override
    public void run(String... args) {
        String configError = endpoint.configError();
        if (configError != null) {
            System.out.println("NOTE: " + configError
                    + " — the plan is fully deterministic (GOAP + remote action), so the endpoint is "
                    + "only needed for platform boot and is never called.");
        }

        RemoteActionSidecar sidecar = new RemoteActionSidecar();
        try {
            sidecar.start(sidecarPort);
            System.out.println("=== Sidecar up at " + sidecar.baseUrl() + " ===");
            String result = runRemotePlan(sidecar.baseUrl());

            System.out.println("=== Remote action result (produced by the sidecar, not the LLM) ===");
            System.out.println(result);

            List<String> actions = platform.getActions().stream()
                    .map(a -> a.getName())
                    .filter(n -> n != null && n.contains("remoteDigest"))
                    .toList();
            System.out.println("=== Registered remote action on the platform: " + actions + " ===");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to start the remote-action sidecar", e);
        } finally {
            sidecar.stop();
        }
    }

    /** Pulls the sidecar's action in as a RestAction and runs a GOAP plan over it. */
    private String runRemotePlan(String baseUrl) {
        RestServerRegistration registration =
                new RestServerRegistration(baseUrl, "digest-sidecar", "Deterministic text-digest sidecar");
        RestClient restClient = RestClient.builder().build();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        RestServer restServer = new RestServer(registration, restClient, objectMapper);
        AgentScope restScope = restServer.agentScope(platform);

        // The RestServer scope carries the remote action but no goal, so add one: "produce a
        // BackgroundMessage". The only action that produces BackgroundMessage is the remote one, so
        // GOAP must plan UserInput -> remoteDigest -> BackgroundMessage.
        List<Action> actions = restScope.getActions();
        Goal goal = Goal.Companion.createInstance(
                "remoteDigestGoal", BackgroundMessage.class,
                "Digest the input on the remote sidecar and return a BackgroundMessage");
        // The platform has no base domain types; register the built-in I/O types the action and
        // goal reference (the same JvmType seam POC 1 uses).
        Collection<DomainType> types = List.of(
                new JvmType(UserInput.class),
                new JvmType(BackgroundMessage.class));

        AgentScope scope = AgentScope.Companion.invoke(
                "remote-agent-scope", "GOAP scope over a remote-action sidecar",
                actions, Set.of(goal), types, Set.of(), false, null);
        platform.deploy(scope);

        Agent agent = scope.createAgent("remote-agent", "remote", "Remote-action agent (GOAP over a sidecar)");
        platform.deploy(agent);

        AgentProcess process = platform.runAgentFrom(
                agent,
                ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
                Map.of("userInput", new UserInput(input)));

        return extractOutput(process);
    }

    private String extractOutput(AgentProcess process) {
        Object last = process.lastResult();
        if (last instanceof BackgroundMessage message) {
            return message.getContent();
        }
        if (last instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null) {
                return content.toString();
            }
            return map.toString();
        }
        throw new IllegalStateException("No remote-action output produced (status=" + process.getStatus()
                + ", lastResult=" + last + ").");
    }
}
