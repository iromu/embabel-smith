/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.embabel.agent.skills.script.DockerSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.PodmanSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.tools.coding.RunRoot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The four boot states of {@code smith.tools.scripts.engine}, and the two ways a value
 * can be wrong about them.
 *
 * <p>
 * <b>What each state is allowed to do.</b> Unset installs the platform's no-op engine,
 * which claims no language, so a pack's scripts stay unoffered — the ordinary run's tool
 * list is what it was. {@code process} installs the platform's own spawned-interpreter
 * engine, confined to the run root. {@code docker} and {@code podman} install the
 * platform's container engines, and each of those two installs only when this host can
 * actually run containers: a choice the host cannot honour fails the boot rather than
 * quietly installing the process engine instead, which is the failure mode the switch
 * exists to make impossible.
 *
 * <p>
 * <b>Why the refusal is assertable here.</b> The container probe is injected, so the
 * refusal is exercised on a host that can run containers — this one, where Docker answers
 * — by a probe that says otherwise. The alternative, a test that skipped unless the
 * machine happened to have no daemon, would assert nothing on most machines and on this
 * one would only prove that a working Docker works.
 *
 * <p>
 * <b>Why the key set is pinned alongside the states.</b> Every state above is reached
 * through a {@code smith.tools.*} key, and a key that reads as configured while binding
 * nowhere looks exactly like a run that was never asked to do anything. So the keys this
 * module reads are pinned by name, and each is exercised: {@code scripts.engine} by the
 * four states, {@code coding.root} by the parity case against the addon's own run-root
 * rule.
 *
 * Runs with no model and no endpoint; no container is started and no script is executed.
 *
 * @author Ivan Rodriguez
 */
class ScriptEngineConfigTest {

	/** The pattern a {@code smith.tools.*} key looks like wherever it is written. */
	private static final Pattern TOOL_KEY = Pattern.compile("smith\\.tools\\.[A-Za-z0-9._-]*[A-Za-z0-9]");

	@TempDir
	Path tmp;

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(ScriptEngineConfig.class);

	@Test
	void nothingSelectedInstallsTheEngineThatClaimsNothing() {
		this.runner.withBean(ScriptEngineConfig.ContainerAvailability.class, () -> containers(false, false))
			.run((context) -> {
				assertThat(context).hasSingleBean(SkillScriptExecutionEngine.class);
				assertThat(context.getBean(SkillScriptExecutionEngine.class)).isSameAs(NoOpExecutionEngine.INSTANCE);
				assertThat(context.getBean(SkillScriptExecutionEngine.class).supportedLanguages())
					.as("the quantity that decides whether a script becomes a tool")
					.isEmpty();
			});
	}

	@Test
	void processIsThePlatformsOwnProcessEngine() {
		choosing(ScriptEngineConfig.PROCESS)
			.withBean(ScriptEngineConfig.ContainerAvailability.class, () -> containers(false, false))
			.run((context) -> assertThat(
					ScriptEngineConfig.underlying(context.getBean(SkillScriptExecutionEngine.class)))
				.as("the boot records its calls, which is a wrapper and not an engine, so the claim is about what is"
						+ " underneath it")
				.isInstanceOf(ProcessSkillScriptExecutionEngine.class));
	}

	@Test
	void dockerIsThePlatformsOwnDockerEngineWhereTheHostCanRunContainers() {
		choosing(ScriptEngineConfig.DOCKER)
			.withBean(ScriptEngineConfig.ContainerAvailability.class, () -> containers(true, false))
			.run((context) -> assertThat(
					ScriptEngineConfig.underlying(context.getBean(SkillScriptExecutionEngine.class)))
				.isInstanceOf(DockerSkillScriptExecutionEngine.class));
	}

	@Test
	void podmanIsThePlatformsOwnPodmanEngineWhereTheHostCanRunContainers() {
		choosing(ScriptEngineConfig.PODMAN)
			.withBean(ScriptEngineConfig.ContainerAvailability.class, () -> containers(false, true))
			.run((context) -> assertThat(
					ScriptEngineConfig.underlying(context.getBean(SkillScriptExecutionEngine.class)))
				.isInstanceOf(PodmanSkillScriptExecutionEngine.class));
	}

	@Test
	void aContainerChoiceTheHostCannotRunRefusesAtStartUpRatherThanFallingBack() {
		AtomicReference<Throwable> startup = new AtomicReference<>();
		choosing(ScriptEngineConfig.DOCKER)
			.withBean(ScriptEngineConfig.ContainerAvailability.class, () -> containers(false, false))
			.run((context) -> startup.set(context.getStartupFailure()));

		assertThat(startup.get()).as("the boot failed rather than installing something else").isNotNull();
		assertThat(messageOf(startup.get())).contains(PackSkills.SCRIPT_ENGINE_PROPERTY, ScriptEngineConfig.DOCKER,
				"falling back");
	}

	@Test
	void aContainerChoiceRefusesBeforeAnyEngineIsConstructed() {
		ScriptEngineConfig.ContainerAvailability nowhere = containers(false, false);
		refuses(ScriptEngineConfig.DOCKER, nowhere);
		refuses(ScriptEngineConfig.PODMAN, nowhere);
		// The control: the same call against a host that can run containers does install
		// the engine, so the refusals above are the probe answering and not an
		// unconditional rejection of containers.
		assertThat(ScriptEngineConfig.choose(ScriptEngineConfig.DOCKER, this.tmp.toString(), containers(true, false)))
			.isInstanceOf(DockerSkillScriptExecutionEngine.class);
	}

	@Test
	void anUnrecognisedValueRefusesRatherThanReadingAsNone() {
		AtomicReference<Throwable> startup = new AtomicReference<>();
		choosing("proccess").withBean(ScriptEngineConfig.ContainerAvailability.class, () -> containers(false, false))
			.run((context) -> startup.set(context.getStartupFailure()));

		assertThat(startup.get()).as("a typo is not the same as never having asked").isNotNull();
		String message = messageOf(startup.get());
		assertThat(message).contains(PackSkills.SCRIPT_ENGINE_PROPERTY, "proccess");
		assertThat(ScriptEngineConfig.CHOICES).as("and it names what the choices actually are")
			.allMatch((choice) -> message.contains(choice));
	}

	@Test
	void theScriptRootIsTheValueTheFileToolsConfineto() {
		String configured = this.tmp.resolve("work").toString();
		Environment rootSet = environmentWith(ScriptEngineConfig.RUN_ROOT_PROPERTY, configured);
		Environment rootUnset = environmentWith(ScriptEngineConfig.RUN_ROOT_PROPERTY, "");
		assertThat(ScriptEngineConfig.scriptRoot(rootSet)).isEqualTo(RunRoot.of(configured).path().toString());
		assertThat(ScriptEngineConfig.scriptRoot(rootUnset)).as("a blank value takes the addon's own default")
			.isEqualTo(RunRoot.of(null).path().toString());
	}

	@Test
	void theToolKeysThisModuleReadsAreExactlyThePinnedOnes() throws IOException {
		Path sources = Path.of("src/main/java");
		assertThat(sources).as("the scan reads this module's own main sources").isDirectory();
		final Set<String> found;
		try (Stream<Path> files = Files.walk(sources)) {
			found = files.filter((file) -> file.toString().endsWith(".java"))
				.map(this::keysIn)
				.flatMap(Set::stream)
				.collect(Collectors.toUnmodifiableSet());
		}
		assertThat(found)
			.as("every key this module reads is one of these, and each of these binds (see the cases above)")
			.containsExactlyInAnyOrder(PackSkills.SCRIPT_ENGINE_PROPERTY, ScriptEngineConfig.RUN_ROOT_PROPERTY);
		assertThat(found)
			.as("nothing here is shaped like an addon group switch, whose boot report would otherwise own it")
			.noneMatch((key) -> key.endsWith(".enabled"));
	}

	/**
	 * A boot with the switch set.
	 * @param value the value the operator wrote
	 * @return the runner, still needing a container probe
	 */
	private ApplicationContextRunner choosing(String value) {
		return this.runner.withPropertyValues(PackSkills.SCRIPT_ENGINE_PROPERTY + "=" + value);
	}

	/**
	 * A container probe that answers what the case needs it to.
	 * @param docker the answer for Docker
	 * @param podman the answer for Podman
	 * @return the probe
	 */
	private static ScriptEngineConfig.ContainerAvailability containers(boolean docker, boolean podman) {
		return new ScriptEngineConfig.ContainerAvailability() {

			@Override
			public boolean dockerAvailable() {
				return docker;
			}

			@Override
			public boolean podmanAvailable() {
				return podman;
			}
		};
	}

	/**
	 * The deepest message a boot failure carries, which is the sentence this module wrote
	 * rather than Spring's wrapper about creating a bean.
	 * @param failure the startup failure
	 * @return the message
	 */
	private static String messageOf(Throwable failure) {
		Throwable deepest = failure;
		while (deepest.getCause() != null && deepest.getCause() != deepest) {
			deepest = deepest.getCause();
		}
		return String.valueOf(deepest.getMessage());
	}

	/**
	 * Selecting a container this host cannot run, straight at the selector.
	 * @param engine the value that asked for the container
	 * @param containers the probe that says it cannot
	 */
	private static void refuses(String engine, ScriptEngineConfig.ContainerAvailability containers) {
		try {
			ScriptEngineConfig.choose(engine, System.getProperty("user.dir"), containers);
			fail("nothing refused " + engine + "; a choice the host cannot run installed an engine anyway");
		}
		catch (IllegalStateException ex) {
			assertThat(ex.getMessage()).contains(PackSkills.SCRIPT_ENGINE_PROPERTY, engine);
		}
	}

	/**
	 * An environment carrying one property.
	 * @param key the key
	 * @param value the value
	 * @return the environment
	 */
	private static Environment environmentWith(String key, String value) {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty(key, value);
		return environment;
	}

	/**
	 * Every {@code smith.tools.*} key one source file mentions.
	 * @param file the file to read
	 * @return the keys
	 */
	private Set<String> keysIn(Path file) {
		try {
			Matcher matcher = TOOL_KEY.matcher(Files.readString(file));
			return matcher.results().map((result) -> result.group()).collect(Collectors.toUnmodifiableSet());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
