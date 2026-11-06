package com.embabel.smith.builder.core;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.TestFixtures;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.publish.PublishTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the build core (task 1.3): a coherent description builds (via a fake compiler),
 * an invalid draft writes nothing, and an ambiguous description returns its issues. The eval
 * invoker is a fake that always passes, so the build reaches the publish step.
 */
class BuildCoreTest {

    private BuilderService service(SkillCompiler compiler, Path scratch, Path library) {
        BuilderAgent agent = new BuilderAgent(compiler, null, scratch.toString());
        InvokerFactory factory = pack -> input -> "output: " + input;
        PromptRewriter rewriter = (current, feedback) -> current;
        List<EvalTask> tasks = List.of(new EvalTask("runs", "in", "output"));
        return new BuilderService(agent, factory, rewriter, tasks);
    }

    @Test
    void coherentDescriptionBuildsAndPublishes(@TempDir Path tmp) {
        Path scratch = tmp.resolve("scratch");
        Path library = tmp.resolve("library");
        SkillCompiler compiler = (name, desc, dd) -> TestFixtures.coherent(name);

        BuildResult result = service(compiler, scratch, library)
                .build("explain coffee machines", PublishTarget.parse("folder:" + library), BuildOptions.defaults());

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
        SkillCompiler compiler = (name, desc, dd) -> TestFixtures.dangling(name);

        BuildResult result = service(compiler, scratch, library)
                .build("bad skill", PublishTarget.parse("folder:" + library), BuildOptions.defaults());

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
        SkillCompiler compiler = (name, desc, dd) -> TestFixtures.ambiguous(name);

        BuildResult result = service(compiler, scratch, library)
                .build("ambiguous", PublishTarget.parse("folder:" + library), BuildOptions.defaults());

        assertThat(result.isBuilt()).isFalse();
        assertThat(result.stage()).isEqualTo("build");
        assertThat(result.issues()).anyMatch(issue -> issue.contains("ambiguous"));
        assertThat(result.draft()).isNotNull();
        assertThat(result.draft().name()).isEqualTo(result.packName());
        assertThat(library).doesNotExist();
    }
}
