package com.embabel.smith.builder.publish;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests of publishing (task 4.3): a folder publish writes the pack plus its sidecar; a git
 * publish commits into a local bare repository's working tree and (with push) lands the pack in the
 * remote; a same-named pack collides unless {@code --overwrite} replaces it.
 */
class PublisherTest {

    private final SkillPack pack = SampleSkillPacks.explainer();
    private final LifecycleMetadata lifecycle = new LifecycleStamper().stamp(pack, 1.0);

    @Test
    void folderPublishWritesThePackAndSidecar(@TempDir Path library) throws Exception {
        FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));

        PublishResult result = publisher.publish(pack, lifecycle, false, false);

        assertThat(result.location()).isEqualTo(library.resolve("explainer"));
        assertThat(result.location().resolve("manifest.yml")).isRegularFile();
        assertThat(result.location().resolve("lifecycle.yml")).isRegularFile();
        assertThat(result.pushed()).isFalse();
    }

    @Test
    void aFolderCollisionBlocksUnlessOverwrite(@TempDir Path library) throws Exception {
        FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));
        publisher.publish(pack, lifecycle, false, false);

        assertThatThrownBy(() -> publisher.publish(pack, lifecycle, false, false))
                .isInstanceOf(CollisionException.class)
                .hasMessageContaining("explainer");

        PublishResult replaced = publisher.publish(pack, lifecycle, true, false);
        assertThat(replaced.location().resolve("manifest.yml")).isRegularFile();
    }

    @Test
    void gitPublishCommitsAndPushesIntoALocalRepo(@TempDir Path tmp) throws Exception {
        Path bare = tmp.resolve("repo.git");
        runGit("init", "--bare", bare.toString());

        GitPublisher publisher = new GitPublisher(PublishTarget.parse("git:" + bare));
        try {
            // Local commit only (no push): the working tree has the pack, the remote does not.
            PublishResult local = publisher.publish(pack, lifecycle, false, false);
            assertThat(local.location().resolve("manifest.yml")).isRegularFile();
            assertThat(local.pushed()).isFalse();

            // A same-named pack now exists in the working tree → collision without overwrite.
            assertThatThrownBy(() -> publisher.publish(pack, lifecycle, false, false))
                    .isInstanceOf(CollisionException.class);

            // Overwrite + push: the commit lands in the bare repository.
            PublishResult pushed = publisher.publish(pack, lifecycle, true, true);
            assertThat(pushed.pushed()).isTrue();

            Path verify = tmp.resolve("verify");
            runGit("clone", "--quiet", bare.toString(), verify.toString());
            assertThat(verify.resolve("explainer").resolve("manifest.yml")).isRegularFile();
            assertThat(verify.resolve("explainer").resolve("lifecycle.yml")).isRegularFile();
        } finally {
            publisher.close();
        }
    }

    private static void runGit(String... args) {
        List<String> command = new java.util.ArrayList<>(List.of("git", "-c", "core.askpass=false"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + out);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
