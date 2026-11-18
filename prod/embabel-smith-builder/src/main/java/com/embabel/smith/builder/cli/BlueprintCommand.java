/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.builder.blueprint.BlueprintGenerator;
import com.embabel.smith.builder.blueprint.BlueprintGenerator.BlueprintResult;
import com.embabel.smith.builder.blueprint.BlueprintGenerator.CodebaseSource;
import com.embabel.smith.builder.blueprint.ProcessExtractor;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The {@code smith blueprint} command: reconstructs the business process a repository
 * implements and writes it as a blueprint — the seven discovery deliverables plus an
 * index — which is the input {@code smith build --docs} reads. It is the one command that
 * ends in documents rather than a published capability, so it runs no eval, no refinement
 * and no publish stage; its gate is simply whether the answer carried every deliverable.
 *
 * <p>
 * One model call, over as much of the repository as a context window allows. That is what
 * makes it the expensive command of the two, and why the corpus bound and the count of
 * files left out are part of the report and of the index the blueprint ships with.
 *
 * @author Ivan Rodriguez
 */
@Command(name = "blueprint", description = "Reconstruct a repository's business process as a blueprint directory")
public class BlueprintCommand implements Callable<Integer> {

	/**
	 * Seven documents come out of this one call, where a generation call emits a single
	 * spec, so the answer budget is wider than the generation default.
	 */
	private static final int MAX_TOKENS = 24_000;

	@ArgGroup(exclusive = true, multiplicity = "1", heading = "Codebase input (exactly one of)%n")
	Input input;

	@Option(names = "--subdir",
			description = "Directory inside the codebase to build the blueprint from, for a monorepo "
					+ "whose corpus would otherwise be mostly other people's code")
	String subdir;

	@Option(names = "--out", required = true, description = "Directory to write the blueprint documents to")
	String out;

	@Option(names = "--overwrite", description = "Replace the documents already in --out")
	boolean overwrite;

	private final Ai ai;

	public BlueprintCommand(Ai ai) {
		this.ai = ai;
	}

	/**
	 * Test seam: allows substituting the extractor that runs the discovery prompt.
	 * @return the extractor, running the bundled discovery prompt on the builder's model
	 * layer with an answer budget wide enough for seven documents
	 */
	protected ProcessExtractor newExtractor() {
		LlmOptions budget = LlmOptions.withDefaults().withTemperature(0.2).withMaxTokens(MAX_TOKENS);
		return new ProcessExtractor(new AiSpecPromptCaller(this.ai, budget));
	}

	/**
	 * Test seam: allows substituting the generator.
	 * @return the generator running this command's extractor
	 */
	protected BlueprintGenerator newGenerator() {
		return new BlueprintGenerator(newExtractor());
	}

	/**
	 * Generates the blueprint and prints its report.
	 * @return the exit code: 0 when every deliverable was written, 1 when the run was
	 * refused, failed, or left deliverables missing
	 */
	@Override
	public Integer call() {
		try {
			BlueprintResult result = newGenerator().generate(
					new CodebaseSource(this.input.repo, this.input.path, this.input.zip, this.subdir),
					Path.of(this.out), this.overwrite);
			System.out.print(result.report());
			return result.complete() ? 0 : 1;
		}
		catch (Exception ex) {
			System.err.println((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
			return 1;
		}
	}

	/**
	 * The mutually exclusive codebase inputs: a repository URL to clone, a local checkout
	 * to read where it sits, or an archive to extract. Exactly one is required, so naming
	 * none of them — or more than one — is a usage error (exit 2).
	 */
	static class Input {

		@Option(names = "--repo", description = "Git URL to clone (shallow) and read")
		String repo;

		@Option(names = "--path", description = "Local directory holding the codebase")
		String path;

		@Option(names = "--zip", description = "Codebase archive (.zip, as downloaded from a forge)")
		String zip;

	}

}
