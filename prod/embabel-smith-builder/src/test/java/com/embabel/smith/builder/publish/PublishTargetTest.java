package com.embabel.smith.builder.publish;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests of {@link PublishTarget#parse}: the {@code folder:} and {@code git:} URI grammar,
 * the optional git ref (split on the LAST {@code @}), and that an unknown/blank scheme is rejected.
 */
class PublishTargetTest {

    @Test
    void parsesFolderSource() {
        PublishTarget target = PublishTarget.parse("folder:/tmp/built-specs");
        assertThat(target.type()).isEqualTo(PublishTarget.Type.FOLDER);
        assertThat(target.location()).isEqualTo("/tmp/built-specs");
        assertThat(target.ref()).isNull();
    }

    @Test
    void parsesGitSourceWithoutRef() {
        PublishTarget target = PublishTarget.parse("git:https://example.com/repo.git");
        assertThat(target.type()).isEqualTo(PublishTarget.Type.GIT);
        assertThat(target.location()).isEqualTo("https://example.com/repo.git");
        assertThat(target.ref()).isNull();
    }

    @Test
    void parsesGitSourceWithRefSplitOnLastAt() {
        // A git URL may itself contain '@' (e.g. an scp-style host) — the ref is the part after the LAST '@'.
        PublishTarget target = PublishTarget.parse("git:git@github.com:org/repo.git@feature-branch");
        assertThat(target.type()).isEqualTo(PublishTarget.Type.GIT);
        assertThat(target.location()).isEqualTo("git@github.com:org/repo.git");
        assertThat(target.ref()).isEqualTo("feature-branch");
    }

    @Test
    void rejectsUnknownScheme() {
        assertThatThrownBy(() -> PublishTarget.parse("svn:https://example.com/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown source URI scheme");
    }

    @Test
    void rejectsBlankUri() {
        assertThatThrownBy(() -> PublishTarget.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublishTarget.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
