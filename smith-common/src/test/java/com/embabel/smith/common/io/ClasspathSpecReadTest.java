/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.io;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.stream.Stream;

import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A pack that ships as a classpath directory reads exactly like one named on disk, from
 * either of the two shapes a classpath directory takes when the code runs.
 *
 * <p>
 * <b>Why the jar arm is the one that matters.</b> Run from exploded classes — which is
 * what a test run and {@code spring-boot:run} are — the classpath location <em>is</em> a
 * directory on the filesystem, and reading it is nearly the same call as reading a path
 * someone typed. The claim worth a test is the other arm: inside a jar there is no
 * directory to list, so {@link SpecFolders} has to reconstruct one, and a pack whose
 * {@code steps/} came back empty from that reconstruction would read as a well-formed
 * pack with one step missing rather than as a failure. Both arms therefore assert the
 * same steps, in the same order, and the jar arm asserts equality with the exploded read
 * rather than with a hand-written expectation, so the two cannot drift apart.
 *
 * <p>
 * <b>The fixture.</b> {@code src/test/resources/specs/acme-explainer} is on the test
 * classpath the way a shipped pack is on the main one, and its two steps and two types
 * are named so that the order the reader lists them in is the order it sorts them in, not
 * the order they were written down in.
 *
 * @author Ivan Rodriguez
 */
class ClasspathSpecReadTest {

	private static final String PACK = "specs/acme-explainer";

	private final AgentSpecReader reader = new AgentSpecReader();

	@Test
	void readsAPackFromExplodedClassesOnTheClasspath() throws Exception {
		AgentSpec pack = this.reader.readResource(PACK);

		assertThat(pack.name()).isEqualTo("acme-explainer");
		assertThat(pack.steps().stream().map(step -> step.name())).containsExactly("extractTopic", "writeExplainer");
		assertThat(pack.types().stream().map(type -> type.name())).containsExactly("ExplainerText", "TopicBrief");
		assertThat(pack.goalOutputType()).isEqualTo("ExplainerText");
	}

	@Test
	void readsTheSamePackFromInsideAJarWhereThereIsNoDirectoryToRead(@TempDir Path dir) throws Exception {
		AgentSpec exploded = this.reader.readResource(PACK);
		Path jar = jarOfExplodedPack(dir);

		AgentSpec fromJar = readWithClassLoaderOver(jar, () -> this.reader.readResource(PACK));

		assertThat(fromJar).isEqualTo(exploded);
	}

	@Test
	void refusesToInventWhereAPackOnTheClasspathIs() {
		assertThatThrownBy(() -> this.reader.readResource("specs/no-such-pack"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("specs/no-such-pack");
	}

	/**
	 * The extraction cache is keyed by the class loader as well as by the resource path,
	 * which is the half a single jar cannot show. The lookup goes through the context
	 * loader, so a second loader that answers for the same path out of a different jar
	 * has to be given its own copy; an entry keyed by path alone hands it the first
	 * loader's extraction, and reading one jar while asked for another is not a failure
	 * anything here would report.
	 */
	@Test
	void readsTheJarItsOwnLoaderAnswersFromWhenTwoJarsShareThePath(@TempDir Path dir) throws Exception {
		Path first = jarWithStep(dir.resolve("first.jar"), "stepFromFirstJar");
		Path second = jarWithStep(dir.resolve("second.jar"), "stepFromSecondJar");

		AgentSpec fromFirst = readWithClassLoaderOver(first, () -> this.reader.readResource(PACK));
		AgentSpec fromSecond = readWithClassLoaderOver(second, () -> this.reader.readResource(PACK));

		assertThat(fromFirst.steps().stream().map(step -> step.name())).containsExactly("stepFromFirstJar");
		assertThat(fromSecond.steps().stream().map(step -> step.name())).containsExactly("stepFromSecondJar");
	}

	/**
	 * A jar holding the same pack path twice over, in content only: the manifest, goal
	 * and single step are the shape the fixture on disk has, and the step is the one
	 * thing that says which jar the bytes came from. Directory entries are written as an
	 * archiver writes them, because a loader only answers for a directory it can see as
	 * one.
	 */
	private static Path jarWithStep(Path jar, String stepName) throws Exception {
		try (OutputStream bytes = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(bytes)) {
			putEntry(out, PACK, null);
			putEntry(out, PACK + "/steps", null);
			putEntry(out, PACK + "/manifest.yml", """
					name: acme-explainer
					version: 1.0.0
					defaultPlanner: GOAP
					defaultModel: null
					""");
			putEntry(out, PACK + "/goal.yml", """
					stepType: goal
					name: goalReached
					description: "The goal of the acme-explainer pack has been reached"
					outputTypeName: Outcome
					export: true
					""");
			putEntry(out, PACK + "/steps/" + stepName + ".yml", """
					stepType: action
					name: %s
					description: "The one step this pack declares"
					inputTypeNames:
					  - UserInput
					outputTypeName: Outcome
					prompt: |
					  Do the thing.
					""".formatted(stepName));
		}
		return jar;
	}

	/**
	 * One jar entry, as a directory when there is no content to put in it.
	 */
	private static void putEntry(JarOutputStream out, String name, String content) throws Exception {
		out.putNextEntry(new ZipEntry((content == null) ? name + "/" : name));
		if (content != null) {
			out.write(content.getBytes(StandardCharsets.UTF_8));
		}
		out.closeEntry();
	}

	/**
	 * The fixture pack as a jar, with the same resource paths it has on disk, and
	 * directory entries included the way an archiver writes them.
	 */
	private static Path jarOfExplodedPack(Path dir) throws Exception {
		Path exploded = SpecFolders.classpath(PACK);
		Path jar = dir.resolve("pack.jar");
		try (OutputStream bytes = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(bytes)) {
			try (Stream<Path> files = Files.walk(exploded)) {
				for (Path file : files.sorted().toList()) {
					String relative = exploded.relativize(file).toString().replace('\\', '/');
					String entry = relative.isEmpty() ? PACK : PACK + "/" + relative;
					out.putNextEntry(new ZipEntry(Files.isDirectory(file) ? entry + "/" : entry));
					if (Files.isRegularFile(file)) {
						Files.copy(file, out);
					}
					out.closeEntry();
				}
			}
		}
		return jar;
	}

	/**
	 * Runs a read with a loader that can see nothing but that jar, which is what makes
	 * the jar arm measure the jar rather than the exploded copy still sitting next to it.
	 */
	private static <T> T readWithClassLoaderOver(Path jar, Callable<T> read) throws Exception {
		ClassLoader loader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null);
		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		thread.setContextClassLoader(loader);
		try {
			return read.call();
		}
		finally {
			thread.setContextClassLoader(previous);
			if (loader instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}

}
