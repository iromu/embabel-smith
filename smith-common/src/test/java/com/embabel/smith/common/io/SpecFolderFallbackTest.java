/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.io;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What {@link SpecFolders#classpath(String)} does when the classpath cannot answer with a
 * directory, which is the case a native binary is always in.
 *
 * <p>
 * <b>The claim.</b> An image holds resources as a flat named set, so asking its loader
 * for {@code specs/<pack>} gets nothing listable back. A refusal at that point — which is
 * what this method used to do — leaves the binary with no way to reach its own packs, so
 * the method falls through to two routes that name a directory on disk: the override and
 * the folder beside the running program. The tests here are about that fall-through and,
 * just as much, about when it must <em>not</em> happen: a run that can list its classpath
 * has to keep reading the pack that was compiled in, or an override left exported in
 * someone's shell would silently replace it. Test 2 is the one that pins that ordering;
 * the others would stay green if the fallbacks were tried first.
 *
 * <p>
 * <b>What gets said.</b> One of these routes can answer after the class loader had
 * already located the pack, and then the run reads a copy of it that did not ship. The
 * last two tests pin the difference that has to make: a warning naming both the located
 * resource and the directory used, and nothing at all when the classpath had no entry to
 * substitute — which is the route an image takes on every run.
 *
 * <p>
 * <b>What is not covered.</b> The environment-variable arm is the same two lines as the
 * property arm and is not driven separately — a JVM cannot set its own environment, and
 * the only way to do it would be to fork. Where that variable happens to be set in the
 * environment running the suite, the tests that depend on it being absent assume out
 * rather than fail, the way the repo's other environment-gated tests do. And nothing here
 * builds an image: the resolution order is the evidence, not a run of
 * {@code target/smith}.
 *
 * @author Ivan Rodriguez
 */
class SpecFolderFallbackTest {

	private static final String DECOY = "specs/acme-explainer";

	private final AgentSpecReader reader = new AgentSpecReader();

	private String previousOverride;

	@BeforeEach
	void rememberTheOverride() {
		this.previousOverride = System.getProperty(SpecFolders.OVERRIDE_PROPERTY);
		Assumptions.assumeTrue(System.getenv(SpecFolders.OVERRIDE_ENV) == null,
				SpecFolders.OVERRIDE_ENV + " is set in this environment, so the unset-override cases cannot run");
	}

	@AfterEach
	void restoreTheOverride() {
		if (this.previousOverride == null) {
			System.clearProperty(SpecFolders.OVERRIDE_PROPERTY);
		}
		else {
			System.setProperty(SpecFolders.OVERRIDE_PROPERTY, this.previousOverride);
		}
	}

	/**
	 * The route the binary depends on: with nothing on the classpath to list, the
	 * override is what answers, and the pack read from it is a pack in full — not an
	 * empty directory that reads as a well-formed pack with no steps.
	 */
	@Test
	void readsAPackFromTheOverrideWhenTheClasspathCannotListIt(@TempDir Path dir) throws Exception {
		Path pack = writePack(dir.resolve("specs").resolve("override-only"), "override-only", "draftFromOverride");
		System.setProperty(SpecFolders.OVERRIDE_PROPERTY, dir.toString());

		Path located = SpecFolders.classpath("specs/override-only");
		AgentSpec packRead = this.reader.readResource("specs/override-only");

		assertThat(located).isEqualTo(pack).startsWith(dir);
		assertThat(packRead.name()).isEqualTo("override-only");
		assertThat(packRead.steps().stream().map(step -> step.name())).containsExactly("draftFromOverride");
	}

	/**
	 * The ordering claim. The override is the escape hatch for a location that cannot be
	 * listed, not a redirect: with the classpath able to answer, a decoy parked under the
	 * override must stay unread.
	 */
	@Test
	void leavesTheClasspathPackAloneEvenWithAnOverrideSet(@TempDir Path dir) throws Exception {
		writePack(dir.resolve("specs").resolve("acme-explainer"), "acme-explainer", "theDecoyStep");
		System.setProperty(SpecFolders.OVERRIDE_PROPERTY, dir.toString());

		Path located = SpecFolders.classpath(DECOY);
		AgentSpec packRead = this.reader.readResource(DECOY);

		assertThat(located.startsWith(dir)).isFalse();
		assertThat(packRead.steps().stream().map(step -> step.name())).containsExactly("extractTopic", "writeExplainer")
			.doesNotContain("theDecoyStep");
	}

	/**
	 * The substitution the ordering above cannot prevent, and the one that therefore has
	 * to be said out loud: the class loader <em>found</em> something at that path, could
	 * not list it, and the override answered with a different pack. The warning names
	 * both the located URL and the directory used, because a run reading a pack that did
	 * not ship has nothing else about it that would say so.
	 */
	@Test
	void warnsByNamingBothCopiesWhenTheClasspathLocatedWhatItCouldNotList(@TempDir Path dir) throws Exception {
		Path pack = writePack(dir.resolve("specs").resolve("acme-explainer"), "acme-explainer", "theDecoyStep");
		System.setProperty(SpecFolders.OVERRIDE_PROPERTY, dir.toString());
		List<String> warnings = new ArrayList<>();

		Path located = warningsDuring(warnings,
				() -> withContextClassLoader(namingButNotListing(DECOY), () -> SpecFolders.classpath(DECOY)));

		assertThat(located).isEqualTo(pack).startsWith(dir);
		assertThat(warnings).singleElement()
			.satisfies((warning) -> assertThat(warning).contains(DECOY)
				.contains(dir.resolve("specs").resolve("acme-explainer").toString()));
	}

	/**
	 * The other half, which is what keeps that warning honest rather than noise: a
	 * classpath with <em>no entry at all</em> under that name is the documented route an
	 * image takes every run, not a substitution, so nothing is logged.
	 */
	@Test
	void staysQuietWhenTheClasspathHadNoEntryForTheOverrideToReplace(@TempDir Path dir) throws Exception {
		Path pack = writePack(dir.resolve("specs").resolve("override-only"), "override-only", "draftFromOverride");
		System.setProperty(SpecFolders.OVERRIDE_PROPERTY, dir.toString());
		List<String> warnings = new ArrayList<>();

		assertThat(warningsDuring(warnings, () -> SpecFolders.classpath("specs/override-only"))).isEqualTo(pack);
		assertThat(warnings).isEmpty();
	}

	/**
	 * A refusal has to say where it looked, because the three locations are all in
	 * different places and the caller cannot see them from here.
	 */
	@Test
	void namesTheClasspathAndBothOverrideNamesWhenNothingIsSet() {
		assertThatThrownBy(() -> SpecFolders.classpath("specs/no-such-pack-anywhere"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("specs/no-such-pack-anywhere")
			.hasMessageContaining(SpecFolders.OVERRIDE_PROPERTY)
			.hasMessageContaining(SpecFolders.OVERRIDE_ENV)
			.hasMessageContaining("classpath");
	}

	/**
	 * The same, for the failure a mistyped override actually produces: the named folder
	 * is there, the pack under it is not, and the message has to show the path it went
	 * looking for rather than only the variable's value.
	 */
	@Test
	void namesThePathItLookedForWhenTheOverrideIsSetButEmpty(@TempDir Path dir) {
		System.setProperty(SpecFolders.OVERRIDE_PROPERTY, dir.toString());

		assertThatThrownBy(() -> SpecFolders.classpath("specs/no-such-pack-either"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(dir.resolve("specs").resolve("no-such-pack-either").toString());
	}

	/**
	 * Runs a call with everything {@link SpecFolders} logs captured into
	 * {@code warnings}. It logs through {@link System.Logger}, whose JDK implementation
	 * writes to a JUL logger of the same name — the one this detaches from its parents
	 * and taps, so the assertion does not depend on what the suite's logging backend
	 * happens to be.
	 * @param warnings the list the logged messages land in
	 * @param call the call to run while listening
	 * @return whatever the call returned
	 */
	private static Path warningsDuring(List<String> warnings, Callable<Path> call) throws Exception {
		Handler captured = new Handler() {

			@Override
			public void publish(LogRecord record) {
				if (record != null && record.getMessage() != null) {
					warnings.add(record.getMessage());
				}
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		Logger logger = Logger.getLogger(SpecFolders.class.getName());
		boolean parentLogs = logger.getUseParentHandlers();
		logger.setUseParentHandlers(false);
		logger.addHandler(captured);
		try {
			return call.call();
		}
		finally {
			logger.removeHandler(captured);
			logger.setUseParentHandlers(parentLogs);
		}
	}

	/**
	 * A loader that names {@code resource} with a URL nothing here can open as a
	 * directory, which is the shape of a resource-only entry, and answers for nothing
	 * else. It is how a test reaches the located-but-unlistable case without leaving the
	 * JVM it runs in.
	 * @param resource the resource path to answer for
	 * @return the loader
	 */
	private static ClassLoader namingButNotListing(String resource) throws MalformedURLException {
		URL named = URI.create("http://spec-folders.invalid/" + resource).toURL();
		return new ClassLoader(null) {

			@Override
			public URL getResource(String name) {
				return resource.equals(name) ? named : super.getResource(name);
			}
		};
	}

	/**
	 * Runs a call with {@code loader} as the context loader, which is the loader
	 * {@link SpecFolders} asks.
	 * @param loader the loader to install
	 * @param call the call to run under it
	 * @return whatever the call returned
	 */
	private static <T> T withContextClassLoader(ClassLoader loader, Callable<T> call) throws Exception {
		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		thread.setContextClassLoader(loader);
		try {
			return call.call();
		}
		finally {
			thread.setContextClassLoader(previous);
		}
	}

	/**
	 * A pack of the shape a shipped pack has: a manifest, a goal, and one step under
	 * {@code steps/}, so the read exercises the enumeration rather than one file open.
	 */
	private static Path writePack(Path pack, String name, String stepName) throws Exception {
		Files.createDirectories(pack.resolve("steps"));
		Files.writeString(pack.resolve("manifest.yml"), """
				name: %s
				version: 1.0.0
				defaultPlanner: GOAP
				defaultModel: null
				""".formatted(name));
		Files.writeString(pack.resolve("goal.yml"), """
				stepType: goal
				name: goalReached
				description: "The goal of the %s pack has been reached"
				outputTypeName: Outcome
				export: true
				""".formatted(name));
		Files.writeString(pack.resolve("steps").resolve(stepName + ".yml"), """
				stepType: action
				name: %s
				description: "The one step this pack declares"
				inputTypeNames:
				  - UserInput
				outputTypeName: Outcome
				prompt: |
				  Do the thing.
				""".formatted(stepName));
		return pack;
	}

}
