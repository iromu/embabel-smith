package com.embabel.smith.runtime.cli;

import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.GoalNotFoundException;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.TraceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunCommandTest {

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	private final PrintStream originalOut = System.out;

	private final PrintStream originalErr = System.err;

	private AgentSpecRunService.RunResult nextResult;

	private RuntimeException nextException;

	private Path seenTraceFile;

	private String seenSource;

	private String seenGoal;

	private String seenInput;

	private String seenPlanner;

	private TrustTier seenTier;

	@BeforeEach
	void captureStdStreams() {
		System.setOut(new PrintStream(out, true));
		System.setErr(new PrintStream(err, true));
	}

	@AfterEach
	void restoreStdStreams() {
		System.setOut(originalOut);
		System.setErr(originalErr);
	}

	private RunCommand stubCommand(AgentSpecRunService.RunResult result, RuntimeException exception) {
		nextResult = result;
		nextException = exception;
		return new RunCommand(null) {
			@Override
			protected AgentSpecRunService newService(Path traceFile) {
				seenTraceFile = traceFile;
				return new AgentSpecRunService(null) {
					@Override
					public RunResult run(String sourceUri, String goal, String input, String planner, TrustTier tier) {
						seenSource = sourceUri;
						seenGoal = goal;
						seenInput = input;
						seenPlanner = planner;
						seenTier = tier;
						if (nextException != null) {
							throw nextException;
						}
						return nextResult;
					}
				};
			}
		};
	}

	private static AgentSpecRunService.RunResult result(boolean success, String output, String error) {
		return new AgentSpecRunService.RunResult(success, output, error, new TraceRecord("id", Instant.now(), null,
				null, "g", "i", "GOAP", "standard", List.of(), success, error, output));
	}

	@Test
	void aSuccessfulRunPrintsTheGoalOutputAndExitsZero(@TempDir Path tmp) {
		int exit = new CommandLine(stubCommand(result(true, "the goal output", null), null)).execute("--source",
				"folder:" + tmp, "--goal", "g", "--input", "in", "--planner", "GOAP", "--trust-tier", "high",
				"--trace-dir", tmp.toString());

		assertThat(exit).isZero();
		assertThat(out.toString()).contains("the goal output");
		assertThat(seenSource).isEqualTo("folder:" + tmp);
		assertThat(seenGoal).isEqualTo("g");
		assertThat(seenInput).isEqualTo("in");
		assertThat(seenPlanner).isEqualTo("GOAP");
		assertThat(seenTier).isEqualTo(TrustTier.HIGH);
		assertThat(seenTraceFile).isEqualTo(tmp.resolve("runs.jsonl"));
	}

	@Test
	void aFailedRunPrintsTheErrorToStderrAndExitsNonZero(@TempDir Path tmp) {
		int exit = new CommandLine(stubCommand(result(false, null, "goal not achieved (status=STUCK)"), null))
			.execute("--source", "folder:" + tmp);

		assertThat(exit).isEqualTo(1);
		assertThat(err.toString()).contains("goal not achieved");
		assertThat(out.toString()).isEmpty();
	}

	@Test
	void aMissingGoalErrorListsTheAvailableGoals(@TempDir Path tmp) {
		int exit = new CommandLine(
				stubCommand(null, new GoalNotFoundException("nope", List.of("briefDone", "explainDone"))))
			.execute("--source", "folder:" + tmp, "--goal", "nope");

		assertThat(exit).isEqualTo(1);
		assertThat(err.toString()).contains("briefDone").contains("explainDone");
	}

	@Test
	void missingRequiredSourceIsAUsageError(@TempDir Path tmp) {
		int exit = new CommandLine(stubCommand(result(true, "x", null), null)).execute("--goal", "g");

		assertThat(exit).isEqualTo(2);
	}

	@Test
	void anUnknownTrustTierIsAUsageError(@TempDir Path tmp) {
		int exit = new CommandLine(stubCommand(result(true, "x", null), null)).execute("--source", "folder:" + tmp,
				"--trust-tier", "bogus");

		assertThat(exit).isEqualTo(2);
	}

	@Test
	void theTrustTierDefaultsToStandard(@TempDir Path tmp) {
		new CommandLine(stubCommand(result(true, "x", null), null)).execute("--source", "folder:" + tmp);

		assertThat(seenTier).isEqualTo(TrustTier.STANDARD);
	}

}
