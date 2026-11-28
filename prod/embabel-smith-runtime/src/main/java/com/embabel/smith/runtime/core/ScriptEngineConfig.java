/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.lang.reflect.Constructor;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.embabel.agent.skills.script.DockerSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.PodmanSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.agent.tools.file.FileTools;

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
 * process engine gets a {@code FileTools} confined to the root the file tools confine to
 * — the value of {@value #RUN_ROOT_PROPERTY}, through the same factory
 * {@code confinedTo(root)} itself calls — rather than a second location a pack could
 * escape by knowing the difference.
 *
 * <p>
 * <b>Why the host environment is filtered even though the root already confines
 * paths.</b> {@code confinedTo} sets the {@code FileTools} argument and nothing else, and
 * the platform's own environment switch — construct the engine with inheritance off and
 * it keeps only its allowlist, a dozen or so entries like {@code PATH} — defaults to on
 * in every factory, so an engine built by that factory hands a script the environment the
 * JVM was started with, API keys among it. The switch is reachable but not nameable: the
 * constructors a Java caller can see stop before the pair wanted here — the public forms
 * that carry {@code inheritEnvironment} take no {@code FileTools} root, and the one that
 * takes both is private — so the engine is built by naming that constructor to the JVM,
 * with every other argument read from the platform rather than copied here.
 * {@code ScriptEnvironmentTest} is what keeps that a dependency on a shape rather than a
 * guess at one. Nothing a script relies on is lost: the platform adds {@code INPUT_DIR}
 * and {@code OUTPUT_DIR} <em>after</em> filtering, which that test pins too, because a
 * script that cannot find its output directory fails in a way that looks unrelated to
 * this choice. The two container choices keep a host variable away from a script by a
 * different route — {@value #DOCKER} and {@value #PODMAN} run it in an image that was
 * never given that environment — so what is stated here belongs to the process engine
 * alone, and the run's line says which engine it speaks for.
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

	/**
	 * The platform's own script timeout, in the form its constructor takes it. That
	 * constructor's first parameter is a {@code long} because {@code Duration} is a
	 * Kotlin value class, and the encoding is nanoseconds shifted left by one — measured
	 * against a default-constructed engine rather than guessed, which is also what
	 * explains why an argument written as raw nanoseconds looks like half the limit it
	 * names. It is a constant rather than a read field because nothing here depends on
	 * its value, and {@code ScriptEnvironmentTest} pins that it still equals the
	 * platform's default, so a platform that changes it fails a test instead of quietly
	 * changing run behaviour.
	 */
	static final long PLATFORM_DEFAULT_TIMEOUT = (30L * 1_000_000_000L) << 1;

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
			case PROCESS -> hostEngine(root);
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
	 * The process engine this run scripts in: confined to the run root the way the
	 * platform's own factory confines, and with host environment inheritance switched
	 * off, which neither that factory nor any constructor a Java caller can name does.
	 *
	 * <p>
	 * <b>Why this is a reflective call rather than a constructor invocation.</b> The
	 * constructors Java can see are a ladder of {@code @JvmOverloads} forms that stop
	 * before the two arguments wanted together: the public forms that carry
	 * {@code inheritEnvironment} do not carry a {@code FileTools} root, and the one that
	 * carries both is private. So the engine is built by naming that constructor to the
	 * JVM and asking it to honour the confinement and withhold the environment in the
	 * same object. That is a deliberate dependency on a shape rather than an accident of
	 * one: the shape is asserted by {@code ScriptEnvironmentTest}, which runs a script
	 * and reads its environment, and a platform that moves the constructor fails this
	 * method's refusal at boot rather than a test nobody reads.
	 *
	 * <p>
	 * Everything else is the platform's own default and taken from the platform rather
	 * than restated: interpreters and the allowlist are read from its companion, the
	 * languages are the whole enum its default is built from, the timeout is its default
	 * in the raw form the constructor wants. Only two things differ from
	 * {@code confinedTo(root)} — inheritance, and the fact that this one had to say so.
	 * @param root the run root, confined exactly as the file tools confine it
	 * @return the engine
	 * @throws IllegalStateException when this platform version cannot be asked for an
	 * engine that withholds the host environment. It does not fall back to
	 * {@code confinedTo}, because that would run scripts with the host's variables in
	 * hand after the run had been told otherwise — the same promise-reported-as-kept
	 * failure as a container choice that quietly ran on the host.
	 */
	static SkillScriptExecutionEngine hostEngine(String root) {
		try {
			Constructor<?> declared = ProcessSkillScriptExecutionEngine.class.getDeclaredConstructor(long.class,
					Set.class, Map.class, Map.class, boolean.class, Set.class, FileTools.class);
			declared.setAccessible(true);
			return (SkillScriptExecutionEngine) declared.newInstance(PLATFORM_DEFAULT_TIMEOUT,
					EnumSet.allOf(ScriptLanguage.class),
					ProcessSkillScriptExecutionEngine.Companion.getDefaultInterpreters(), Map.of(), Boolean.FALSE,
					ProcessSkillScriptExecutionEngine.Companion.getSAFE_ENV_ALLOWLIST(), FileTools.readWrite(root));
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			throw new IllegalStateException(PackSkills.SCRIPT_ENGINE_PROPERTY + "='" + PROCESS + "' asks for scripts"
					+ " to run on the host with the host's environment withheld, and this platform version offers no"
					+ " constructor that takes a root and that switch together. Refusing rather than falling back to"
					+ " the platform's own factory: that one leaves inheritance on, and a script that ran with every"
					+ " variable this JVM holds after being promised the allowlist would report the promise as kept.",
					ex);
		}
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
