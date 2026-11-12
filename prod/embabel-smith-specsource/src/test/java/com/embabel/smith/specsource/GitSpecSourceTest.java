package com.embabel.smith.specsource;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for git-source resolution against a local bare repository (no
 * network, no model): clone-on-first-use, fetch-on-reresolve, branch/tag/SHA pinning,
 * default-branch following, ref/remote failures, and corrupt-cache recovery.
 */
class GitSpecSourceTest {

	@TempDir
	Path temp;

	private Path bare;

	private Path work;

	private Path cacheRoot;

	private final AgentSpecWriter writer = new AgentSpecWriter();

	@BeforeEach
	void setUp() {
		bare = temp.resolve("remote.git");
		run("git", "init", "--bare", "-b", "main", bare.toString());
		work = temp.resolve("work");
		run("git", "init", "-b", "main", work.toString());
		run("git", "-C", work.toString(), "remote", "add", "origin", bare.toString());
		cacheRoot = temp.resolve("cache");
	}

	@Test
	void firstResolutionClonesAndDiscoversPacks() throws IOException {
		pushPack("explainer", SampleAgentSpecs.explainer());

		ResolvedSpecSource resolved = source("main").resolve();

		assertThat(resolved.packs()).hasSize(1);
		assertThat(resolved.packs().get(0).name()).isEqualTo("explainer");
		assertThat(resolved.origin().root()).isEqualTo(cacheRoot.resolve(cacheKeyOf("git:" + bare + "@main")));
		assertThat(Files.isRegularFile(resolved.origin().root().resolve("explainer").resolve("manifest.yml"))).isTrue();
		assertThat(resolved.warnings()).isEmpty();
	}

	@Test
	void reResolutionFetchesInsteadOfRecloning() throws IOException {
		pushPack("explainer", SampleAgentSpecs.explainer());
		ResolvedSpecSource first = source("main").resolve();
		// an untracked marker survives a fetch; a fresh clone into this directory would
		// not
		Files.writeString(first.origin().root().resolve("marker.txt"), "keep");

		pushPack("trivial", SampleAgentSpecs.singleStep());
		ResolvedSpecSource second = source("main").resolve();

		assertThat(second.packs()).extracting(ResolvedSpec::name).containsExactlyInAnyOrder("explainer", "trivial");
		assertThat(Files.exists(second.origin().root().resolve("marker.txt"))).isTrue();
	}

	@Test
	void pinnedShaIsImmutableWhenTheRepoGainsCommits() throws IOException {
		pushPack("explainer", SampleAgentSpecs.explainer());
		String sha = run("git", "-C", work.toString(), "rev-parse", "main").trim();

		ResolvedSpecSource first = source(sha).resolve();
		pushPack("trivial", SampleAgentSpecs.singleStep());
		ResolvedSpecSource second = source(sha).resolve();

		assertThat(second.packs()).extracting(ResolvedSpec::name).containsExactly("explainer");
		assertThat(second.packs().get(0).contentHash()).isEqualTo(first.packs().get(0).contentHash());
	}

	@Test
	void pinnedTagIsStableWhenTheBranchAdvances() throws IOException {
		pushPack("explainer", SampleAgentSpecs.explainer());
		run("git", "-C", work.toString(), "tag", "v1");
		run("git", "-C", work.toString(), "push", "origin", "v1");

		ResolvedSpecSource atTag = source("v1").resolve();
		pushPack("trivial", SampleAgentSpecs.singleStep());
		ResolvedSpecSource reResolved = source("v1").resolve();

		assertThat(atTag.packs()).extracting(ResolvedSpec::name).containsExactly("explainer");
		assertThat(reResolved.packs()).extracting(ResolvedSpec::name).containsExactly("explainer");
	}

	@Test
	void unpinnedSourceFollowsTheDefaultBranchTip() throws IOException {
		pushPack("explainer", SampleAgentSpecs.explainer());
		ResolvedSpecSource first = source(null).resolve();
		assertThat(first.packs()).extracting(ResolvedSpec::name).containsExactly("explainer");

		pushPack("trivial", SampleAgentSpecs.singleStep());
		ResolvedSpecSource second = source(null).resolve();

		assertThat(second.packs()).extracting(ResolvedSpec::name).containsExactlyInAnyOrder("explainer", "trivial");
	}

	@Test
	void unknownRefFailsNamingTheMissingRef() throws IOException {
		pushPack("explainer", SampleAgentSpecs.explainer());

		assertThatThrownBy(() -> source("no-such-ref").resolve()).isInstanceOf(SpecSourceException.class)
			.hasMessageContaining("no-such-ref");
	}

	@Test
	void unreachableRemoteFailsNamingTheUrl() {
		Path missing = temp.resolve("does-not-exist.git");

		assertThatThrownBy(() -> sourceAt(missing.toString(), "main").resolve()).isInstanceOf(SpecSourceException.class)
			.hasMessageContaining(missing.toString());
	}

	@Test
	void nonGitDirectoryFails() throws IOException {
		Path plain = temp.resolve("plain");
		Files.createDirectories(plain);
		Files.writeString(plain.resolve("README.md"), "not a repo");

		assertThatThrownBy(() -> sourceAt(plain.toString(), "main").resolve()).isInstanceOf(SpecSourceException.class)
			.hasMessageContaining(plain.toString());
	}

	@Test
	void corruptCacheIsReplacedByAFreshClone() throws IOException {
		pushPack("explainer", SampleAgentSpecs.explainer());
		ResolvedSpecSource first = source("main").resolve();
		Path cache = first.origin().root();
		deleteTree(cache.resolve(".git"));

		ResolvedSpecSource second = source("main").resolve();

		assertThat(second.packs()).extracting(ResolvedSpec::name).containsExactly("explainer");
		assertThat(Files.isRegularFile(cache.resolve("explainer").resolve("manifest.yml"))).isTrue();
	}

	// --- helpers --------------------------------------------------------------

	private GitSpecSource source(String ref) {
		return sourceAt(bare.toString(), ref);
	}

	private GitSpecSource sourceAt(String url, String ref) {
		return new GitSpecSource("git:" + url + (ref == null ? "" : "@" + ref), url, ref, cacheRoot);
	}

	private void pushPack(String dirName, AgentSpec pack) throws IOException {
		writer.write(pack, work.resolve(dirName));
		run("git", "-C", work.toString(), "add", "-A");
		run("git", "-C", work.toString(), "-c", "user.email=test@example.com", "-c", "user.name=test", "commit", "-m",
				"add " + dirName);
		run("git", "-C", work.toString(), "push", "origin", "main");
	}

	private static String cacheKeyOf(String uri) {
		// mirrors GitSpecSource's cache layout: sha-256 hex of the full source URI
		try {
			return java.util.HexFormat.of()
				.formatHex(java.security.MessageDigest.getInstance("SHA-256")
					.digest(uri.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> stream = Files.walk(dir)) {
			stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.delete(path);
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	private static String run(String... command) {
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			int exit = process.waitFor();
			assertThat(exit).as("command %s failed: %s", String.join(" ", command), output).isEqualTo(0);
			return output;
		}
		catch (IOException | InterruptedException e) {
			throw new AssertionError(e);
		}
	}

}
