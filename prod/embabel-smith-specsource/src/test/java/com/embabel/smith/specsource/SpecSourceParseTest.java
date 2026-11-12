package com.embabel.smith.specsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for the source-URI grammar: {@code folder:<path>} and {@code git:<url>[@<ref>]}.
 */
class SpecSourceParseTest {

    @Test
    @DisplayName("folder URI parses to a FolderSpecSource rooted at the path")
    void folderUriParses() {
        SpecSource source = SpecSource.parse("folder:./agent-specs");

        assertThat(source).isInstanceOf(FolderSpecSource.class);
        assertThat(((FolderSpecSource) source).root()).isEqualTo(Path.of("./agent-specs"));
    }

    @Test
    @DisplayName("absolute folder URI parses")
    void absoluteFolderUriParses() {
        SpecSource source = SpecSource.parse("folder:/tmp/packs");

        assertThat(source).isInstanceOf(FolderSpecSource.class);
        assertThat(((FolderSpecSource) source).root()).isEqualTo(Path.of("/tmp/packs"));
    }

    @Test
    @DisplayName("git URI with a ref parses to a pinned GitSpecSource")
    void gitUriWithRefParses() {
        SpecSource source = SpecSource.parse("git:https://example.com/org/specs.git@v1.2");

        GitSpecSource git = (GitSpecSource) source;
        assertThat(git.url()).isEqualTo("https://example.com/org/specs.git");
        assertThat(git.ref()).isEqualTo("v1.2");
    }

    @Test
    @DisplayName("git URI without a ref defaults to the repository default branch")
    void gitUriWithoutRefParsesToDefaultRef() {
        SpecSource source = SpecSource.parse("git:https://example.com/org/specs.git");

        GitSpecSource git = (GitSpecSource) source;
        assertThat(git.url()).isEqualTo("https://example.com/org/specs.git");
        assertThat(git.ref()).isNull();
    }

    @Test
    @DisplayName("git URI splits on the last @ so URLs containing @ keep their user@host part")
    void gitUriSplitsOnLastAt() {
        SpecSource source = SpecSource.parse("git:https://user@host.example.com/org/specs.git@main");

        GitSpecSource git = (GitSpecSource) source;
        assertThat(git.url()).isEqualTo("https://user@host.example.com/org/specs.git");
        assertThat(git.ref()).isEqualTo("main");
    }

    @Test
    @DisplayName("local-path git URI parses")
    void localPathGitUriParses() {
        SpecSource source = SpecSource.parse("git:/tmp/specs-repo.git@deadbeef");

        GitSpecSource git = (GitSpecSource) source;
        assertThat(git.url()).isEqualTo("/tmp/specs-repo.git");
        assertThat(git.ref()).isEqualTo("deadbeef");
    }

    @Test
    @DisplayName("unknown scheme is rejected with an error naming the URI")
    void unknownSchemeIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecSource.parse("svn:https://example.com/repo"))
                .withMessageContaining("svn:https://example.com/repo")
                .withMessageContaining("svn");
    }

    @Test
    @DisplayName("empty URI is rejected")
    void emptyUriIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecSource.parse(""))
                .withMessageContaining("empty");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecSource.parse("   "));
    }

    @Test
    @DisplayName("URI without a scheme is rejected")
    void schemelessUriIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecSource.parse("agent-specs"))
                .withMessageContaining("agent-specs");
    }

    @Test
    @DisplayName("scheme with an empty path is rejected")
    void emptyPathIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecSource.parse("folder:"))
                .withMessageContaining("folder:");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecSource.parse("git:"));
    }

    @Test
    @DisplayName("git URI with an empty ref after @ is rejected")
    void gitUriWithEmptyRefIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecSource.parse("git:/tmp/repo.git@"))
                .withMessageContaining("git:/tmp/repo.git@");
    }
}
