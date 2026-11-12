package com.embabel.smith.agentspecformat;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the on-demand load (task 4.1, offline part): only the goal and the requested
 * action steps are copied, not the whole pack.
 */
class OnDemandAssemblerTest {

	private final SpecSurface surface = new SpecSurface();

	private final OnDemandAssembler assembler = new OnDemandAssembler();

	@Test
	void copiesOnlyTheGoalAndTheRequestedChain(@TempDir Path dir) throws Exception {
		Path packDir = new AgentSpecWriter().write(SampleAgentSpecs.explainer(), dir.resolve("pack"));
		var chain = surface.actionChain(SampleAgentSpecs.explainer());
		Path target = dir.resolve("on-demand");

		assembler.assemble(packDir, target, chain);

		assertThat(Files.isRegularFile(target.resolve("goal.yml"))).isTrue();
		assertThat(Files.isRegularFile(target.resolve("extractTopic.yml"))).isTrue();
		assertThat(Files.isRegularFile(target.resolve("writeExplainer.yml"))).isTrue();
		// manifest and types are not step specs, so they are not copied on demand
		assertThat(Files.isRegularFile(target.resolve("manifest.yml"))).isFalse();
		assertThat(Files.isDirectory(target.resolve("types"))).isFalse();
	}

}
