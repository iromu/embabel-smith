/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.embabel.agent.skills.script.DockerSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.PodmanSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The one decision about whether a pack's skill scripts can run: what
 * {@code smith.tools.scripts.engine} selects, turned into the platform's own engine.
 *
 * <p>
 * <b>Why a decision is needed at all.</b> Embabel offers a skill's script as a tool only
 * when an installed engine claims the script's language, and its default engine claims
 * nothing. So installing an engine <em>adds</em> callable tools to a run — a script that
 * was inert text becomes something the model can invoke. That is a change to what a run
 * can reach, and the rule for that here is that it is somebody's explicit decision rather
 * than a side effect of the skills jar landing on the classpath. Unset means
 * {@value #NONE}, which is Embabel's own default behaviour, so the ordinary run's tool
 * list is what it was before this class existed.
 *
 * <p>
 * <b>The value names a substrate, never a class.</b> {@code process}, {@code docker} and
 * {@code podman} are mapped here to the three engines {@code embabel-agent-skills} ships.
 * Nothing reads a class name from configuration, which is what keeps the choice
 * reviewable: an operator cannot point a run at an engine nobody audited, and a value
 * that is not one of the four is refused at start-up rather than read as {@value #NONE}.
 *
 * <p>
 * <b>Why the engine is published under Embabel's own type.</b> The bean is a
 * {@link SkillScriptExecutionEngine}, as
 * {@code builder.skill.script.SkillScriptEngineConfiguration} publishes its GraalJS one,
 * so it is attached through the framework's seam —
 * {@code Skills.withScriptExecutionEngine(engine)} — and nothing in this repo depends on
 * a class this repo picked.
 *
 * <p>
 * <b>Why the switch is read rather than matched.</b> This reads the property from the
 * {@link Environment} inside one {@code @Bean} method instead of gating four beans with
 * {@code @ConditionalOnProperty}. A native image decides conditional beans at build time,
 * so a condition-gated switch would be frozen at whatever value the build saw and would
 * stop being a deployment-time switch — which is the entire point of it.
 *
 * <p>
 * <b>Why a container choice refuses instead of falling back.</b> The isolation is the
 * reason the choice was made. A run that quietly ran its scripts on the host after being
 * told they would run in a container would report the promise as kept. So the choice
 * fails at start-up, naming the property and the value, and it does so here rather than
 * at the first script call — see {@link ContainerAvailability} for why the probe is ours
 * to make.
 *
 * <p>
 * <b>Why the script root is the run root.</b> D8's rule is one root per run, so the
 * process engine is constructed through {@code confinedTo(root)} with the root the file
 * tools confine to — the value of {@value #RUN_ROOT_PROPERTY} — rather than a second
 * location a pack could escape by knowing the difference.
 *
 * <p>
 * <b>Why that is a path confinement and not an environment one.</b> {@code confinedTo}
 * sets the {@code FileTools} argument and nothing else, and the platform's own
 * environment switch — construct the engine with inheritance off and it keeps only its
 * allowlist, a dozen or so entries like {@code PATH} — is a constructor parameter that no
 * Java-visible form of this engine carries: every multi-argument constructor is marked
 * synthetic, because the leading {@code Duration} is a Kotlin value class, which is the
 * same reason the factory exists. So a script run by the {@value #PROCESS} engine sees
 * the environment the JVM was started with. That is stated on the run's own line rather
 * than fixed here, and the choices that do keep a host variable away from a script are
 * {@value #DOCKER} and {@value #PODMAN}, which run it in an image that was never given
 * that environment.
 *
 * @author Ivan Rodriguez
 */
@Configuration(proxyBeanMethods = false)
public class ScriptEngineConfig {

	/** Installs nothing, which is the default and Embabel's own behaviour. */
	public static final String NONE = "none";

	/** Runs a script as a spawned interpreter on the host, confined to the run root. */
	public static final String PROCESS = "process";

	/** Runs a script in a Docker container. */
	public static final String DOCKER = "docker";

	/** Runs a script in a Podman container. */
	public static final String PODMAN = "podman";

	/** The values the switch accepts, in the order it reads better than a set. */
	public static final List<String> CHOICES = List.of(NONE, PROCESS, DOCKER, PODMAN);

	/**
	 * The property the file tools take their root from, which is where the script root
	 * comes from too. Read as a key, not as a class: this module's edge to the addon is
	 * provided rather than compile, so a host that runs the runtime without the addon
	 * still boots and still has one root — its own working directory, which is the same
	 * rule the addon applies to a blank value.
	 */
	public static final String RUN_ROOT_PROPERTY = "smith.tools.coding.root";

	/**
	 * The property that says how much one script call may hand over. Read as a key rather
	 * than as a bound field for the same reason the engine switch is read: a native image
	 * would freeze a {@code @ConditionalOnProperty} at build time, and this is meant to
	 * be a deployment-time choice. Unset means
	 * {@link RecordingScriptExecutionEngine#DEFAULT_OUTPUT_CAP}; zero or less leaves
	 * output uncapped, which is the platform's own behaviour and is said rather than
	 * implied.
	 */
	public static final String OUTPUT_CAP_PROPERTY = "smith.tools.scripts.output-cap";

	/** The probe nobody has to name: the platform's own two availability checks. */
	private static final ContainerAvailability PLATFORM_CONTAINERS = new ContainerAvailability() {

		@Override
		public boolean dockerAvailable() {
			return DockerSkillScriptExecutionEngine.Companion.isDockerAvailable();
		}

		@Override
		public boolean podmanAvailable() {
			return PodmanSkillScriptExecutionEngine.Companion.isPodmanAvailable();
		}

	};

	/**
	 * The engine this run's scripts execute in, present whatever the switch says so that
	 * every boot has one answer to name. {@value #NONE} yields the platform's no-op
	 * engine, which claims no language and therefore offers no script tool — the same
	 * behaviour as installing nothing, arrived at by the same door as the other three.
	 * @param environment the boot's environment, which is where the switch and the root
	 * are read
	 * @param containers the container probe, replaceable by a deployment that wants the
	 * availability question answered differently
	 * @param scriptCalls where each script call of this boot is written down
	 * @return the selected engine, recording its calls
	 * @throws IllegalStateException when the value names none of the four, or names a
	 * container this host cannot run
	 */
	@Bean
	public SkillScriptExecutionEngine skillScriptExecutionEngine(Environment environment,
			ObjectProvider<ContainerAvailability> containers, ScriptCallLog scriptCalls) {
		return recorded(choose(choice(environment), scriptRoot(environment),
				containers.getIfAvailable(() -> PLATFORM_CONTAINERS)), scriptCalls, outputCap(environment));
	}

	/**
	 * The log one boot's script calls are recorded in.
	 * @return an empty log, filled as the run calls scripts
	 */
	@Bean
	public ScriptCallLog scriptCallLog() {
		return new ScriptCallLog();
	}

	/**
	 * The engine with its calls recorded, which is the engine the boot hands out.
	 *
	 * <p>
	 * The no-op engine is left unwrapped: it claims no language, so it is offered no
	 * script and there is no call to record. That keeps the default boot handing out the
	 * platform's own object, identity intact, which is what lets a test say that
	 * {@value #NONE} installs nothing.
	 * @param engine the engine the selection installed
	 * @param calls the log to write
	 * @return the engine, wrapped unless it runs nothing at all
	 */
	static SkillScriptExecutionEngine recorded(SkillScriptExecutionEngine engine, ScriptCallLog calls) {
		return recorded(engine, calls, RecordingScriptExecutionEngine.DEFAULT_OUTPUT_CAP);
	}

	/**
	 * The engine with its calls recorded and its output bounded, which is the engine the
	 * boot hands out.
	 * @param engine the engine the selection installed
	 * @param calls the log to write
	 * @param outputCap how much one call may hand over
	 * @return the engine, wrapped unless it runs nothing at all
	 */
	static SkillScriptExecutionEngine recorded(SkillScriptExecutionEngine engine, ScriptCallLog calls, int outputCap) {
		return (engine == NoOpExecutionEngine.INSTANCE) ? engine
				: new RecordingScriptExecutionEngine(engine, calls, outputCap);
	}

	/**
	 * How much one script call may hand over, as the boot wrote it.
	 * @param environment the boot's environment
	 * @return the configured ceiling, or the default when unset
	 */
	static int outputCap(Environment environment) {
		return environment
			.getProperty(OUTPUT_CAP_PROPERTY, Integer.class, RecordingScriptExecutionEngine.DEFAULT_OUTPUT_CAP)
			.intValue();
	}

	/**
	 * The engine a boot really runs scripts in, past the recording wrapper.
	 *
	 * <p>
	 * The claims worth seeing through a wrapper for are the ones about the engine itself
	 * — which engine the environment disclosure in {@link PackSkills} is speaking for.
	 * Asking the wrapper would name our class where the fact belongs to the platform's.
	 * @param engine the engine a run was handed, possibly wrapped
	 * @return the engine underneath
	 */
	public static SkillScriptExecutionEngine underlying(SkillScriptExecutionEngine engine) {
		return (engine instanceof RecordingScriptExecutionEngine recording) ? recording.delegate() : engine;
	}

	/**
	 * The engine a selection installs, with the root already applied.
	 * @param choice the switch's value, already trimmed and lower-cased
	 * @param root the run root the file tools share
	 * @param containers whether this host can run a container
	 * @return the engine
	 */
	static SkillScriptExecutionEngine choose(String choice, String root, ContainerAvailability containers) {
		return switch (choice) {
			case NONE -> NoOpExecutionEngine.INSTANCE;
			case PROCESS -> ProcessSkillScriptExecutionEngine.confinedTo(root);
			case DOCKER ->
				confinedIn(containers::dockerAvailable, DOCKER, root, DockerSkillScriptExecutionEngine::confinedTo);
			case PODMAN ->
				confinedIn(containers::podmanAvailable, PODMAN, root, PodmanSkillScriptExecutionEngine::confinedTo);
			default -> throw new IllegalStateException(PackSkills.SCRIPT_ENGINE_PROPERTY + "='" + choice
					+ "' names no script engine. The choices are " + CHOICES
					+ ". Nothing was installed, and an unrecognised value is refused rather than read as '" + NONE
					+ "', because a typo that fell through would look like a run that was never asked to execute anything.");
		};
	}

	/**
	 * The selection as written, or {@value #NONE} when the boot never mentioned it.
	 * @param environment the boot's environment
	 * @return the trimmed, lower-cased value
	 */
	private static String choice(Environment environment) {
		return environment.getProperty(PackSkills.SCRIPT_ENGINE_PROPERTY, NONE).trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * The root the scripts are confined to: the file tools' root, resolved the same way.
	 * @param environment the boot's environment
	 * @return the absolute, normalized root
	 */
	static String scriptRoot(Environment environment) {
		String configured = environment.getProperty(RUN_ROOT_PROPERTY, "").trim();
		var base = configured.isEmpty() ? Paths.get(System.getProperty("user.dir")) : Paths.get(configured);
		return base.toAbsolutePath().normalize().toString();
	}

	/**
	 * A container engine, or the refusal to run scripts on the host under its name.
	 * @param available the probe for this container
	 * @param engine the value that asked for it, quoted in the refusal
	 * @param root the run root
	 * @param factory the platform's own confined factory for that container
	 * @return the engine
	 */
	private static SkillScriptExecutionEngine confinedIn(BooleanSupplier available, String engine, String root,
			Function<String, SkillScriptExecutionEngine> factory) {
		if (!available.getAsBoolean()) {
			throw new IllegalStateException(PackSkills.SCRIPT_ENGINE_PROPERTY + "='" + engine
					+ "' asks for skill scripts" + " to run in " + engine
					+ ", and this host cannot run containers. Refusing rather than falling back"
					+ " to the host: the isolation is the reason the choice was made, and a script that ran here after"
					+ " being promised a container would report the promise as kept.");
		}
		return factory.apply(root);
	}

	/**
	 * Whether this host can run the container a choice asks for.
	 *
	 * <p>
	 * <b>Why the probe is ours.</b> The platform exposes no "this host has no daemon"
	 * state to observe: {@code ContainerStartupStatus} carries only {@code STARTED},
	 * {@code FAILED} and {@code UNKNOWN}, and the {@code Unavailable} case belongs to a
	 * different nested type whose producing method is private. So the refusal is worked
	 * out from the platform's own {@code isDockerAvailable()} and
	 * {@code isPodmanAvailable()} before an engine is constructed, and the pair is
	 * injectable so the refusal is assertable on a host that can run containers — which
	 * the alternative, "start a container and see", could not be.
	 */
	public interface ContainerAvailability {

		/**
		 * Whether this host can invoke Docker.
		 * @return {@code true} when a container could be started
		 */
		boolean dockerAvailable();

		/**
		 * Whether this host can invoke Podman.
		 * @return {@code true} when a container could be started
		 */
		boolean podmanAvailable();

	}

}
