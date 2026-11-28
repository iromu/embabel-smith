/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.agent.core.ToolGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The edit semantics the coding-tool spec asks for, exercised through the booted
 * {@code file} group rather than against the class.
 *
 * <p>
 * <b>Why this tool exists at all.</b> The platform's own {@code editFile} replaces every
 * occurrence of the span with one {@code String.replace} call and answers
 * {@code "file edited"} without naming the file it changed. So an ambiguous span silently
 * rewrites several places the model never pointed at, and a successful edit leaves the
 * model without the path it just changed. The addon supersedes it under the same tool
 * name, which is why the group offers exactly one {@code editFile} — see
 * {@link PublishedFileGroupTest#upstreamEditIsSupersededOnce()}.
 *
 * <p>
 * <b>Why this boot sets {@code writes}.</b> Editing is a write, and the default-on group
 * is read-only until {@code smith.tools.coding.writes} says otherwise, so without this
 * property the tool under test would not be offered at all. The default boot — writes
 * off, no edit tool anywhere — is {@link DefaultBootStateTest}'s claim, not this one.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.coding.writes=true" })
class EditSemanticsTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-edit-");

	@Autowired
	private ToolGroup fileGroup;

	@DynamicPropertySource
	static void runRoot(DynamicPropertyRegistry registry) {
		registry.add("smith.tools.coding.root", ROOT::toString);
	}

	@Test
	@DisplayName("a unique match rewrites the file and reports the path")
	void uniqueMatchEditsAndNamesThePath() throws Exception {
		Path file = given("unique.txt", "one span here, and nothing else");
		String answer = edit("unique.txt", "one span here", "the only span there");
		assertThat(answer).startsWith("edited").contains(file.toAbsolutePath().toString()).contains("path=unique.txt");
		assertThat(Files.readString(file)).isEqualTo("the only span there, and nothing else");
	}

	@Test
	@DisplayName("no match writes nothing and says so")
	void noMatchWritesNothing() throws Exception {
		Path file = given("no-match.txt", "content the model misremembered");
		String answer = edit("no-match.txt", "text that is not in the file", "whatever");
		assertThat(answer).startsWith("ERROR:")
			.contains("no match")
			.contains("nothing was written")
			.contains("path=no-match.txt")
			.contains("root=" + ROOT);
		assertThat(Files.readString(file)).isEqualTo("content the model misremembered");
	}

	@Test
	@DisplayName("an ambiguous match refuses and counts the matches")
	void ambiguousMatchRefusesAndCounts() throws Exception {
		Path file = given("ambiguous.txt", "repeat repeat repeat");
		String answer = edit("ambiguous.txt", "repeat", "replaced");
		assertThat(answer).startsWith("ERROR:")
			.contains("ambiguous")
			.contains("occurs 3 times")
			.contains("nothing was written")
			.contains("path=ambiguous.txt");
		assertThat(Files.readString(file)).isEqualTo("repeat repeat repeat");
	}

	@Test
	@DisplayName("an empty span is refused rather than matched everywhere")
	void emptySpanIsRefused() throws Exception {
		given("empty-span.txt", "anything");
		assertThat(edit("empty-span.txt", "", "inserted")).startsWith("ERROR:").contains("empty oldContent");
	}

	private Path given(String name, String contents) throws Exception {
		Path file = ROOT.resolve(name);
		Files.writeString(file, contents);
		return file;
	}

	private String edit(String path, String oldContent, String newContent) {
		return ToolCalls.call(ToolCalls.tool(this.fileGroup, TargetedEditTool.EDIT_FILE), "path", path, "oldContent",
				oldContent, "newContent", newContent);
	}

}
