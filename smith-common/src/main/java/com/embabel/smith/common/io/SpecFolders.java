/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Addresses a pack directory that ships on the classpath as a directory {@link Path}.
 *
 * <p>
 * <b>Why a directory and not a stream.</b> {@link AgentSpecReader} enumerates
 * {@code steps/} and {@code types/} — the file names are the pack's business, not the
 * reader's — so a pack has to arrive as something listable, which a resource
 * {@code InputStream} is not. This class therefore hands back a directory rather than
 * reading anything itself, and {@code AgentSpecReader.read(Path)} stays the only place a
 * pack's bytes are parsed, whether the pack was found on the classpath or named on disk.
 *
 * <p>
 * <b>The two shapes a classpath directory takes.</b> Run from exploded classes — a test
 * run, or {@code spring-boot:run} — {@code getResource} returns a {@code file:} URL that
 * <em>is</em> the directory, and it is returned as it stands, with no copy. Run from a
 * jar, the same call returns a {@code jar:} URL, and no {@code Path} can point inside a
 * jar, so the subtree is written out once to a temporary directory and that is what gets
 * returned. The extraction is cached per class loader and location, so a process that
 * loads the same pack fifty times pays one copy, and a second loader that sees the same
 * resource path in a different jar gets its own copy rather than the first loader's. The
 * temporary trees are deleted when the process exits.
 *
 * <p>
 * <b>Where the class loader cannot answer, and what is tried next.</b> A native image
 * carries resources as a flat, named set with no directory entries, so
 * {@code getResource} of a directory is not a thing it can answer and no listing follows
 * it. Rather than refuse there, this method falls through to two routes that name a
 * directory on disk: an explicit override ({@value #OVERRIDE_PROPERTY} or
 * {@value #OVERRIDE_ENV}, each naming a folder that the resource path resolves below),
 * and the {@code specs/} folder sitting beside the running program, which the build ships
 * next to the image for exactly this reason. The order keeps the shipped pack
 * authoritative: a run that <em>can</em> list its own classpath never consults either
 * fallback, so an override left set in someone's shell cannot silently replace the pack
 * that was compiled in. The one case that order cannot protect is a classpath that
 * <em>locates</em> the resource and cannot list it, because a fallback then answers with
 * a different copy of the pack — and that substitution is logged, since a run reading a
 * pack other than the one that shipped has nothing else about it that would say so. When
 * all three come up empty, one message names every location that was tried rather than
 * guessing at file names.
 *
 * @author Ivan Rodriguez
 */
public final class SpecFolders {

	/**
	 * System property naming a directory to look for a pack in when the classpath cannot
	 * be listed. Read at call time rather than bound at startup, so a test can point it
	 * at a temporary folder. Package-private because the test that proves the resolution
	 * order lives in this package.
	 */
	static final String OVERRIDE_PROPERTY = "smith.specs.dir";

	/**
	 * Environment variable doing the same job as {@link #OVERRIDE_PROPERTY}. It is the
	 * only one of the two a native binary can be given, because an image does not parse
	 * the {@code -D} flags a JVM would read; the property is what a JVM run and a test
	 * set.
	 */
	static final String OVERRIDE_ENV = "SMITH_SPECS_DIR";

	/**
	 * The channel for the one thing a caller cannot see from here: that the pack it is
	 * about to read is not the copy that shipped in the jar. It is the JDK's logging API
	 * and not SLF4J because this module's compile classpath holds no logging dependency
	 * to borrow it from — the same reason {@code embabel-smith-skillevaluator}'s scanners
	 * use it — and an application that hosts this one still routes it wherever its own
	 * logging goes.
	 */
	private static final System.Logger LOG = System.getLogger(SpecFolders.class.getName());

	/**
	 * The extractions already paid for, per class loader and then per location. The
	 * loader is part of the key because the lookup goes through the context loader: two
	 * loaders that each see a different jar at the same resource path must not be handed
	 * the same copy. The inner maps are only ever touched through {@link #EXTRACTED},
	 * which is why they can be plain {@link ConcurrentHashMap}s.
	 */
	private static final Map<ClassLoader, Map<String, Path>> EXTRACTED = new ConcurrentHashMap<>();

	/**
	 * The roots of the temporary trees this process extracted, which the shutdown hook
	 * deletes.
	 */
	private static final Set<Path> EXTRACT_ROOTS = ConcurrentHashMap.newKeySet();

	static {
		// Safe to delete at exit because such a tree is only ever a stand-in for a pack
		// that is still in the jar: nothing writes into it, so there is nothing there to
		// lose. A hook rather than File.deleteOnExit because it deletes the whole tree
		// including the directories deleteOnExit would have left behind.
		Runtime.getRuntime().addShutdownHook(new Thread(SpecFolders::reclaimExtracted, "smith-spec-extract-cleanup"));
	}

	private SpecFolders() {
	}

	/**
	 * The directory a classpath location occupies, or the directory holding the same pack
	 * when the classpath cannot be listed as one. A fallback that answers after the class
	 * loader had located that location says so with a warning naming both.
	 * @param resourcePath the slash-separated resource path, e.g.
	 * {@code specs/builder-single-spec}
	 * @return a directory on the filesystem holding the resources below
	 * {@code resourcePath}: the class directory itself when the classes are exploded, a
	 * temporary copy when they are inside a jar, and the directory named by the override
	 * or by the running program when neither can be listed
	 * @throws IllegalStateException when no route can list that location, with a message
	 * naming every location that was tried
	 */
	public static Path classpath(String resourcePath) {
		String location = normalize(resourcePath);
		List<String> tried = new ArrayList<>();
		Path fromClasspath = fromClassLoader(location, tried);
		if (fromClasspath != null) {
			return fromClasspath;
		}
		Path fromOverride = fromOverride(location, tried);
		if (fromOverride != null) {
			return substituted(location, fromOverride);
		}
		Path fromProgram = fromRunningProgram(location, tried);
		if (fromProgram != null) {
			return substituted(location, fromProgram);
		}
		throw new IllegalStateException("Cannot list a spec directory at '" + location + "'. Tried "
				+ String.join("; ", tried) + ". Point '" + OVERRIDE_PROPERTY + "' or '" + OVERRIDE_ENV
				+ "' at a directory that holds '" + location + "'.");
	}

	/**
	 * The pack as a classpath directory, which is the route that answers on a JVM.
	 * @param location the normalized resource path
	 * @param tried the reasons collected for the refusal, appended to when this route
	 * does not answer
	 * @return the directory, or {@code null} to fall through to the on-disk routes
	 */
	private static Path fromClassLoader(String location, List<String> tried) {
		ClassLoader loader = classLoader();
		URL url = loader.getResource(location);
		if (url == null) {
			tried.add("the classpath, which has no directory entry at '" + location
					+ "' (a native image holds resources as a flat named set)");
			return null;
		}
		if ("file".equals(url.getProtocol())) {
			Path dir = fromFileUrl(url);
			if (!Files.isDirectory(dir)) {
				tried.add("the classpath location " + dir + ", which is not a directory");
				return null;
			}
			return dir;
		}
		try {
			return extracted(location, loader);
		}
		catch (UncheckedIOException | IllegalStateException ex) {
			// A named-file-only location answers this way: a `resource:` URL in an image,
			// or a
			// directory the loader names but cannot open.
			tried.add("the classpath location " + url + ", which cannot be listed: " + ex.getMessage());
			return null;
		}
	}

	/**
	 * The pack below a directory someone named, the route that works in a native binary.
	 * @param location the normalized resource path
	 * @param tried the reasons collected for the refusal, appended to when this route
	 * does not answer
	 * @return the directory, or {@code null} when nothing is named or the named folder
	 * does not hold one
	 */
	private static Path fromOverride(String location, List<String> tried) {
		String named = System.getProperty(OVERRIDE_PROPERTY);
		if (named == null || named.isBlank()) {
			named = System.getenv(OVERRIDE_ENV);
		}
		if (named == null || named.isBlank()) {
			tried.add("no " + OVERRIDE_PROPERTY + " or " + OVERRIDE_ENV + ", which were both unset");
			return null;
		}
		Path dir = Path.of(named).resolve(location);
		if (!Files.isDirectory(dir)) {
			tried.add("the override '" + named + "', which holds no directory " + dir);
			return null;
		}
		return dir;
	}

	/**
	 * The pack below the running program's own directory, which is where the native build
	 * ships the pack folder beside the image.
	 * @param location the normalized resource path
	 * @param tried the reasons collected for the refusal, appended to when this route
	 * does not answer
	 * @return the directory, or {@code null} when the program's location is not
	 * reportable or nothing sits next to it — on a JVM the program is the {@code java}
	 * binary, so this route is inert there by construction
	 */
	private static Path fromRunningProgram(String location, List<String> tried) {
		String program = ProcessHandle.current().info().command().orElse(null);
		Path parent = (program != null) ? Path.of(program).getParent() : null;
		Path dir = (parent != null) ? parent.resolve(location) : null;
		if (dir != null && Files.isDirectory(dir)) {
			return dir;
		}
		tried.add((dir != null) ? "the directory beside the running program at " + dir
				: "the running program, whose path this process cannot report");
		return null;
	}

	/**
	 * The warning for the one substitution this class makes without being asked for one:
	 * the class loader found something at that location, could not list it, and a
	 * directory someone named is now answering in its place. Saying nothing here is what
	 * let a run read a pack other than the one that shipped with nothing to show for it.
	 * The loader is asked again rather than the answer carried down from the first route,
	 * because this is reached only after that route has already failed.
	 * @param location the normalized resource path
	 * @param used the directory being returned instead of the located one
	 * @return {@code used}, so a caller returns it and warns in one step
	 */
	private static Path substituted(String location, Path used) {
		URL located = classLoader().getResource(location);
		if (located != null) {
			LOG.log(System.Logger.Level.WARNING, "The class loader located " + located + " but could not list it"
					+ " as a directory, so this run reads the pack from " + used + " instead.");
		}
		return used;
	}

	/**
	 * The extraction of a jar-resident location, cached per loader and location so a
	 * process loading the same pack fifty times pays one copy and a second loader that
	 * sees a different jar under the same resource path is not handed the first loader's
	 * copy.
	 * @param location the normalized resource path
	 * @param loader the loader the location was found through
	 * @return the temporary directory the subtree was written out to
	 */
	private static Path extracted(String location, ClassLoader loader) {
		return EXTRACTED.computeIfAbsent(loader, (key) -> new ConcurrentHashMap<>())
			.computeIfAbsent(location, (key) -> {
				try {
					return extract(key, loader);
				}
				catch (IOException ex) {
					throw new UncheckedIOException(
							"Cannot materialize the spec directory '" + key + "': " + ex.getMessage(), ex);
				}
			});
	}

	private static Path extract(String location, ClassLoader loader) throws IOException {
		URL url = loader.getResource(location);
		if (url == null) {
			throw new IOException("The classpath location '" + location + "' disappeared between lookup and listing");
		}
		if (!"jar".equals(url.getProtocol())) {
			throw new IOException("The classpath location '" + location + "' is a " + url.getProtocol()
					+ " URL, and only file: and jar: locations can be listed as directories");
		}
		if (!(url.openConnection() instanceof JarURLConnection connection)) {
			throw new IOException("The jar URL for '" + location + "' is not a " + JarURLConnection.class.getName()
					+ ", so its entries cannot be listed");
		}
		connection.setUseCaches(false);
		String prefix = prefixOf(connection.getEntryName());
		Path temp = Files.createTempDirectory("smith-spec-");
		EXTRACT_ROOTS.add(temp);
		Path root = temp.resolve(location);
		Files.createDirectories(root);
		try (JarFile jar = connection.getJarFile()) {
			for (JarEntry entry : Collections.list(jar.entries())) {
				String name = entry.getName();
				if (entry.isDirectory() || !name.startsWith(prefix)) {
					continue;
				}
				Path target = root.resolve(name.substring(prefix.length()));
				Files.createDirectories(target.getParent());
				try (InputStream content = jar.getInputStream(entry)) {
					Files.copy(content, target);
				}
			}
		}
		return root;
	}

	/**
	 * Deletes every tree this process extracted, which is what the shutdown hook runs.
	 * Safe because such a tree is only ever a stand-in for a pack that is still in the
	 * jar: nothing writes into it, so a run that ends leaves nothing behind that anyone
	 * still needed.
	 */
	private static void reclaimExtracted() {
		for (Path root : EXTRACT_ROOTS) {
			deleteTree(root);
		}
	}

	/**
	 * Deletes a directory tree, best effort: a file the OS will not hand back is a disk
	 * leak at exit, not a wrong answer, and there is nobody left to tell.
	 * @param root the directory to delete, which may already be gone
	 */
	private static void deleteTree(Path root) {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder()).forEach((path) -> {
				try {
					Files.delete(path);
				}
				catch (IOException ignored) {
					// best-effort cleanup; ignore failures
				}
			});
		}
		catch (IOException ignored) {
			// best-effort cleanup; ignore failures
		}
	}

	/**
	 * The entry prefix to strip, so the names below a directory come back relative to it.
	 * @param entryName the name the connection reports for the located directory
	 * @return that name with a single trailing separator, matching every entry below it
	 */
	private static String prefixOf(String entryName) {
		String trimmed = entryName.endsWith("/") ? entryName.substring(0, entryName.length() - 1) : entryName;
		return trimmed + "/";
	}

	private static Path fromFileUrl(URL url) {
		try {
			return Path.of(url.toURI());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot turn the classpath URL " + url + " into a directory", ex);
		}
	}

	private static String normalize(String resourcePath) {
		String location = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
		return location.endsWith("/") ? location.substring(0, location.length() - 1) : location;
	}

	private static ClassLoader classLoader() {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		return (context != null) ? context : SpecFolders.class.getClassLoader();
	}

}
