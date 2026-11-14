/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.skillscripts;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.api.common.scope.AgentScopeBuilder;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import org.graalvm.polyglot.Engine;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 26 launcher: prints the evidence that an Embabel agent can run an Agent Skill's
 * JavaScript inside its own JVM, then — only when a model endpoint is configured — lets the
 * model call that script as a tool.
 *
 * <p>
 * Sections 1–5 are offline and print <em>before</em> Spring is started, because Embabel's
 * model configuration fails to instantiate without an endpoint and none of that evidence needs
 * a model. Spring is only entered for section 6, which is what the model is for. So the tables
 * the README quotes reproduce with no API key:
 *
 * <pre>
 *   mvn -pl poc/poc-26-graaljs-skill-scripts spring-boot:run
 * </pre>
 *
 * With an endpoint as well (see {@code ~/.config/env/llm.env}), the last section shows the
 * model reaching the script through Embabel's tool loop:
 *
 * <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   mvn -pl poc/poc-26-graaljs-skill-scripts spring-boot:run
 * </pre>
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class SkillScriptsApp implements CommandLineRunner {

	/**
	 * Invoice used by both the deterministic run and the model demo, so the two are comparable.
	 */
	static final String INVOICE = """
			{
			  "number": "INV-2026-014",
			  "currency": "EUR",
			  "ledgerAccount": "91827364550123456789",
			  "lines": [
			    { "description": "Cold-chain pallet", "category": "goods", "qty": 12, "unitPrice": 84.99 },
			    { "description": "Refrigerated haulage", "category": "freight", "qty": 1, "unitPrice": 240.00 }
			  ]
			}
			""";

	private final AgentPlatform platform;

	private final SkillScriptAgent agent;

	public SkillScriptsApp(AgentPlatform platform, SkillScriptAgent agent) {
		this.platform = platform;
		this.agent = agent;
	}

	public static void main(String[] args) {
		GraalJsSkillScriptExecutionEngine engine = new GraalJsSkillScriptExecutionEngine();
		SkillScriptRuntime runtime = new SkillScriptRuntime(engine);
		Path skillsDir = Path.of(System.getProperty("smith.skill-scripts.dir", "src/main/resources/skills"));
		Skills skills = runtime.skillsFrom(skillsDir);

		environment();
		toolSurface(runtime, skillsDir);
		deterministicRun(runtime, skills);
		conformance(engine);
		engineComparison(engine);

		if (endpointConfigured()) {
			SpringApplication.run(SkillScriptsApp.class, args);
		}
		else {
			section("6. The model calling the script as a tool");
			System.out.println("Skipped: no model endpoint configured. Set OPENAI_CUSTOM_BASE_URL, "
					+ "OPENAI_CUSTOM_API_KEY and EMBABEL_MODELS_DEFAULT_LLM to run this section. "
					+ "The same tool call is proven offline in SkillScriptRuntimeTest.");
		}
	}

	@Override
	public void run(String... args) {
		section("6. The model calling the script as a tool");
		System.out.println("Endpoint: " + System.getenv("OPENAI_CUSTOM_BASE_URL") + " / "
				+ System.getenv("EMBABEL_MODELS_DEFAULT_LLM"));
		AgentScope scope = AgentScopeBuilder.fromInstance(this.agent).createAgentScope();
		Agent agent = scope.createAgent("invoice-scripted", "skill-script-agent",
				"Answers invoice questions by running the skill's JavaScript");
		this.platform.deploy(agent);
		String request = "Total this invoice and tell me what I owe: " + INVOICE;
		AgentProcess process = this.platform.runAgentFrom(agent, ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
				Map.of("userInput", new UserInput(request)));
		if (process.lastResult() instanceof SkillScriptAgent.ScriptedAnswer answer) {
			System.out.println(answer.content());
		}
		else {
			System.out.println("No answer produced; status=" + process.getStatus());
		}
	}

	private static void environment() {
		section("1. Environment — no GraalVM, no Node required");
		System.out.println("JVM            : " + System.getProperty("java.version") + " ("
				+ System.getProperty("java.vendor") + ", " + System.getProperty("java.vm.name") + ")");
		System.out.println("GraalJS        : " + reportEngineVersion() + " — embedded as a library on the classpath");
		System.out.println("node on PATH   : " + (ScriptEngineComparison.nodeAvailable() ? "yes" : "no")
				+ " — the in-JVM engine does not look for it");
		System.out.println("JIT via Graal  : " + (graalCompilerPresent()
				? "the Graal compiler is on the module path, so the JavaScript is JIT-compiled"
				: "no Graal compiler, so the JavaScript runs on the interpreter — the "
						+ "--upgrade-module-path step in RunOnJDK.md is optional and deliberately skipped here"));
	}

	private static void toolSurface(SkillScriptRuntime runtime, Path skillsDir) {
		section("2. What Embabel offers with and without a script engine");
		SkillScriptRuntime without = new SkillScriptRuntime(NoOpExecutionEngine.INSTANCE);
		List<String> defaultEngine = without.toolNames(without.skillsFrom(skillsDir));
		List<String> withEngine = runtime.toolNames(runtime.skillsFrom(skillsDir));
		System.out.println("Embabel's default engine (NoOpExecutionEngine) : " + defaultEngine);
		System.out.println("with the GraalJS engine installed              : " + withEngine);
		System.out.println("Embabel logs \"script execution is not yet supported\" in the first case; "
				+ "the script tool exists only in the second.");
	}

	private static void deterministicRun(SkillScriptRuntime runtime, Skills skills) {
		section("3. The skill's script, executed in this JVM");
		ScriptExecutionResult result = runtime.runScript(skills, "invoice-totalling", "compute_totals.js", List.of(),
				INVOICE, List.of());
		if (result instanceof ScriptExecutionResult.Success success) {
			System.out.println("exit code " + success.getExitCode() + ", stdout:");
			System.out.println(success.getStdout().strip());
		}
		else {
			System.out.println("the script did not run: " + describe(result));
			return;
		}

		ScriptExecutionResult rejected = runtime.runScript(skills, "invoice-totalling", "compute_totals.js",
				List.of("--discount=20"), INVOICE, List.of());
		System.out.println("a discount over 15% without approval -> " + describe(rejected));
	}

	private static void conformance(GraalJsSkillScriptExecutionEngine engine) {
		section("4. ECMAScript 2026 surface the scripts get");
		List<Es2026Conformance.Row> rows = new Es2026Conformance(engine, workDir()).run();
		System.out.println(Es2026Conformance.toMarkdown(rows));
		long passed = rows.stream().filter(Es2026Conformance.Row::passed).count();
		System.out.println(passed + "/" + rows.size() + " probes executed successfully.");
	}

	private static void engineComparison(GraalJsSkillScriptExecutionEngine engine) {
		section("5. In-JVM against Embabel's shipped external-interpreter engine");
		System.out.println(
				ScriptEngineComparison.toMarkdown(new ScriptEngineComparison(engine, workDir()).compare()));
	}

	private static boolean endpointConfigured() {
		return isSet("OPENAI_CUSTOM_BASE_URL") && isSet("OPENAI_CUSTOM_API_KEY") && isSet("EMBABEL_MODELS_DEFAULT_LLM");
	}

	private static boolean isSet(String name) {
		String value = System.getenv(name);
		return value != null && !value.isBlank();
	}

	private static Path workDir() {
		return Path.of(System.getProperty("java.io.tmpdir"));
	}

	private static String reportEngineVersion() {
		try (Engine engine = Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build()) {
			return engine.getVersion();
		}
	}

	private static boolean graalCompilerPresent() {
		if (ModuleLayer.boot().findModule("jdk.internal.vm.compiler").isPresent()) {
			return true;
		}
		try {
			Class.forName("org.graalvm.compiler.hotspot.GraalHotSpotVMInfo", false, ClassLoader.getPlatformClassLoader());
			return true;
		}
		catch (Throwable ex) {
			return false;
		}
	}

	private static String describe(ScriptExecutionResult result) {
		if (result instanceof ScriptExecutionResult.Success success) {
			return (success.getExitCode() != 0) ? "exit " + success.getExitCode() + ", stderr: "
					+ success.getStderr().strip() : "exit 0";
		}
		if (result instanceof ScriptExecutionResult.Failure failure) {
			return failure.getError();
		}
		if (result instanceof ScriptExecutionResult.Denied denied) {
			return "denied — " + denied.getReason();
		}
		return result.toString();
	}

	private static void section(String title) {
		System.out.println();
		System.out.println("== " + title + " " + "=".repeat(Math.max(0, 76 - title.length())));
	}

}
