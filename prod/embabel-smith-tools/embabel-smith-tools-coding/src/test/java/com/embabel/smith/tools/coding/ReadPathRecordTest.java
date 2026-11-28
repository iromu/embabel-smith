/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.core.ToolGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a run can say about what it read.
 *
 * <p>
 * <b>The three things, and why those three.</b> A run report that says only "files were
 * read" answers no question: the reader wants the file, the tool that answered, and the
 * root it was confined to. The file is what upstream already keeps ({@code
 * FileReadLog.getPathsRead()}); the other two are why this exists. The tool matters
 * because one path reaches the same content through {@code readFile}, {@code listFiles}
 * and a search, and a run that read a directory is not a run that read a file. The root
 * matters because confinement is this addon's whole claim, and a record that cannot name
 * the boundary it respected is describing an event it cannot check.
 *
 * <p>
 * <b>Reads, and only reads.</b> The partition in {@link ReadPathLog} is asserted against
 * the group's own offered surface, so the two lists covering the surface is a checked
 * fact rather than an assumption that survives a platform release quietly adding a
 * reader. A write is not recorded here — with the switch on, writes are recorded by their
 * own results, and folding them into the read log would turn "what the run saw" into
 * "what the run did", which is a different question with a different answer.
 *
 * <p>
 * <b>A refusal records nothing, and that is the assertion.</b> A call the root refused
 * read nothing, so a record for it would state a touch that did not happen. The refusal
 * itself still reaches the model naming path and root, which the confinement tests cover;
 * what is checked below is that the log stayed the same length.
 *
 * @author Ivan Rodriguez
 */
class ReadPathRecordTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-readlog-");

	/**
	 * A file only this case reads, so the shared root cannot make one case's record
	 * satisfy another's.
	 * @param prefix the name segment the case owns
	 * @return the file, holding text both a read and a search can find
	 */
	private static Path seed(String prefix) throws IOException {
		Path file = ROOT.resolve(prefix + ".txt");
		Files.writeString(file, "needle in " + prefix + "\n");
		return file;
	}

	private static List<String> triples(ReadPathLog reads) {
		return reads.reads().stream().map((read) -> read.file() + " | " + read.tool() + " | " + read.root()).toList();
	}

	private static String triple(Path file, String tool) {
		return file.toAbsolutePath() + " | " + tool + " | " + ROOT;
	}

	@Nested
	@SpringBootTest(properties = "spring.main.web-application-type=none")
	@DisplayName("through the file group, with nothing configured")
	class ThroughTheFileGroup {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private ReadPathLog reads;

		@DynamicPropertySource
		static void runRoot(DynamicPropertyRegistry registry) {
			registry.add("smith.tools.coding.root", ROOT::toString);
		}

		@Test
		@DisplayName("a read is recorded with its file, its tool and the root")
		void aReadIsRecordedWithThreeThings() throws IOException {
			Path note = seed("note");
			assertThat(ToolCalls.call(ToolCalls.tool(group(), "readFile"), "path", note.getFileName().toString()))
				.contains("needle");
			assertThat(triples(this.reads)).contains(triple(note, "readFile"));
		}

		@Test
		@DisplayName("a listing is recorded as a listing, not as a read of the file it listed")
		void aListingIsItsOwnTool() throws IOException {
			seed("listed");
			assertThat(ToolCalls.call(ToolCalls.tool(group(), "listFiles"), "path", ".")).contains("listed");
			assertThat(triples(this.reads)).contains(triple(ROOT, "listFiles"));
		}

		@Test
		@DisplayName("both searches are recorded against the directory they searched")
		void searchesAreRecordedAgainstTheRoot() throws IOException {
			seed("searched");
			assertThat(ToolCalls.call(ToolCalls.tool(group(), "findFiles"), "glob", "**/searched.txt")).isNotBlank();
			assertThat(ToolCalls.call(ToolCalls.tool(group(), "findPatternInProject"), "pattern", "needle",
					"globPattern", "**/searched.txt"))
				.isNotBlank();
			assertThat(triples(this.reads)).contains(triple(ROOT, "findFiles"), triple(ROOT, "findPatternInProject"));
		}

		@Test
		@DisplayName("a read the root refused adds nothing, because nothing was read")
		void aRefusedReadIsNotRecorded() {
			int before = this.reads.reads().size();
			assertThat(ToolCalls.call(ToolCalls.tool(group(), "readFile"), "path", "../outside.txt"))
				.startsWith("ERROR:");
			assertThat(this.reads.reads()).as("the log stayed the length it was").hasSize(before);
		}

		@Test
		@DisplayName("the offered surface is reads and writes, with nothing outside the two lists")
		void thePartitionCoversWhatIsOffered() {
			List<String> offered = ToolCalls.names(group());
			assertThat(offered).as("the record would be incomplete for anything else")
				.isSubsetOf(ReadPathLog.READ_TOOLS)
				.doesNotContainAnyElementsOf(ReadPathLog.WRITE_TOOLS);
			assertThat(offered).as("and the control: a real surface is being read").contains("readFile");
		}

		@Test
		@DisplayName("the record prints as one line per read")
		void theRecordIsLines() throws IOException {
			Path note = seed("printed");
			ToolCalls.call(ToolCalls.tool(group(), "readFile"), "path", note.getFileName().toString());
			assertThat(this.reads.describe())
				.contains("read " + note.toAbsolutePath() + " with readFile under root " + ROOT);
		}

		private ToolGroup group() {
			return role(CodingToolGroups.FILE_ROLE);
		}

		private ToolGroup role(String role) {
			return this.context.getBeansOfType(ToolGroup.class)
				.values()
				.stream()
				.filter((group) -> role.equals(group.getMetadata().getRole()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no group registered under role '" + role + "'"));
		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.coding.writes=true" })
	@DisplayName("with writes switched on")
	class WithWritesOn {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private ReadPathLog reads;

		@DynamicPropertySource
		static void runRoot(DynamicPropertyRegistry registry) {
			registry.add("smith.tools.coding.root", ROOT::toString);
		}

		@Test
		@DisplayName("a write changes a file and does not enter the read record")
		void aWriteIsNotARead() throws IOException {
			Path target = ROOT.resolve("written.txt");
			seed("readBeforeWriting");
			ToolCalls.call(ToolCalls.tool(group(), "readFile"), "path", "readBeforeWriting.txt");
			assertThat(ToolCalls.call(ToolCalls.tool(group(), "createFile"), "path", "written.txt", "content", "made"))
				.isNotBlank();
			assertThat(target).as("the write really happened, so the absence below is a choice").exists();
			assertThat(triples(this.reads)).as("and the log was reached at all").isNotEmpty();
			assertThat(this.reads.reads()).as("every record names a reader")
				.allSatisfy((read) -> assertThat(ReadPathLog.READ_TOOLS).contains(read.tool()));
		}

		private ToolGroup group() {
			return this.context.getBeansOfType(ToolGroup.class)
				.values()
				.stream()
				.filter((group) -> CodingToolGroups.FILE_ROLE.equals(group.getMetadata().getRole()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no file group"));
		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.expose.reader=file-read" })
	@DisplayName("through a role an operator composed")
	class ThroughAComposedRole {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private ReadPathLog reads;

		@DynamicPropertySource
		static void runRoot(DynamicPropertyRegistry registry) {
			registry.add("smith.tools.coding.root", ROOT::toString);
		}

		@Test
		@DisplayName("a read through the composed role reaches the same record")
		void aComposedReadIsRecorded() throws IOException {
			Path note = seed("composed");
			ToolGroup composed = composedRole("reader");
			assertThat(ToolCalls.call(ToolCalls.tool(composed, "readFile"), "path", note.getFileName().toString()))
				.contains("needle");
			assertThat(triples(this.reads)).contains(triple(note, "readFile"));
		}

		@SuppressWarnings("unchecked")
		private ToolGroup composedRole(String role) {
			// Composed roles arrive as one List<ToolGroup> provider bean rather than as
			// group
			// beans, which is the channel the boot report says the platform appends after
			// the
			// others. Reading them from the file-group beans would find nothing and read
			// as a
			// refusal to record.
			List<ToolGroup> composed = (List<ToolGroup>) this.context.getBean("exposedToolGroups", List.class);
			return composed.stream()
				.filter((group) -> role.equals(group.getMetadata().getRole()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("the expose map registered no '" + role + "' role; roles "
						+ composed.stream().map((group) -> group.getMetadata().getRole()).toList()));
		}

	}

}
