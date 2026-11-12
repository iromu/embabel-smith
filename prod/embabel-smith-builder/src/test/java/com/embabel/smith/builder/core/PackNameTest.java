package com.embabel.smith.builder.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of {@link PackName#slug}: deterministic (same description → same slug),
 * non-alphanumerics collapse to '-', slugs are truncated to 40 chars, and empty/null fall
 * back to {@code built-spec}.
 */
class PackNameTest {

	@Test
	void sameDescriptionYieldsTheSameSlug() {
		String description = "Explain Coffee Machines to a Beginner";
		assertThat(PackName.slug(description)).isEqualTo(PackName.slug(description));
	}

	@Test
	void nonAlphanumericsBecomeDashes() {
		assertThat(PackName.slug("Hello, World! (v2)")).isEqualTo("hello-world-v2");
	}

	@Test
	void longDescriptionsAreTruncatedToFortyChars() {
		String slug = PackName.slug("a".repeat(50));
		assertThat(slug).hasSize(40);
		assertThat(slug).isEqualTo("a".repeat(40));
	}

	@Test
	void emptyOrNullFallsBackToBuiltSpec() {
		assertThat(PackName.slug("")).isEqualTo("built-spec");
		assertThat(PackName.slug("   ")).isEqualTo("built-spec");
		assertThat(PackName.slug(null)).isEqualTo("built-spec");
	}

}
