package com.embabel.smith.skillsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for the source-URI grammar: {@code folder:<path>} and {@code git:<url>[@<ref>]}.
 */
class SkillSourceParseTest {

    @Test
    @DisplayName("folder URI parses to a FolderSkillSource rooted at the path")
    void folderUriParses() {
        SkillSource source = SkillSource.parse("folder:./skill-packs");

        assertThat(source).isInstanceOf(FolderSkillSource.class);
        assertThat(((FolderSkillSource) source).root()).isEqualTo(Path.of("./skill-packs"));
    }

    @Test
    @DisplayName("absolute folder URI parses")
    void absoluteFolderUriParses() {
        SkillSource source = SkillSource.parse("folder:/tmp/packs");

        assertThat(source).isInstanceOf(FolderSkillSource.class);
        assertThat(((FolderSkillSource) source).root()).isEqualTo(Path.of("/tmp/packs"));
    }

    @Test
    @DisplayName("git URI with a ref parses to a pinned GitSkillSource")
    void gitUriWithRefParses() {
        SkillSource source = SkillSource.parse("git:https://example.com/org/skills.git@v1.2");

        GitSkillSource git = (GitSkillSource) source;
        assertThat(git.url()).isEqualTo("https://example.com/org/skills.git");
        assertThat(git.ref()).isEqualTo("v1.2");
    }

    @Test
    @DisplayName("git URI without a ref defaults to the repository default branch")
    void gitUriWithoutRefParsesToDefaultRef() {
        SkillSource source = SkillSource.parse("git:https://example.com/org/skills.git");

        GitSkillSource git = (GitSkillSource) source;
        assertThat(git.url()).isEqualTo("https://example.com/org/skills.git");
        assertThat(git.ref()).isNull();
    }

    @Test
    @DisplayName("git URI splits on the last @ so URLs containing @ keep their user@host part")
    void gitUriSplitsOnLastAt() {
        SkillSource source = SkillSource.parse("git:https://user@host.example.com/org/skills.git@main");

        GitSkillSource git = (GitSkillSource) source;
        assertThat(git.url()).isEqualTo("https://user@host.example.com/org/skills.git");
        assertThat(git.ref()).isEqualTo("main");
    }

    @Test
    @DisplayName("local-path git URI parses")
    void localPathGitUriParses() {
        SkillSource source = SkillSource.parse("git:/tmp/skills-repo.git@deadbeef");

        GitSkillSource git = (GitSkillSource) source;
        assertThat(git.url()).isEqualTo("/tmp/skills-repo.git");
        assertThat(git.ref()).isEqualTo("deadbeef");
    }

    @Test
    @DisplayName("unknown scheme is rejected with an error naming the URI")
    void unknownSchemeIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SkillSource.parse("svn:https://example.com/repo"))
                .withMessageContaining("svn:https://example.com/repo")
                .withMessageContaining("svn");
    }

    @Test
    @DisplayName("empty URI is rejected")
    void emptyUriIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SkillSource.parse(""))
                .withMessageContaining("empty");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SkillSource.parse("   "));
    }

    @Test
    @DisplayName("URI without a scheme is rejected")
    void schemelessUriIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SkillSource.parse("skill-packs"))
                .withMessageContaining("skill-packs");
    }

    @Test
    @DisplayName("scheme with an empty path is rejected")
    void emptyPathIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SkillSource.parse("folder:"))
                .withMessageContaining("folder:");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SkillSource.parse("git:"));
    }

    @Test
    @DisplayName("git URI with an empty ref after @ is rejected")
    void gitUriWithEmptyRefIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SkillSource.parse("git:/tmp/repo.git@"))
                .withMessageContaining("git:/tmp/repo.git@");
    }
}
