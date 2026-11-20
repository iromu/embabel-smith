/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.embabel.smith.skillscripts.GraalJsSkillScriptExecutionEngine;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 27 launcher: prints the frozen evidence this module scores against and the script
 * engine the generated policies will run on, then — only when a model endpoint is
 * configured — starts Spring so the paid generations have a process to run in.
 *
 * <p>
 * Sections 1–4 are offline and print <em>before</em> Spring is started, following the
 * sibling launchers: the evidence is checked-in data and a resolved class, none of it
 * needs a model, and Embabel's model configuration fails to instantiate without an
 * endpoint. The frozen benchmark, the labelled corpus and the mutability declaration are
 * read from disk rather than restated here, so the launcher cannot drift from the
 * evidence it quotes.
 *
 * <pre>
 *   mvn -Ppoc -pl poc/poc-27-recursive-self-improvement spring-boot:run
 * </pre>
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class SelfImproveApp implements CommandLineRunner {

	/**
	 * The directory holding the frozen evidence, overridable the way POC 26 overrides its
	 * skill folder, so the launcher can run from outside the module directory.
	 */
	private static final Path RESOURCES = Path.of(System.getProperty("smith.self-improve.dir", "src/main/resources"));

	/**
	 * Prints the frozen evidence and the resolved script engine offline, then starts
	 * Spring only when a model endpoint is configured.
	 * @param args the command-line arguments
	 */
	public static void main(String[] args) {
		engine();
		benchmark();
		corpus();
		mutability();

		if (endpointConfigured()) {
			SpringApplication.run(SelfImproveApp.class, args);
		}
		else {
			skippedOuterLoop();
		}
	}

	/**
	 * Runs after Spring has started. The generations that would run here are the referee,
	 * the outer loop and the archive, which are later tasks of this change; until they
	 * exist there is nothing to spend a model call on.
	 * @param args the command-line arguments
	 */
	@Override
	public void run(String... args) {
		section("5. The paid outer loop");
		System.out.println("Spring started against " + System.getenv("OPENAI_CUSTOM_BASE_URL") + " / "
				+ System.getenv("EMBABEL_MODELS_DEFAULT_LLM"));
		System.out.println("No generation runs yet: the referee and the outer loop are later tasks of this change.");
	}

	private static void engine() {
		section("1. The script engine the generated policies run on (POC 26)");
		GraalJsSkillScriptExecutionEngine engine = new GraalJsSkillScriptExecutionEngine();
		var location = engine.getClass().getProtectionDomain().getCodeSource().getLocation();
		System.out.println("class            : " + engine.getClass().getName());
		System.out.println("resolved from    : " + location);
		System.out.println("languages        : " + engine.supportedLanguages());
		System.out.println("limits           : " + GraalJsSkillScriptExecutionEngine.DEFAULT_TIMEOUT_MILLIS
				+ " ms wall clock, " + GraalJsSkillScriptExecutionEngine.DEFAULT_STATEMENT_LIMIT + " statements, "
				+ GraalJsSkillScriptExecutionEngine.DEFAULT_MAX_OUTPUT_CHARS + " output chars, "
				+ GraalJsSkillScriptExecutionEngine.DEFAULT_MAX_SCRIPT_BYTES + " script bytes");
		System.out.println("This engine is the boundary the mutable set is allowed to reach: a generated policy is"
				+ " guest code under these limits, never code this process compiles.");
	}

	private static void benchmark() {
		section("2. The frozen task set (fixed for the run; never written by the loop)");
		Path tasks = RESOURCES.resolve("benchmark/tasks");
		List<Path> files = list(tasks, ".json");
		System.out.println(tasks + " — " + files.size() + " task(s)");
		files.forEach((file) -> System.out.println("  " + file.getFileName() + " (" + lines(file) + " lines)"));
		System.out.println("Each file declares its own deterministic expectation; the format is documented in"
				+ " benchmark/tasks/README.md.");
	}

	private static void corpus() {
		section("3. The labelled trace corpus (fixed for the run; tuning and held-out splits)");
		Path traces = RESOURCES.resolve("corpus/traces");
		List<Path> files = list(traces, ".jsonl");
		long records = files.stream().mapToLong(SelfImproveApp::lines).sum();
		files.forEach(
				(file) -> System.out.println("  " + file.getFileName() + " — " + lines(file) + " labelled record(s)"));
		System.out.println(traces + " — " + files.size() + " file(s), " + records + " labelled record(s) in total");
		System.out.println("The split is per file and run ids carry the split prefix, so the two splits cannot"
				+ " share a record.");
	}

	private static void mutability() {
		section("4. The mutable/fixed declaration");
		Path declaration = RESOURCES.resolve("evidence/MUTABILITY.md");
		if (Files.isRegularFile(declaration)) {
			System.out.println(declaration + " — " + lines(declaration) + " lines; the single source for what a"
					+ " generation may change and what it may not.");
		}
		else {
			System.out.println(declaration + " — missing; a run may not start without it");
		}
	}

	private static void skippedOuterLoop() {
		section("5. The paid outer loop");
		System.out.println("Skipped: no model endpoint configured. Set OPENAI_CUSTOM_BASE_URL, "
				+ "OPENAI_CUSTOM_API_KEY and EMBABEL_MODELS_DEFAULT_LLM to start Spring for the generations.");
		System.out.println("Sections 1-4 need none of that, and the inner loop is offline by design.");
	}

	private static List<Path> list(Path dir, String suffix) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (var paths = Files.list(dir)) {
			return paths.filter(Files::isRegularFile)
				.filter((path) -> path.getFileName().toString().endsWith(suffix))
				.sorted()
				.collect(Collectors.toList());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot list " + dir, ex);
		}
	}

	private static long lines(Path file) {
		try (var lines = Files.lines(file)) {
			return lines.filter((line) -> !line.isBlank()).count();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read " + file, ex);
		}
	}

	private static boolean endpointConfigured() {
		return isSet("OPENAI_CUSTOM_BASE_URL") && isSet("OPENAI_CUSTOM_API_KEY") && isSet("EMBABEL_MODELS_DEFAULT_LLM");
	}

	private static boolean isSet(String name) {
		String value = System.getenv(name);
		return value != null && !value.isBlank();
	}

	private static void section(String title) {
		System.out.println();
		System.out.println("== " + title + " " + "=".repeat(Math.max(0, 76 - title.length())));
	}

}
