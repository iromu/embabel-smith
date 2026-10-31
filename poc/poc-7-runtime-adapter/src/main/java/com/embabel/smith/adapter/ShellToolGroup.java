package com.embabel.smith.adapter;

import com.embabel.agent.core.ToolGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@code shell} tool group as a platform {@link ToolGroup} bean, so the Embabel platform
 * registers it under the role {@code shell}. A skill-pack action that declares
 * {@code toolGroups: [shell]} then gets the real {@link ShellCommandTool} injected by the platform —
 * the "Skill Runtime Adapter" bridge from doc 03 §2.3 (Option A, the shell tool group).
 */
@Configuration
public class ShellToolGroup {

    /** The tool-group role the skill-pack actions reference. */
    public static final String ROLE = "shell";

    @Bean
    public ToolGroup shellToolGroupBean() {
        return ShellToolGroupFactory.shellGroup();
    }
}
