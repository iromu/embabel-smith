/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where a generated script is allowed to land inside the published folder. The name comes
 * from a model, so the rule that keeps it out of the rest of the filesystem lives on the
 * type itself rather than in each caller that writes one.
 *
 * @author Ivan Rodriguez
 */
class SkillScriptTest {

	@Test
	void anyDeclaredPathLandsInTheScriptsFolderUnderItsBareName() {
		// Four ways a model can name the same script; all four are the same published
		// file,
		// and none of them is a file anywhere else.
		assertThat(SkillScript.of("scripts/totals.js", "c").path()).isEqualTo("scripts/totals.js");
		assertThat(SkillScript.of("totals.js", "c").path()).isEqualTo("scripts/totals.js");
		assertThat(SkillScript.of("./build/totals.js", "c").path()).isEqualTo("scripts/totals.js");
		assertThat(SkillScript.of("..\\outside\\totals.js", "c").path()).isEqualTo("scripts/totals.js");
	}

	@Test
	void aNameThatIsNotABareJavaScriptFileIsRefusedRatherThanRelocated() {
		// Relocating these would be the silent option, and it would let a body that names
		// a
		// helper publish a folder with a different file in it.
		assertThatThrownBy(() -> SkillScript.of("notes.txt", "c")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("must be a .js file name");
		assertThatThrownBy(() -> SkillScript.of("totals.JSX", "c")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SkillScript.of("scripts/", "c")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SkillScript.of(null, "c")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nullContentIsTheEmptyScriptRatherThanTheStringNull() {
		// The generator skips a blank script before constructing one, so this is the rule
		// for
		// anything that builds a script directly: an absent body publishes as an empty
		// file,
		// not as the four-character program "null" that a JSON node's own text would
		// give.
		assertThat(new SkillScript("scripts/totals.js", null).content()).isEmpty();
	}

	@Test
	void aScriptCannotBeConstructedAtAPathOutsideItsFolder() {
		// The write path takes the path as given, so the invariant is enforced here: the
		// canonical constructor rejects the escapes a resolved Path would otherwise walk.
		assertThatThrownBy(() -> new SkillScript("../outside.js", "c")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SkillScript("totals.js", "c")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SkillScript("scripts/sub/totals.js", "c"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
