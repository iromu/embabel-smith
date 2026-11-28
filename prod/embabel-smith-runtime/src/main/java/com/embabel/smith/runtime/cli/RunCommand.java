/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The {@code smith run} command: runs an exported goal over the agent specs in a source,
 * printing the goal output to stdout on success (exit 0) and a clear error to stderr on
 * failure (exit 1). Usage errors exit 2 (picocli default).
 *
 * @author Ivan Rodriguez
 */
@Command(name = "run", description = "Run an exported goal over the agent specs in a source")
public class RunCommand implements Callable<Integer> {

	@Option(names = "--source", required = true, description = "Spec source URI: folder:<path> or git:<url>[@ref]")
	String source;

	@Option(names = "--goal", description = "Goal to run (defaults to the single exported goal)")
	String goal;

	@Option(names = "--input", description = "Input bound to the goal's UserInput")
	String input;

	@Option(names = "--planner", description = "Planner override (default GOAP)")
	String planner;

	@Option(names = "--trust-tier", converter = CaseInsensitiveTrustTierConverter.class,
			description = "Trust tier: standard (default) or high")
	TrustTier trustTier = TrustTier.STANDARD;

	@Option(names = "--trace-dir", description = "Directory for the JSONL trace (default ~/.smith/traces)")
	Path traceDir;

	private final AgentPlatform platform;

	private final SkillScriptExecutionEngine scriptEngine;

	public RunCommand(AgentPlatform platform) {
		this(platform, null);
	}

	/**
	 * The command over a boot that selected a script engine.
	 * @param platform the booted agent platform
	 * @param scriptEngine the engine {@code smith.tools.scripts.engine} selected at boot,
	 * or {@code null} for none
	 */
	public RunCommand(AgentPlatform platform, SkillScriptExecutionEngine scriptEngine) {
		this.platform = platform;
		this.scriptEngine = scriptEngine;
	}

	/**
	 * Test seam: allows substituting the run service.
	 * @param traceFile the JSONL trace file the run service writes to
	 * @return a new run service over the booted platform
	 */
	protected AgentSpecRunService newService(Path traceFile) {
		return new AgentSpecRunService(this.platform, traceFile, this.scriptEngine);
	}

	@Override
	public Integer call() {
		try {
			Path traceFile = (this.traceDir != null) ? this.traceDir.resolve("runs.jsonl")
					: JsonlTraceStore.defaultPath();
			AgentSpecRunService.RunResult result = newService(traceFile).run(this.source, this.goal, this.input,
					this.planner, this.trustTier);
			// What the ingest noticed goes to stderr, ahead of the result: a run that
			// offered
			// nothing because a skill folder was refused looks like a run that was asked
			// for nothing,
			// and the goal's own output stays the only thing on stdout.
			for (String note : result.notes()) {
				System.err.println("[smith] " + note);
			}
			if (result.success()) {
				System.out.println(result.output());
				return 0;
			}
			System.err.println(result.error());
			return 1;
		}
		catch (Exception ex) {
			System.err.println((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
			return 1;
		}
	}

}
