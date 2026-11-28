/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.tools.file.FileReadTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run root, enforced by this addon's own booted tools.
 *
 * <p>
 * <b>Which offered methods reach which platform resolver</b>, read off the methods the
 * booted {@code file} group actually offers (upstream {@code fileUtils.kt}):
 * <ul>
 * <li>{@code resolveAndValidateFile} — {@code readFile}, {@code editFile},
 * {@code appendFile}, {@code delete}. That resolver calls {@code resolvePath} first and
 * then adds existence and is-a-regular-file checks with {@code NOFOLLOW_LINKS}.</li>
 * <li>plain {@code resolvePath} only — {@code listFiles}, {@code fileSize},
 * {@code fileCount}, {@code exists}, {@code createFile} (and so {@code writeFile}, which
 * delegates to it), {@code createDirectory}.</li>
 * <li>neither — {@code findFiles} and {@code findPatternInProject}, which start at the
 * root itself and take no caller-supplied path.</li>
 * </ul>
 *
 * <p>
 * <b>Why the distinction does not change the answer here.</b> Both resolvers decide
 * containment <em>lexically</em>: normalize, then test {@code startsWith(root)}. A link
 * inside the root pointing outside therefore passes both of them — the extra
 * {@code NOFOLLOW_LINKS} checks in {@code resolveAndValidateFile} say nothing about where
 * the link leads. So the symlink case is this addon's to answer, and it is answered once,
 * in {@link RunRoot}, by comparing the real path of the deepest existing ancestor as well
 * as the lexical one. The {@code resolveAndValidateFile} half of the list is the half
 * that would have been silently readable and writable without that; the
 * {@code resolvePath} half is refused by upstream's traversal check but not by its link
 * check.
 *
 * <p>
 * <b>Why this boot sets {@code writes}.</b> Half the methods listed above are writes, and
 * an untouched deployment's group offers none of them, so the property is what puts them
 * on the table to be refused. The read-only half of the list is the same
 * {@link ConfiningTool} wrapper either way, and the default boot's absence of writers is
 * {@link DefaultBootStateTest}'s claim.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.coding.writes=true" })
class RunRootConfinementTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-confinement-");

	private static final Path OUTSIDE = ToolCalls.root("smith-tools-outside-");

	@Autowired
	private ToolGroup fileGroup;

	@Autowired
	private CodingFileTools fileTools;

	@DynamicPropertySource
	static void runRoot(DynamicPropertyRegistry registry) {
		registry.add("smith.tools.coding.root", ROOT::toString);
	}

	@Test
	@DisplayName("an unset root is the process working directory")
	void defaultRootIsTheWorkingDirectory() {
		assertThat(RunRoot.of("").path())
			.isEqualTo(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize());
		assertThat(RunRoot.of(null).path())
			.isEqualTo(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize());
	}

	@ParameterizedTest(name = "{0} refuses a traversal path naming both path and root")
	@ValueSource(strings = { "readFile", "listFiles", "fileSize", "writeFile", "createFile", "appendFile", "delete",
			"editFile" })
	void traversalIsRefused(String toolName) {
		String answer = call(toolName, "../../etc/passwd");
		assertRefusal(toolName, answer, "../../etc/passwd");
	}

	@ParameterizedTest(name = "{0} refuses an absolute path outside the root naming both path and root")
	@ValueSource(strings = { "readFile", "listFiles", "fileSize", "writeFile", "createFile", "appendFile", "delete",
			"editFile" })
	void absolutePathOutsideIsRefused(String toolName) {
		String answer = call(toolName, OUTSIDE.resolve("secret.txt").toString());
		assertRefusal(toolName, answer, OUTSIDE.resolve("secret.txt").toString());
	}

	@Test
	@DisplayName("a link inside the root leading outside is refused — the case upstream's lexical check lets through")
	void symlinkEscapeIsRefused() throws Exception {
		Path secret = givenSecretOutside();
		String answer = call("readFile", "link/secret.txt");
		assertRefusal("readFile", answer, "link/secret.txt");
		assertThat(answer).doesNotContain("do not read secrets");
		assertThat(Files.exists(secret)).as("nothing read it, so nothing moved").isTrue();
	}

	@ParameterizedTest(name = "{0} refuses the same link argument the read tool refuses")
	@ValueSource(strings = { "listFiles", "fileSize", "writeFile", "createFile", "appendFile", "delete", "editFile" })
	void everyPathTakingToolRefusesTheLink(String toolName) throws Exception {
		givenSecretOutside();
		assertRefusal(toolName, call(toolName, "link/secret.txt"), "link/secret.txt");
	}

	@Test
	@DisplayName("the gap is real: the same platform instance, unconfined, reads straight through the link")
	void upstreamInstanceAloneDoesNotRefuseIt() throws Exception {
		givenSecretOutside();
		FileReadTools unconfined = this.fileTools.readTools();
		assertThat(unconfined.readFile("link/secret.txt")).as("the addon's layer is what closes this")
			.contains("do not read secrets");
	}

	private Path givenSecretOutside() throws Exception {
		Path secret = OUTSIDE.resolve("secret.txt");
		if (!Files.exists(secret)) {
			Files.writeString(secret, "do not read secrets");
		}
		if (!Files.exists(ROOT.resolve("inside.txt"))) {
			Files.writeString(ROOT.resolve("inside.txt"), "inside the root");
		}
		if (Files.notExists(ROOT.resolve("link"))) {
			Files.createSymbolicLink(ROOT.resolve("link"), OUTSIDE);
		}
		return secret;
	}

	private String call(String toolName, String path) {
		return ToolCalls.call(ToolCalls.tool(this.fileGroup, toolName), "path", path);
	}

	private static void assertRefusal(String toolName, String answer, String path) {
		assertThat(answer).as("%s answered on an escaping path", toolName).startsWith("ERROR:");
		assertThat(answer).as("%s named the path", toolName).contains("path=" + path);
		assertThat(answer).as("%s named the root", toolName).contains("root=" + ROOT);
	}

}
