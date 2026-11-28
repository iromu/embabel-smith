/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.tools.file.PatternSearch;
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
 * The two search tools, bounded, and the reason path matching is offered where it is.
 *
 * <p>
 * <b>What a cap has to survive.</b> A search that found more than
 * {@link ConfiningTool#SEARCH_CAP} matches is allowed to return the first
 * {@code SEARCH_CAP} of them, but only if the answer says that is what it did — a
 * shortened list that reads as exhaustive is worse than no answer, because the model goes
 * on reasoning as though it had seen the repository. So both directions are asserted:
 * under the cap every match comes back with its path, and over it the answer names the
 * cap, names how many were found, and lists exactly the cap.
 *
 * <p>
 * <b>Why the bound lives on the wrapper.</b> {@link ConfiningTool} is already the thing
 * every tool this addon publishes passes through, on both routes — the group a role names
 * and the roles the expose map composes. Capping there means one place where a search is
 * bounded rather than one per place a search is built, which is why the composed-role
 * case below is the same assertion at a different entry point rather than a different
 * claim.
 *
 * <p>
 * <b>The glob predicate, and why nothing was authored for it.</b> The last test records
 * the finding the capability question turned on: {@code PatternSearch.matchesGlob}
 * carries no {@code @LlmTool} at all, unlike {@code findPatternInProject}, so it reaches
 * no model — while path matching is already in front of the model as {@code findFiles}'s
 * {@code glob} parameter. A dedicated yes/no predicate tool would have added a round-trip
 * to answer a question about a pattern the model cannot see.
 *
 * @author Ivan Rodriguez
 */
class SearchCapTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-search-cap-");

	/** Three files: comfortably under the cap, so the whole answer must come back. */
	private static final int UNDER_CAP = 3;

	/** Past it, by enough that an off-by-one cannot hide in the count. */
	private static final int OVER_CAP = ConfiningTool.SEARCH_CAP + 3;

	/**
	 * Seed files under a name only the calling case globs for. The prefix matters: one
	 * directory serves every case here, and they run in one context, so a case that
	 * globbed the shared {@code *.txt} would find another case's files and read the cap
	 * in whichever direction the execution order happened to leave them.
	 * @param prefix the name segment this case owns
	 * @param count how many files to write, each holding a line the search can find
	 * @return their absolute paths, in creation order
	 */
	private static List<String> seed(String prefix, int count) throws IOException {
		List<String> paths = new ArrayList<>();
		for (int at = 0; at < count; at++) {
			Path file = ROOT.resolve(prefix + "-" + at + ".txt");
			Files.writeString(file, "needle " + at + "\n");
			paths.add(file.toAbsolutePath().toString());
		}
		return paths;
	}

	/**
	 * The glob that matches only the files a case seeded under its own prefix.
	 * @param prefix the prefix it seeded with
	 * @return the glob to hand the search tool
	 */
	private static String globFor(String prefix) {
		return "**/" + prefix + "-*.txt";
	}

	@Nested
	@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.coding.writes=true" })
	@DisplayName("through the file group")
	class ThroughTheFileGroup {

		@Autowired
		private ToolGroup fileGroup;

		@DynamicPropertySource
		static void runRoot(DynamicPropertyRegistry registry) {
			registry.add("smith.tools.coding.root", ROOT::toString);
		}

		@Test
		@DisplayName("a filename search under the cap returns every match, with its path")
		void findFilesUnderTheCapIsTheWholeAnswer() throws IOException {
			List<String> paths = seed("name-under", UNDER_CAP);
			String answer = ToolCalls.call(ToolCalls.tool(this.fileGroup, "findFiles"), "glob", globFor("name-under"));
			assertThat(paths).allSatisfy((path) -> assertThat(answer).contains(path));
			assertThat(answer).as("nothing was left out, so nothing has to say so").doesNotContain("cap reached");
		}

		@Test
		@DisplayName("a content search under the cap returns every match, with its path")
		void findPatternUnderTheCapIsTheWholeAnswer() throws IOException {
			List<String> paths = seed("text-under", UNDER_CAP);
			String answer = ToolCalls.call(ToolCalls.tool(this.fileGroup, "findPatternInProject"), "pattern", "needle",
					"globPattern", globFor("text-under"));
			assertThat(paths).allSatisfy((path) -> assertThat(answer).contains(path));
			assertThat(answer).doesNotContain("cap reached");
		}

		@Test
		@DisplayName("a filename search past the cap lists the cap and says it was reached")
		void findFilesPastTheCapSaysSo() throws IOException {
			List<String> paths = seed("name-over", OVER_CAP);
			String answer = ToolCalls.call(ToolCalls.tool(this.fileGroup, "findFiles"), "glob", globFor("name-over"));
			assertThat(answer).contains("cap reached")
				.contains(String.valueOf(ConfiningTool.SEARCH_CAP))
				.contains(String.valueOf(OVER_CAP));
			assertThat(paths.stream().filter(answer::contains).count())
				.as("exactly the cap, not a shortened guess at it")
				.isEqualTo(ConfiningTool.SEARCH_CAP);
		}

		@Test
		@DisplayName("and a content search past the cap says the same thing")
		void findPatternPastTheCapSaysSo() throws IOException {
			List<String> paths = seed("text-over", OVER_CAP);
			String answer = ToolCalls.call(ToolCalls.tool(this.fileGroup, "findPatternInProject"), "pattern", "needle",
					"globPattern", globFor("text-over"));
			assertThat(answer).contains("cap reached")
				.contains(String.valueOf(ConfiningTool.SEARCH_CAP))
				.contains(String.valueOf(OVER_CAP));
			assertThat(paths.stream().filter(answer::contains).count()).isEqualTo(ConfiningTool.SEARCH_CAP);
		}

		@Test
		@DisplayName("reading a long file is not a search, and is not clipped by the cap")
		void aLongReadComesBackWhole() throws IOException {
			// The control for the cap's scope: it is applied to two tool names, not to
			// every answer the wrapper passes back. A cap that clipped readFile too would
			// pass the four cases above while quietly breaking the tool the platform
			// describes.
			StringBuilder text = new StringBuilder();
			for (int at = 0; at < OVER_CAP; at++) {
				text.append("line ").append(at).append('\n');
			}
			Files.writeString(ROOT.resolve("long-read.txt"), text.toString());
			String answer = ToolCalls.call(ToolCalls.tool(this.fileGroup, "readFile"), "path", "long-read.txt");
			assertThat(answer).contains("line " + (OVER_CAP - 1));
			assertThat(answer).doesNotContain("cap reached");
		}

		@Test
		@DisplayName("path matching reaches the model through findFiles, because matchesGlob is offered to none")
		void theGlobPredicateCarriesNoLlmTool() throws NoSuchMethodException {
			// The measurement behind not authoring a predicate tool. Both methods sit on
			// the
			// same interface, so the difference between them is the annotation and
			// nothing
			// else: one is offered, the other reaches no model at all — and the offered
			// one
			// already takes a path pattern.
			Method offered = PatternSearch.class.getMethod("findPatternInProject", String.class, String.class);
			Method absent = PatternSearch.class.getMethod("matchesGlob", String.class, String.class);
			assertThat(offered.isAnnotationPresent(LlmTool.class)).as("the content search is offered").isTrue();
			assertThat(absent.isAnnotationPresent(LlmTool.class))
				.as("the glob predicate carries no @LlmTool, so it is not offered")
				.isFalse();
			assertThat(ToolCalls.names(this.fileGroup)).doesNotContain("matchesGlob");
			assertThat(ToolCalls.parameters(this.fileGroup, "findFiles")).contains("glob");
		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.expose.wide=search" })
	@DisplayName("through a role the expose map composed")
	class ThroughAComposedRole {

		@Autowired
		private ApplicationContext context;

		@DynamicPropertySource
		static void runRoot(DynamicPropertyRegistry registry) {
			registry.add("smith.tools.coding.root", ROOT::toString);
		}

		@SuppressWarnings("unchecked")
		private ToolGroup wideRole() {
			// Composed roles arrive as one List<ToolGroup> provider bean rather than as
			// group beans, so they are read from the bean by name — the channel
			// ExposeMapBootStateTest documents.
			List<ToolGroup> composed = (List<ToolGroup>) this.context.getBean("exposedToolGroups", List.class);
			return composed.stream()
				.filter((group) -> "wide".equals(group.getMetadata().getRole()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("the expose map registered no 'wide' role; roles "
						+ composed.stream().map((group) -> group.getMetadata().getRole()).toList()));
		}

		@Test
		@DisplayName("the same wrapper bounds the same search, because it is the same wrapper")
		void theComposedSearchIsBoundedToo() throws IOException {
			// This route goes through the same confine() call, so the cap it gets is the
			// cap the file group's search gets.
			ToolGroup wide = wideRole();
			assertThat(ToolCalls.names(wide)).as("the composed role offers the search").contains("findFiles");
			List<String> paths = seed("composed", OVER_CAP);
			String answer = ToolCalls.call(ToolCalls.tool(wide, "findFiles"), "glob", globFor("composed"));
			assertThat(answer).contains("cap reached").contains(String.valueOf(ConfiningTool.SEARCH_CAP));
			assertThat(paths.stream().filter(answer::contains).count()).isEqualTo(ConfiningTool.SEARCH_CAP);
		}

		@Test
		@DisplayName("and it bounds the content search through that route as well")
		void theComposedContentSearchIsBoundedToo() throws IOException {
			// The cap is applied to two tool names, so the composed route has to be
			// watched on both. A wrapper that bounded the filename search alone would
			// pass the case above and leave the content search unbounded wherever the
			// expose map composed a role — which is the only route that reaches a
			// deployment the file group's own switch was never asked about.
			ToolGroup wide = wideRole();
			assertThat(ToolCalls.names(wide)).as("the composed role offers the content search too")
				.contains("findPatternInProject");
			List<String> paths = seed("wide-text", OVER_CAP);
			String answer = ToolCalls.call(ToolCalls.tool(wide, "findPatternInProject"), "pattern", "needle",
					"globPattern", globFor("wide-text"));
			assertThat(answer).contains("cap reached")
				.contains(String.valueOf(ConfiningTool.SEARCH_CAP))
				.contains(String.valueOf(OVER_CAP));
			assertThat(paths.stream().filter(answer::contains).count())
				.as("exactly the cap, from the other entry point")
				.isEqualTo(ConfiningTool.SEARCH_CAP);
		}

	}

}
