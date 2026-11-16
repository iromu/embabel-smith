/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A named directory of files to publish: the agent-spec pack or an Agent Skill folder.
 * The publisher is content-agnostic — it gates on a name collision, replaces the
 * directory on {@code --overwrite} and (for git) commits without pushing — so both
 * artifacts share the collision and no-push semantics instead of each carrying its own
 * publisher.
 *
 * @author Ivan Rodriguez
 */
public interface Publishable {

	/**
	 * The directory name the artifact is published under, which is also the name the
	 * collision gate compares against.
	 * @return the artifact name.
	 */
	String name();

	/**
	 * The eval pass-rate recorded at baseline, quoted in the git commit message.
	 * @return the baseline pass rate.
	 */
	double baselinePassRate();

	/**
	 * Writes the artifact's files into the (already-created) directory.
	 * @param directory the directory to write into.
	 * @throws IOException when a file cannot be written.
	 */
	void writeTo(Path directory) throws IOException;

}
