/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The index entry keeps its input types as a defensive copy: a caller that hands over
 * {@code null} reads back an empty list rather than a null it has to guard, and a caller
 * that hands over a mutable list cannot mutate the entry afterwards.
 */
class SpecEntryTest {

	@Test
	void nullInputTypesReadBackAsAnEmptyList() {
		SpecEntry entry = new SpecEntry("pack", "pack", "does a thing", null, "Report");

		assertThat(entry.inputTypes()).isNotNull().isEmpty();
	}

	@Test
	void inputTypesAreCopiedSoTheCallerCannotMutateThem() {
		List<String> given = new ArrayList<>();
		given.add("UserInput");
		SpecEntry entry = new SpecEntry("pack", "pack", "does a thing", given, "Report");

		given.add("Mutated");

		assertThat(entry.inputTypes()).containsExactly("UserInput");
	}

}
