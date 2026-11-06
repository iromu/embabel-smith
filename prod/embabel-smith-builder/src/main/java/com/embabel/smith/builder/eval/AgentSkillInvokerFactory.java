package com.embabel.smith.builder.eval;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.SkillPackWriter;
import com.embabel.smith.common.model.SkillPack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The production {@link InvokerFactory}: deploys the pack just built and returns an invoker that
 * runs it as a single typed goal run (the non-chat invocation path, POC 5). Endpoint-gated — each
 * call deploys the pack and the invocation calls the model.
 */
public class AgentSkillInvokerFactory implements InvokerFactory {

    private final AgentPlatform platform;
    private final SkillPackTypeMapper typeMapper = new SkillPackTypeMapper();
    private final StepSpecAssembler assembler = new StepSpecAssembler();
    private final SkillPackWriter writer = new SkillPackWriter();

    public AgentSkillInvokerFactory(AgentPlatform platform) {
        this.platform = platform;
    }

    @Override
    public SkillInvoker invokerFor(SkillPack pack) {
        Path packDir = writeTempPack(pack);
        DataDictionary types = platform.plus(
                DataDictionary.fromDomainTypes("builder-eval-types", typeMapper.allRuntimeTypes(pack)));
        Path stepsDir;
        try {
            stepsDir = assembler.assemble(List.of(packDir), Files.createTempDirectory("builder-eval-steps-"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to assemble step specs for eval: " + e.getMessage(), e);
        }
        String suffix = Long.toHexString(System.nanoTime());
        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
                "builder-eval-scope-" + suffix,
                platform,
                new YmlStepSpecRepository(stepsDir.toString(), List.of()),
                types,
                List.of(),
                List.of());
        Agent agent = builder.deploy()
                .createAgent("builder-eval-agent-" + suffix, "eval-skill", "Builder eval: " + pack.name());
        platform.deploy(agent);
        AgentInvocation<BackgroundMessage> invocation = AgentInvocation.create(platform, BackgroundMessage.class);
        return input -> {
            BackgroundMessage result = invocation.invoke(Map.of("userInput", new UserInput(input)));
            return result == null ? null : result.getContent();
        };
    }

    private Path writeTempPack(SkillPack pack) {
        try {
            Path dir = Files.createTempDirectory("builder-eval-pack-");
            writer.write(pack, dir);
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write pack for eval: " + e.getMessage(), e);
        }
    }
}
