/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The seed this POC generates from: embabel-smith's own build process, described in its
 * own words.
 *
 * <p>
 * The file is {@code src/main/resources/self/self_process_description.md}, written by
 * hand from {@code docs/05-architecture.md}, {@code docs/08-builder.md} and
 * {@code docs/10-self-improvement.md}, and it names the eight pipeline stages in the
 * order {@code core/BuilderService.build()} holds them. It is the build input a model is
 * asked to decompose, which is why the stage names in {@link BuilderPipelineStages} are
 * its names rather than an invention of this class.
 *
 * <p>
 * {@code evidence/MUTABILITY.md} fixes the self-description as a fixed artifact: it is
 * the seed, so a generation may read it and may not write it, anywhere, under any name.
 * This class only ever reads it.
 *
 * @author Ivan Rodriguez
 */
public final class SelfDescription {

	/** Classpath location of the checked-in self-description. */
	public static final String RESOURCE = "/self/self_process_description.md";

	private SelfDescription() {
	}

	/**
	 * The self-description text, as checked in.
	 * @return the description the process generation is given
	 * @throws IllegalStateException if the resource is not on the classpath, which means
	 * it was renamed or not packaged rather than that the model is unavailable
	 */
	public static String text() {
		try (InputStream resource = SelfDescription.class.getResourceAsStream(RESOURCE)) {
			if (resource == null) {
				throw new IllegalStateException("No self-description on the classpath at " + RESOURCE
						+ ". It is the fixed seed named in evidence/MUTABILITY.md, so a run without it has no"
						+ " input to generate from.");
			}
			return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read the self-description at " + RESOURCE, ex);
		}
	}

}
