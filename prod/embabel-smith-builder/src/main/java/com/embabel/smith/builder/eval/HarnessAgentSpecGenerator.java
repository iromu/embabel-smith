/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.nio.file.Path;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.core.BuilderPackPaths;

/**
 * The harness flow's generator: the builder's own {@code specs/builder-harness} pack
 * issues the drafting call, over three steps that decide for themselves what of the
 * build's documents to read.
 *
 * <p>
 * <b>What makes this a harness and not the agentic flow again.</b> The agentic pack's two
 * steps work on a corpus the driver put in front of them, so the only reading decision
 * left to a step is whether to look up something it already has. This pack's first step
 * is given the roots and asked what is there ({@code list_docs}), its second is given
 * what the first did not read and asked what to read next, and its third drafts from the
 * plan those two produced — reading whatever it still needs through {@code read_doc}. The
 * choice of <em>which document, in what order</em> is the step's; the choice of <em>which
 * roots are open at all</em> stays the driver's, exactly as on every other flow
 * ({@code tools/ToolSurface}, and the reason a step cannot name a path it guessed).
 *
 * <p>
 * <b>Why the driver is this class and not a new one.</b> Everything a harness has to
 * guarantee about a draft is already implemented once in
 * {@link AgenticAgentSpecGenerator}: the checks run whether or not the step called the
 * tool (a step that was told to validate its own draft measurably declines), the findings
 * come back appended to the same request rather than as a new task, the loop has a
 * ceiling and hands on the last draft with its findings recorded instead of publishing
 * it, and a pack that declares a role the platform cannot resolve is refused before a
 * call is spent. A second copy of that would be a second place for the ceiling to drift.
 * What differs is the pack and the name in the refusals, which is what the constructor
 * passes.
 *
 * <p>
 * <b>The memo is the driver's, not the step's.</b> {@code cli/BuildCommand} calls
 * {@code ToolSurface.startRun()} when it wires this generator, so a step that re-asks for
 * a document it already read gets the answer it already got (and says so), while a second
 * build in the same process cannot be answered from the first build's files. That is what
 * keeps a reading loop from costing a read per repetition.
 *
 * @author Ivan Rodriguez
 */
public class HarnessAgentSpecGenerator extends AgenticAgentSpecGenerator {

	/**
	 * The shipped harness pack, addressed as
	 * {@link AgenticAgentSpecGenerator#builderPack()} addresses its own — through the
	 * class loader, at the {@code specs/} resource directory of this module. The pack
	 * travels in the jar with the code that drives it, which is what removes the working
	 * directory from the picture: a build launched from the repository root loads the
	 * same pack as one launched inside the module. This one hides the parent's rather
	 * than sharing it, which is safe because the shared driver never asks for a pack by
	 * name — it reads the directory its constructor was handed.
	 * @return the directory of the pack this flow drives
	 */
	public static Path builderPack() {
		return BuilderPackPaths.directory("builder-harness");
	}

	/**
	 * Drives the shipped harness pack over the given platform.
	 * @param invokers the factory that deploys the harness pack and returns its goal
	 * invoker
	 * @param platform the platform whose resolvable roles the pack's three steps are
	 * checked against before the deploy
	 */
	public HarnessAgentSpecGenerator(InvokerFactory invokers, AgentPlatform platform) {
		super(invokers, platform, builderPack(), "harness");
	}

}
