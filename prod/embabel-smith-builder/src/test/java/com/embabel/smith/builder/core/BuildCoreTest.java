package com.embabel.smith.builder.core;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.TestFixtures;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the build core (task 1.3): a coherent description builds (via a fake
 * generator), an invalid draft writes nothing, an ambiguous description returns its
 * issues, and the build dispatches to the generator of its technique. The eval invoker is
 * a fake that always passes, so the build reaches the publish step.
 */
class BuildCoreTest {

	private BuilderService service(AgentSpecGenerator generator, Path scratch, Path library) {
		return service(new BuilderAgent(generator, scratch.toString()), library);
	}

	private BuilderService service(BuilderAgent agent, Path library) {
		InvokerFactory factory = pack -> input -> "output: " + input;
		PromptRewriter rewriter = (current, feedback) -> current;
		List<EvalTask> tasks = List.of(new EvalTask("runs", "in", "output"));
		return new BuilderService(agent, factory, rewriter, tasks);
	}

	private static AgentSpecGenerator embabel(AgentSpecCompiler compiler) {
		return new EmbabelAgentSpecGenerator(compiler, null);
	}

	@Test
	void coherentDescriptionBuildsAndPublishes(@TempDir Path tmp) {
		Path scratch = tmp.resolve("scratch");
		Path library = tmp.resolve("library");
		AgentSpecGenerator generator = embabel((name, desc, dd) -> TestFixtures.coherent(name));

		BuildResult result = service(generator, scratch, library).build("explain coffee machines",
				PublishTarget.parse("folder:" + library), BuildOptions.defaults());

		assertThat(result.isBuilt()).isTrue();
		Path packDir = result.published().location();
		assertThat(packDir.resolve("manifest.yml")).isRegularFile();
		assertThat(packDir.resolve("goal.yml")).isRegularFile();
		assertThat(packDir.resolve("lifecycle.yml")).isRegularFile();
		// The D5 scratch write also happened.
		assertThat(scratch.resolve(result.packName())).isDirectory();
	}

	@Test
	void invalidDraftWritesNothing(@TempDir Path tmp) {
		Path scratch = tmp.resolve("scratch");
		Path library = tmp.resolve("library");
		AgentSpecGenerator generator = embabel((name, desc, dd) -> TestFixtures.dangling(name));

		BuildResult result = service(generator, scratch, library).build("bad spec",
				PublishTarget.parse("folder:" + library), BuildOptions.defaults());

		assertThat(result.isBuilt()).isFalse();
		assertThat(result.stage()).isEqualTo("build");
		assertThat(result.issues()).anyMatch(issue -> issue.contains("NonExistentType"));
		assertThat(result.draft()).isNotNull();
		assertThat(result.draft().name()).isEqualTo(result.packName());
		assertThat(library).doesNotExist();
		// Nothing was written to the scratch area either.
		assertThat(java.nio.file.Files.exists(scratch)).isFalse();
	}

	@Test
	void ambiguousDescriptionReturnsIssues(@TempDir Path tmp) {
		Path scratch = tmp.resolve("scratch");
		Path library = tmp.resolve("library");
		AgentSpecGenerator generator = embabel((name, desc, dd) -> TestFixtures.ambiguous(name));

		BuildResult result = service(generator, scratch, library).build("ambiguous",
				PublishTarget.parse("folder:" + library), BuildOptions.defaults());

		assertThat(result.isBuilt()).isFalse();
		assertThat(result.stage()).isEqualTo("build");
		assertThat(result.issues()).anyMatch(issue -> issue.contains("ambiguous"));
		assertThat(result.draft()).isNotNull();
		assertThat(result.draft().name()).isEqualTo(result.packName());
		assertThat(library).doesNotExist();
	}

	@Test
	void buildDispatchesToTheGeneratorOfItsTechnique(@TempDir Path tmp) {
		Path scratch = tmp.resolve("scratch");
		Path library = tmp.resolve("library");
		AtomicReference<String> used = new AtomicReference<>();
		AgentSpecGenerator singleShot = (description) -> {
			used.set("single-shot");
			return draft("single-shot-pack");
		};
		AgentSpecGenerator embabel = (description) -> {
			used.set("embabel");
			return draft("embabel-pack");
		};
		BuilderAgent agent = new BuilderAgent(Map.of(GenerationTechnique.SINGLE_SHOT, singleShot,
				GenerationTechnique.EMBABEL, embabel), scratch.toString());
		BuilderService service = service(agent, library);

		BuildResult byDefault = service.build("explain coffee machines", PublishTarget.parse("folder:" + library),
				BuildOptions.defaults());
		assertThat(used.get()).isEqualTo("single-shot");
		assertThat(byDefault.packName()).isEqualTo("single-shot-pack");

		BuildResult embabelBuild = service.build("explain coffee machines", PublishTarget.parse("folder:" + library),
				new BuildOptions(BuildOptions.DEFAULT_MAX_REFINE, BuildOptions.DEFAULT_SCORE_GATE, false, false,
						GenerationTechnique.EMBABEL));
		assertThat(used.get()).isEqualTo("embabel");
		assertThat(embabelBuild.packName()).isEqualTo("embabel-pack");
	}

	private static CompiledDraft draft(String name) {
		AgentSpec pack = new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name + "-goal", "The goal", "BackgroundMessage"),
				List.of(ActionStepSpec.action("say", "Say it", List.of("UserInput"), "BackgroundMessage",
						"Repeat: {{userInput.content}}")),
				List.of());
		return new CompiledDraft(name, pack, List.of());
	}

}
